# ==============================================================================
# Crux - Android ProGuard & R8 Optimization Rules
# ==============================================================================

# General / Stack traces preservation
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------------------
# Kotlinx Serialization
# ------------------------------------------------------------------------------
-keepattributes *Annotation*,InnerClasses
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    *;
}
-keep class *$$serializer { *; }

# Preserve Crux data models & dynamic payloads
-keep class org.crux.data.model.** { *; }
-keep class org.crux.data.datastore.model.** { *; }
-keep class org.crux.data.ai.** { *; }
-keep class org.crux.data.privacy.** { *; }
-keep class org.crux.data.readinglist.** { *; }
-keep class org.crux.rss.** { *; }

# ------------------------------------------------------------------------------
# Android WebView & JavaScript Interfaces
# ------------------------------------------------------------------------------
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class org.crux.ui.screen.linkedin.** { *; }
-keep class org.crux.ui.screen.instagram.** { *; }

# ------------------------------------------------------------------------------
# Ktor & OkHttp
# ------------------------------------------------------------------------------
-dontwarn io.ktor.client.engine.**
-dontwarn okhttp3.internal.graal.**
-dontwarn okhttp3.internal.platform.**

-keepnames class io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider {}
-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider {
    <init>();
}
-keepnames class io.ktor.client.HttpClientEngineContainer {}
-keep class * implements io.ktor.client.HttpClientEngineContainer {
    <init>();
}

# ------------------------------------------------------------------------------
# Coil 3 Image Loading
# ------------------------------------------------------------------------------
-keep class coil3.util.DecoderServiceLoaderTarget { *; }
-keep class coil3.util.FetcherServiceLoaderTarget { *; }
-keep class coil3.util.ServiceLoaderComponentRegistry { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }

# ------------------------------------------------------------------------------
# On-Device AI, LiteRT & MediaPipe Runtime
# ------------------------------------------------------------------------------
-keep class com.google.ai.edge.litert.** { *; }
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclasseswithmembers class com.google.ai.edge.litertlm.** { native <methods>; }
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.ai.edge.litert.**
-dontwarn com.google.ai.edge.litertlm.**
-dontwarn com.google.mediapipe.**

# ------------------------------------------------------------------------------
# Koin Dependency Injection
# ------------------------------------------------------------------------------
-keep class * extends org.koin.core.module.Module
-keep class org.koin.core.module.Module
-keep @org.koin.core.annotation.Single class * { *; }
-keep @org.koin.core.annotation.Factory class * { *; }

# ------------------------------------------------------------------------------
# Third-party utilities
# ------------------------------------------------------------------------------
-keep class okio.** { *; }

# Strip verbose/debug/info Logcat statements in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Suppress harmless build-time warnings
-dontwarn com.google.auto.value.**
-dontwarn io.opentelemetry.**
-dontwarn java.beans.**
-dontwarn org.osgi.annotation.**
-dontnote **
