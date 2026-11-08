# Contributing to Crux

Thanks for helping. Crux is an Android app built on [Flare](https://github.com/DimensionDev/Flare) by DimensionDev and licensed under AGPL-3.0, so contributions are accepted under the same license.

## Issues

Search open and closed issues first. If nothing matches, file a new one at https://github.com/tejalogs/crux/issues with your Android version, Crux version (Settings > About) and steps to reproduce. Please do not paste cookies, tokens or screenshots of private accounts.

## Development environment

- JDK 17 or newer
- Android SDK

## Code style

Crux uses [ktlint](https://github.com/pinterest/ktlint). Run `./gradlew ktlintFormat` before opening a pull request.

## Building and testing

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :shared:jvmTest :feature:subscription:jvmTest :feature:agent:jvmTest
```

## Project structure

- `app`: the Android application (Jetpack Compose UI, WebView tabs, manifest).
- `shared`: business logic, data sources, settings and presenters.
- `compose-ui`: reusable Compose components.
- `feature/subscription`: RSS sources and reader logic.
- `feature/agent`: optional on-device or OpenAI-compatible assistant (off by default).
- `feature/login`, `feature/login-api`, `feature/tab`: sign-in detection and tab models.
- `build-logic`: Gradle convention plugins.

## Free-software rules

Crux is built for F-Droid. Do not add proprietary SDKs (Firebase, Google Play services, ML Kit, analytics, ad or crash reporting libraries), snapshot dependencies or binary blobs. `.github/scripts/check_fdroid_nonfoss_pr.sh` checks Gradle files against the F-Droid scanner signatures.
