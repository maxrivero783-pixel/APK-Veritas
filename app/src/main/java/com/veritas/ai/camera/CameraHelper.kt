package com.veritas.ai.camera

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import java.io.File

/**
 * Camera capture helper using the system camera intent.
 *
 * Usage with Activity Result API:
 * ```kotlin
 * val cameraLauncher = registerForActivityResult(CameraHelper.Capture()) { uri ->
 *     uri?.let { handleCapturedPhoto(it) }
 * }
 * cameraLauncher.launch(Unit)
 * ```
 */
object CameraHelper {

    private const val TAG = "VeritasCamera"
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * ActivityResultContract that launches the system camera and returns the photo URI.
     */
    class Capture : ActivityResultContract<Unit, Uri?>() {
        private var photoUri: Uri? = null

        override fun createIntent(context: Context, input: Unit): Intent {
            val photoFile = createImageFile(context)
            photoUri = getFileUri(context, photoFile)

            return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                if (photoUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (resultCode == android.app.Activity.RESULT_OK) photoUri else null
        }
    }

    /**
     * ActivityResultContract that opens a gallery picker for image selection.
     */
    class PickImage : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit): Intent {
            return Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
        }
    }

    // --- Internal ---

    private fun createImageFile(context: Context): File {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.filesDir.resolve("pictures").also { it.mkdirs() }
        val timestamp = System.currentTimeMillis()
        return File(storageDir, "veritas_capture_$timestamp.jpg")
    }

    private fun getFileUri(context: Context, file: File): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Use FileProvider via reflection to avoid compile-time dependency issues
            try {
                val providerClass = Class.forName("androidx.core.content.FileProvider")
                val getUriForFile = providerClass.getMethod(
                    "getUriForFile", Context::class.java, String::class.java, File::class.java
                )
                getUriForFile.invoke(null, context, "${context.packageName}$AUTHORITY_SUFFIX", file) as Uri
            } catch (e: Exception) {
                Log.w(TAG, "FileProvider not available, using MediaStore fallback", e)
                // Fallback: insert into MediaStore
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Veritas")
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: Uri.fromFile(file)
            }
        } else {
            @Suppress("DEPRECATION")
            Uri.fromFile(file)
        }
    }

    /**
     * Convert a URI to a base64 data URL for injection into the WebView.
     */
    fun uriToDataUrl(context: Context, uri: Uri, callback: (String?) -> Unit) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                callback("data:$mimeType;base64,$base64")
            } ?: callback(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert URI to data URL", e)
            callback(null)
        }
    }
}