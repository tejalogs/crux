# Crux - One Calm Home for Your Social Feeds

[![license](https://img.shields.io/badge/License-AGPL%203.0-blue.svg)](LICENSE)
![badge-Android](https://img.shields.io/badge/Android-8.0+-3DDC84?logo=android&logoColor=white)
![badge-macOS](https://img.shields.io/badge/macOS-Sonoma+-black?logo=apple&logoColor=white)
![badge-Windows](https://img.shields.io/badge/Windows-10%2F11-blue?logo=windows&logoColor=white)
![badge-Linux](https://img.shields.io/badge/Linux-AppImage%2FDeb-orange?logo=linux&logoColor=white)
![badge-Privacy](https://img.shields.io/badge/Zero--Egress%20AI-Gemma%204%20Edge-8A2BE2)

Crux (which began as a fork of [Flare](https://github.com/DimensionDev/Flare)) is an open-source, sovereign, privacy-first multiplatform social hub built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. It unifies your fragmented social feeds into an integrated, beautiful timeline with **zero telemetry**, **zero cloud AI data leakage**, and hardware-backed local intelligence.

---

## 🌟 Supported Platforms & Connectors

| Platform | Integration Mode | Features |
| :--- | :--- | :--- |
| **LinkedIn** | Sovereign Web Bridge & Voyager Dash API | Native feed cards, article previews, document carousels, polls, commentary reshares, offline disk persistence |
| **Instagram** | Sovereign Isolated WebView Bridge | Clean timeline cards, sandboxed session storage, tracker blocking |
| **Bluesky** | AT Protocol Native Client | Full feed, mentions, rich media, custom feeds, lists, search |
| **Mastodon** | ActivityPub REST Client | Federated timelines, lists, bookmarks, polls, instance custom emojis |
| **Twitter / X** | Direct API & Syndication | Native timeline mapping, video badges, quotes, search |
| **Nostr** | Nostr SDK & Relay Engine | Cryptographic keys, relay pool multiplexing, decentralized posts |
| **Misskey** | Sharkey / Misskey API | Antennas, custom emojis, reactions, timeline streams |
| **Pixiv & Fanbox** | Native API & Session Engine | High-res art galleries, creator updates |

---

## 🧠 Sovereign On-Device AI (Zero Cloud Egress)

- **Gemma 4 Edge (E2B / E4B) Architecture**: Embedded support for Google's lightweight open weights Gemma 4 Edge (~2.3B parameters, 1.4GB RAM footprint) with speculative **Multi-Token Prediction (MTP)** for up to 2.2x inference speedup and an 8192-token context window.
- **LiteRT-LM & Localhost Bridge**: Direct integration with `.litertlm` runtime containers and zero-trust loopback socket verification (`localhost:11434` for desktop Ollama).
- **Dense 768-Dimensional Semantic Vector Search**: Local mathematical embedding engine utilizing subword n-gram hashing, sinusoidal positional harmonics, TF-IDF term weighting, and unit-norm projection to perform instant offline semantic ranking across cached timelines and reading lists.
- **"Catch Me Up" Feed Digests**: On-device clustering synthesizes missed posts into an expressive summary card with topic chips and author highlight previews right at the top of your feed.

---

## 🛡️ Zero-Trust Security & Sovereign Privacy Hub

- **0 B Cloud AI Leakage**: AI processing, summarization, translation, and semantic search operate strictly on-device without sending tokens or context to cloud servers.
- **Network Egress Inspector**: Live settings dashboard displaying network activity, blocked tracking beacons (Snapchat, DoubleClick, GA, telemetry), and active sovereign bridge statuses.
- **Sandboxed Web Bridges**: LinkedIn and Instagram integrations execute within dedicated sandboxes that automatically strip ads, third-party analytics scripts, and intrusive app install nag-modals.
- **Encrypted Local Storage**: Account credentials, access tokens, and cached feed payloads are stored locally and encrypted.

---

## 🎨 Expressive Material 3 Design & Micro-Interactions

- **Floating Capsule Dock**: Ergonomic floating bottom bar with smooth spring physics, auto-collapsing on feed scroll and expanding on reverse scroll.
- **Stacked Grouped Avatars**: Notification cards feature overlapping stacked avatar circles with `+N` count indicators and signature platform origin badges (Twitter, Bluesky, Mastodon, Instagram, LinkedIn).
- **Tactile Haptic Feedback**: Subtle haptic pulses and spring-damping animations on likes, boosts, bookmarks, and bottom tab switches.
- **Media Viewer**: Double-tap gesture zoom (`1.0x` to `2.5x`) with haptic detent thresholds.
- **Refreshed Explore Screen**: Dynamic category filter pills (`"All"`, `"Trending"`, `"Tech & AI"`, `"News & Media"`, `"People"`) with gradient topic cards and search suggestion chips.

---

## 🛠️ Building & Packaging

### Prerequisites
- JDK 17 or higher
- Android SDK (API 35, Build Tools 35.0.0)

### 1. Build Android Debug APK
```bash
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### 2. Build Android Production Release APK (Optimized with ProGuard/R8)
```bash
./gradlew :app:assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```
*Note: Release builds automatically execute R8 minification, resource shrinking, dead code elimination, and alien binary format stripping (Windows DLLs and Mac JNI libraries are excluded).*

### 3. Run Test Suite
```bash
# Multiplatform shared tests (680+ unit tests)
./gradlew :shared:jvmTest

# Compose UI multiplatform unit tests
./gradlew :compose-ui:jvmTest

# Android unit tests
./gradlew :app:testDebugUnitTest
```

### 4. Build Desktop Client (JVM)
```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
```

---

## 📄 License
This project is licensed under the [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE).
