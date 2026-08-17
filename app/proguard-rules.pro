# Veritas Android - ProGuard / R8 rules

# Keep annotations for reflection
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep app classes
-keep class com.veritas.ai.** { *; }
-keepclassmembers class com.veritas.ai.** { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# Kotlin metadata
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# WebView JS bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Org.json
-keep class org.json.** { *; }

# AndroidX
-dontwarn androidx.**
-keep class androidx.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# WorkManager
-dontwarn androidx.work.**
-keep class androidx.work.** { *; }

# CameraX
-dontwarn androidx.camera.**