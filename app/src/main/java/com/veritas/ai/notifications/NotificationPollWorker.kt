package com.veritas.ai.notifications

import android.content.Context
import android.util.Log
import androidx.work.*
import com.veritas.ai.auth.AuthManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that polls the Cloudflare backend for new notifications.
 * Replaces Firebase Cloud Messaging entirely — no Google dependency.
 *
 * Architecture:
 * - Runs every 15 minutes (minimum WorkManager interval for periodic work)
 * - Sends device_id header so backend tracks active devices
 * - Uses ?since=<last_seq> for efficient delta polling (integer AUTOINCREMENT)
 * - Shows system notifications for each new item
 */
class NotificationPollWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "VeritasNotifPoll"
        private const val BASE_URL = "https://veritas-ai.pages.dev"
        private const val PREFS_DEVICE = "veritas_device"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_NOTIF_SEQ = "last_notif_seq"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val WORK_NAME = "veritas_notification_poll"

        /**
         * Get or generate a persistent device UUID.
         */
        fun getDeviceId(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
            var deviceId = prefs.getString(KEY_DEVICE_ID, null)
            if (deviceId == null) {
                deviceId = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
                Log.d(TAG, "Generated new device_id: $deviceId")
            }
            return deviceId
        }

        fun isNotificationsEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        }

        fun setNotificationsEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
            if (enabled) {
                schedule(context)
            } else {
                cancel(context)
            }
        }

        fun getLastNotifSeq(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
            return prefs.getLong(KEY_LAST_NOTIF_SEQ, 0L)
        }

        fun setLastNotifSeq(context: Context, seq: Long) {
            val prefs = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_NOTIF_SEQ, seq).apply()
        }

        /**
         * Schedule periodic polling every 15 minutes.
         * Constraints: only when connected to network and device not low battery.
         */
        fun schedule(context: Context) {
            if (!isNotificationsEnabled(context)) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()

            val request = PeriodicWorkRequestBuilder<NotificationPollWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Notification polling scheduled (15 min interval)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Notification polling cancelled")
        }

        /**
         * Register this device with the backend. Call on login.
         */
        suspend fun registerDevice(context: Context): Boolean {
            val auth = AuthManager.getInstance(context)
            val token = auth.getToken() ?: return false
            val deviceId = getDeviceId(context)

            return try {
                val json = JSONObject().apply {
                    put("device_id", deviceId)
                    put("device_name", "Android ${android.os.Build.MODEL}")
                }
                val conn = (URL("$BASE_URL/api/notifications/register").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $token")
                }
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) {
                    Log.d(TAG, "Device registered: $deviceId")
                    true
                } else {
                    Log.w(TAG, "Device registration failed: $code")
                    false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Device registration error (non-fatal)", e)
                false
            }
        }

        /**
         * Unregister device from backend. Call on logout.
         */
        suspend fun unregisterDevice(context: Context) {
            val auth = AuthManager.getInstance(context)
            val token = auth.getToken() ?: return
            val deviceId = getDeviceId(context)

            try {
                val json = JSONObject().apply {
                    put("device_id", deviceId)
                }
                val conn = (URL("$BASE_URL/api/notifications/unregister").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $token")
                }
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                conn.disconnect()
                Log.d(TAG, "Device unregistered: $deviceId")
            } catch (e: Exception) {
                Log.w(TAG, "Device unregistration error (non-fatal)", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        if (!isNotificationsEnabled(applicationContext)) return Result.success()

        val auth = AuthManager.getInstance(applicationContext)
        val token = auth.getToken() ?: return Result.success()

        return try {
            val deviceId = getDeviceId(applicationContext)
            val sinceSeq = getLastNotifSeq(applicationContext)
            val url = "$BASE_URL/api/notifications/poll?since=$sinceSeq&limit=20"

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("x-veritas-device-id", deviceId)
            }

            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return Result.retry()
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val notifications = json.optJSONArray("notifications")

            // Use last_seq from response if available, otherwise keep current cursor
            val lastSeqFromServer = json.optLong("last_seq", sinceSeq)
            if (lastSeqFromServer > sinceSeq) {
                setLastNotifSeq(applicationContext, lastSeqFromServer)
            }

            if (notifications != null && notifications.length() > 0) {
                NotificationHelper.createNotificationChannels(applicationContext)

                for (i in 0 until notifications.length()) {
                    val notif = notifications.getJSONObject(i)
                    val title = notif.optString("title", "Veritas AI")
                    val bodyText = notif.optString("body", "")
                    val deepLink = notif.optString("deep_link", "").ifEmpty { null }

                    if (deepLink.isNullOrEmpty()) {
                        NotificationHelper.showNotification(applicationContext, title, bodyText)
                    } else {
                        NotificationHelper.showNotification(applicationContext, title, bodyText, deepLink)
                    }
                }

                Log.d(TAG, "Delivered ${notifications.length()} notifications")
            }

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Notification poll failed (will retry)", e)
            Result.retry()
        }
    }
}