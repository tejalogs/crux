# Release & Packaging

## Build

```bash
./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

The release build runs R8 minification and resource shrinking (`app/proguard-rules.pro`). `dependenciesInfo` is disabled so the APK carries no dependency metadata block, and no timestamps or git hashes are injected into the build.

## Versioning

`fdroid.properties` holds `versionName` and `versionCode`. `BUILD_VERSION` and `BUILD_NUMBER` environment variables override them. The About screen shows `versionName (versionCode)`.

## Signing

Create `signing.properties` in the project root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) to sign the release APK with your key. Without it the release build falls back to the debug keystore. F-Droid signs its own build.

## Cutting a release

1. Bump `fdroid.properties` and add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
2. Run the tests: `./gradlew :app:testDebugUnitTest :shared:jvmTest :feature:subscription:jvmTest :feature:agent:jvmTest`.
3. Tag `v<versionName>` and push the tag.
4. Attach the signed APK to the GitHub release.

## F-Droid

`docs/fdroid/org.crux.social.yml` is a draft of the fdroiddata metadata file. Store listing text lives in `fastlane/metadata/android/en-US/`. Phone screenshots (`images/phoneScreenshots/`) still need to be captured on a device.
