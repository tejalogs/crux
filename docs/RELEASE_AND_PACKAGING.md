# Release & Packaging Guide

This document outlines the release build pipeline, ProGuard/R8 optimization rules, signing configurations, and binary stripping procedures for **Prism (Flare)**.

---

## 1. Release Architecture & Optimization

Android release builds utilize Android Gradle Plugin (AGP) with Google's R8 shrinker enabled:
- **Code Minification (`isMinifyEnabled = true`)**: Eliminates unused classes, methods, and fields across all transitive dependencies.
- **Resource Shrinking (`isShrinkResources = true`)**: Strips unreferenced drawables, strings, and layouts from the final APK.
- **ProGuard Optimization Rules (`app/proguard-rules.pro`)**: Preserves dynamic entrypoints required for Kotlin Multiplatform, including:
  - `kotlinx.serialization` serializers and companion objects.
  - `Ktorfit` / `Ktor` network adapters and serializer providers.
  - `Coil 3` service loader decoders and fetchers.
  - `Room` and bundled SQLite native drivers (`androidx.sqlite.driver.bundled.**`).
  - `@android.webkit.JavascriptInterface` methods in web bridges (such as LinkedIn and Instagram).
  - On-Device Gemma 4 AI, `LiteRT-LM`, and `JNA` interop classes.

---

## 2. Resource Packaging & Binary Sanitization

Transitive dependencies (such as logging and terminal utilities) often package alien platform binaries inside standard JARs. Our build configuration enforces strict packaging exclusions in `app/build.gradle.kts`:
- Excluded extraneous artifacts:
  - `org/fusesource/jansi/**` (removes Windows DLLs and macOS jnilibs)
  - `DebugProbesKt.bin` (removes coroutine debugging artifacts)
  - `/META-INF/{AL2.0,LGPL2.1}` (cleans duplicate licensing manifests)
  - `META-INF/INDEX.LIST` and Netty version properties

---

## 3. Building Release Artifacts

### 3.1 Universal Release APK
To assemble the release APK:
```bash
./gradlew :app:assembleRelease
```
The resulting APK is written to:
```
app/build/outputs/apk/release/app-release.apk
```

### 3.2 Android App Bundle (AAB for Google Play / App Stores)
To build an Android App Bundle:
```bash
./gradlew :app:bundleRelease
```
The resulting AAB is located at:
```
app/build/outputs/bundle/release/app-release.aab
```

---

## 4. Release Signing Configuration

For official release builds, create `signing.properties` in the project root:
```properties
storeFile=path/to/release.keystore
storePassword=your_keystore_password
keyAlias=your_key_alias
keyPassword=your_key_password
```
When `signing.properties` is present, the release task signs the APK with your release key. When omitted, Gradle falls back to the local debug keystore for development testing.

---

## 5. Versioning & F-Droid Compatibility

Version parameters are managed in `fdroid.properties`:
```properties
versionCode=1
versionName=1.0.0
```
CI/CD environments can override these dynamically using environment variables:
```bash
export BUILD_NUMBER=42
export BUILD_VERSION="1.0.0-rc1"
./gradlew :app:assembleRelease
```

---

## 6. Pre-Release Verification Checklist

Before publishing a release, execute the complete multiplatform verification suite:

1. **Shared Multiplatform Test Suite**:
   ```bash
   ./gradlew :shared:jvmTest
   ```
   *(Verifies all models, serialization, parsers, and data sources)*

2. **Compose UI Component Tests**:
   ```bash
   ./gradlew :compose-ui:jvmTest
   ```
   *(Verifies UI logic, theme tokens, and card components)*

3. **Android Unit & Robolectric Tests**:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

4. **Verify R8 Mapping & Seeds**:
   Ensure `app/build/outputs/mapping/release/mapping.txt` and `configuration.txt` correctly preserve all `@android.webkit.JavascriptInterface` methods and data models.
