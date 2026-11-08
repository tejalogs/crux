# Crux

[![license](https://img.shields.io/badge/License-AGPL%203.0-blue.svg)](LICENSE)
![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?logo=android&logoColor=white)

Crux is an Android app that puts **Instagram, X, LinkedIn and Reddit** in one place, each as a private web tab, and adds an **RSS reader** with a paged, distraction-free article view. It is free software (AGPL-3.0), has no Crux account, no server, no ads, no analytics and no crash reporting.

Crux is built on [Flare](https://github.com/DimensionDev/Flare) by DimensionDev.

## What it does

- **Private web tabs.** Instagram, X, LinkedIn and Reddit each load in their own isolated WebView profile, so cookies and storage of one site never reach another. You sign in on the site's own page.
- **Privacy & Tweaks.** Per site: Standard (hide-only) or Strict (also blocks trackers) protection, focus toggles (Reels/Explore, "Who to follow" and trends, promoted posts, app-install nags), block autoplay, clear one site's data, screenshot protection, Global Privacy Control and tracking-parameter removal on links.
- **RSS and a paged reader.** Add feeds, import OPML, bookmark articles, and read with a reader that remembers where you stopped. Links in articles open in your default browser with tracking removed.
- **Smart replies and carousels.** Draft replies and carousel decks with an on-device engine.
- **Optional cloud features.** Translation and AI summaries only run when you ask for them. See [Privacy](#privacy).

## Privacy

The full policy is in [docs/PRIVACY.md](docs/PRIVACY.md). In short:

- Crux does not store your passwords. Sessions live in the WebView profile of each site, on your device only. The Android backup and device-transfer features are switched off, so WebView data, the app database and settings are never copied off the device by Android.
- Crux shows proprietary websites inside WebViews. The page tweaks are display-only: they change what you see, they do not stop a site from knowing what you do while you are signed in to it.
- The app makes network requests to the sites you use, to the RSS feeds you add, and to translation or AI providers only if you use those features.
- **Translation.** Runs when you tap Translate (or turn on auto-translate, which is off by default) and sends the text to the provider chosen in settings: Google Translate (default), DeepL, Google Cloud Translation, LibreTranslate or an OpenAI-compatible server.
- **AI.** Smart replies and summaries use the on-device engine. A cloud AI server (OpenAI-compatible) is used only after you enter its address and key in settings. The optional agent is off by default.

## Install

- **GitHub releases:** download the APK from [Releases](https://github.com/tejalogs/crux/releases).
- **F-Droid:** submission in progress. Crux will carry the *NonFreeNet* anti-feature because it displays non-free network services.

Requires Android 8.0 or newer.

## Build

Requires JDK 17 or newer and the Android SDK.

```bash
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest :shared:jvmTest :feature:subscription:jvmTest :feature:agent:jvmTest
```

Version name and code come from `fdroid.properties` (overridable with `BUILD_VERSION` and `BUILD_NUMBER`). See [docs/RELEASE_AND_PACKAGING.md](docs/RELEASE_AND_PACKAGING.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[GNU Affero General Public License v3.0](LICENSE). Crux contains code from Flare by DimensionDev, also AGPL-3.0.
