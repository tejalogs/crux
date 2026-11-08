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
-keep class org.crux.data.datasource.linkedin.** { *; }
-keep class org.crux.data.datasource.instagram.** { *; }
-keep class org.crux.data.ai.** { *; }
-keep class org.crux.data.privacy.** { *; }

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
# Ktor & Ktorfit
# ------------------------------------------------------------------------------
-dontwarn io.ktor.client.engine.**
-dontwarn okhttp3.internal.graal.**
-dontwarn okhttp3.internal.platform.**

-keep class de.jensklingenberg.ktorfit.** { *; }
-keepclassmembers class de.jensklingenberg.ktorfit.** { *; }
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
# Room, SQLite & Bundled Native Drivers
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.room3.RoomDatabase { <init>(); }
-keepclasseswithmembers class androidx.sqlite.driver.bundled.** { native <methods>; }
-keepclasseswithmembers class androidx.sqlite.driver.** { native <methods>; }

# ------------------------------------------------------------------------------
# Native Interop (JNA, UniFFI, Rust bridges)
# ------------------------------------------------------------------------------
-keep class com.sun.jna.** { *; }
-keep class net.java.dev.jna.** { *; }
-keep class uniffi.** { *; }
-dontwarn com.sun.jna.**
-dontwarn net.java.dev.jna.**

# ------------------------------------------------------------------------------
# On-Device AI, LiteRT & MediaPipe Runtime
# ------------------------------------------------------------------------------
-keep class com.google.ai.edge.litert.** { *; }
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.ai.edge.litert.**
-dontwarn com.google.mediapipe.**

# ------------------------------------------------------------------------------
# Koin Dependency Injection
# ------------------------------------------------------------------------------
-keep class * extends org.koin.core.module.Module
-keep class org.koin.core.module.Module
-keep @org.koin.core.annotation.Single class * { *; }
-keep @org.koin.core.annotation.Factory class * { *; }

# ------------------------------------------------------------------------------
# Third-party utilities & localization
# ------------------------------------------------------------------------------
-keep class org.ocpsoft.prettytime.i18n** { *; }
-keep public class * implements org.ocpsoft.prettytime.TimeUnit
-keep class okio.** { *; }
-keep class io.github.kdroidfilter.** { *; }

# Suppress harmless build-time warnings
-dontwarn com.google.auto.value.**
-dontwarn io.opentelemetry.**
-dontwarn java.beans.**
-dontwarn org.osgi.annotation.**
-dontnote **
