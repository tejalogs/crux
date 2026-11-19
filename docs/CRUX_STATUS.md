# Crux status — 19 Nov 2026

**Repository:** `https://github.com/tejalogs/crux` (`main` branch, AGPL-3.0)
**Website:** `https://tejalogs.github.io/crux/`
**Latest Release:** `v1.0.8` (`versionCode=9`, permanently signed `app-release.apk` attached)

## Distribution & In-App Updates
- **GitHub Releases (Primary)**: Signed release APKs are hosted directly on GitHub Releases (`v1.0.0`–`v1.0.8`, permanently signed with Crux release key from `v1.0.8` onward). F-Droid inclusion MR #56349 was closed by maintainers because the main F-Droid catalog requires 3+ months of public repository history; GitHub Releases + GitHub Pages remain the official distribution channels.
- **In-App OTA Updater**: `AppUpdateManager` checks `/repos/tejalogs/crux/releases/latest` from *Settings → About → Check for updates*, streams APK downloads with progress reporting, and installs via `FileProvider` (`REQUEST_INSTALL_PACKAGES`).

## What Crux is now
Android only. Platforms: **Instagram, X, LinkedIn, Reddit** (live web tabs, each in its own browser profile) + **RSS** with the paged reader.

### Removed
- Native platforms: Mastodon, Bluesky, Misskey, Nostr, Pixiv, Fanbox, Weibo (modules + code).
- Desktop, iOS and web targets.
- Dead screens: Discover, Notifications, Search, DMs, Lists, classic composer, draft box, "Add account" login screens, local follows UI (DB tables kept for upgrades).
- Unused `androidx.browser` dependency (Custom Tabs).

### Added / fixed
- **Onboarding v1.0.1 + polish:** `ic_crux_mark` welcome header with staggered pillar animations, updated Calm Reading & On-Device AI pillar, live dock preview with TalkBack accessibility descriptions and empty-state hint, exposed RSS starter feed chips, explicit *"Sign in later"* + *"Continue to appearance"* buttons, haptic confirmation on sign-in return, mock media preview in Live Calm Preview, and an integrated Theme Mode picker.
- **Home & Tabs:** A pill for every platform you enable, "All RSS" pill, empty state, no onboarding flash, no 1.5 s polling loop. Removing a platform pill in Tab Settings now persists across toggles of other platforms (`platformsToAdd` scoped sync).
- **Privacy & Tweaks** (Settings, and side panel): per site Standard/Strict protection (Standard = hide-only, default for IG/X/LinkedIn; Strict = also blocks trackers, default for Reddit), focus toggles (Reels/Explore, Who to follow/trends, promoted, app nags), block autoplay, clear one site's data, screenshot protection, Global Privacy Control, link cleaning, and an optional In-App Hardened Browser screen under *Settings → Browser and links*.
- **Links:** every link opens in your default browser (tracking removed first), except Instagram/X/LinkedIn/Reddit links, which open in their Crux tab.
- **Share / quick tile / shortcut** open the Crux compose sheet (text + link). Web composer URLs cannot accept local `content://` image/video files directly, so media attachments use each WebView composer's file picker (`onShowFileChooser`).
- **RSS:** lenient feed parsing, US time-zone dates, `<br>`/numbered lists/relative images in articles, defaults seeded once, no duplicate feeds, sturdier OPML import, reader remembers position as a fraction, bookmark waits for the article, and RSS articles can be saved to custom Universal Bookmark Folders.
- **v1.0.5 WebView, Bookmarks & On-Device AI:** Universal Bookmark Folders across all social tabs and RSS (custom icons, colors, notes, inline folder editing); removed local browsing history; onboarding auto-return after login + starter RSS seeding on quick skip; RSS-first default bottom dock order; external links default to system browser; Instagram blank-scroll fix; LinkedIn dark-mode image polarity fix, bottom dock clearance, and `/redir/` navigation; Reddit scroll unlock, inline/fullscreen video playback, and NSFW unblurring; LiteRT-LM on-device AI model management & local prompt execution.
- **Cleanup & lint hardening (v1.0.6):** Renamed remaining legacy `pixiv_*`, `fanbox_*`, and `mastodon_*` XML resource keys across 62 localized `strings.xml` files (`ranking_*`, `creator_supported_title`, `recommended_creators_title`, `private_*`, `post_visibility_*`, `post_item_show_*`); stopped creating Instagram/LinkedIn shadow DB accounts at login and clean up legacy shadow rows once migrated; resolved all `:app:lintDebug` errors (`MissingPermission`, `RestrictedApi`, Compose lint checks); polished update prompt changelog scrolling, equal-height buttons, and debug signing mismatch guidance.
- **4-platform E2E & WebView UX fixes (v1.0.7):** Fixed Instagram Reels horizontal drift into blank space when swiping up after expanding a Reel description (`data-cx-reels`, `touch-action: pan-y`, `initJs` scroll clamp, and `/<user>/reel/` immersive detection); fixed Reddit scroll lag, search reverse-typing on Android IME/Lit inputs, and missing NSFW post descriptions; fixed Google/Apple OAuth back navigation across LinkedIn, X, and Reddit; preserved Activity `uiMode` night mask across Chromium WebView init to eliminate mixed light/dark chrome; added IME-aware scrolling to bookmark note sheets; brought standalone Instagram/LinkedIn/LiveWeb screens to full parity (fullscreen video, `.imePadding()`, `onCreateWindow`, app-bounce protection, X Spaces fleetline hiding, and LinkedIn Smart Reply extraction).
- **Permanent signing, WebView edge-case hardening, UI polish & legacy cleanup (v1.0.8):** Enabled permanent Crux release signing key in GitHub Actions with `apksigner` debug-key rejection guard; hardened WebViews across all 4 platforms (orientation/fullscreen `configChanges`, auto-hide bottom dock in fullscreen video, `onRenderProcessGone` crash recovery, MIME-aware file picker, safe download listener, in-place HTTP→HTTPS upgrade, media pause-before-`onPause`, Instagram search `/explore/tags/` preservation, X/Reddit user Play Store link preservation, Reddit spoiler & `/login` recovery, DM/Messaging/Chat pull-to-refresh guard, offline retry screens); polished bottom dock separation, Bookmark Save sheet & Reading List cards/filters, standalone screen host subtitles, drawer Sign-In badge, and Hardened Browser URL bar; removed unused `:feature:login` / `:feature:login-api` modules, dead `appleMain`/`iosMain`/`wasmJsMain` KMP targets & source sets, and unused Settings/WebView helpers.

## Remaining Backlog
- Dependency updates applied cleanly (`Gradle 9.8.2`, `AGP 9.5.1`, `Kotlin 2.4.21`, `SKIE 0.10.16`, `KSP 2.3.13`, `Ktor 3.6.1`, `Material3 1.5.0-rc01`, `DataStore 1.3.0`). Kotlin and SKIE remain coupled when upgrading further.
