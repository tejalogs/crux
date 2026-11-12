# Crux status — 12 Nov 2026

**Repository:** `https://github.com/tejalogs/crux` (`main` branch, AGPL-3.0)
**Website:** `https://tejalogs.github.io/crux/`
**Latest Release:** `v1.0.1` (`versionCode=2`, signed `app-release.apk` 18.2 MB attached)

## Distribution & In-App Updates
- **GitHub Releases (Primary)**: Signed release APKs are hosted directly on GitHub Releases (`v1.0.0`, `v1.0.1`). F-Droid inclusion MR #56349 was closed by maintainers because the main F-Droid catalog requires 3+ months of public repository history; GitHub Releases + GitHub Pages remain the official distribution channels.
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
- **RSS:** lenient feed parsing, US time-zone dates, `<br>`/numbered lists/relative images in articles, defaults seeded once, no duplicate feeds, sturdier OPML import, reader remembers position as a fraction, bookmark waits for the article.

## Remaining Backlog
- Some internal `R.string.*` / `Res.string.*` resource identifier names (`pixiv_*`, `fanbox_*`, `mastodon_*`) remain in `EditTabDialog`, `TabIcon`, and `CommonStatusComponent` (English & localized user-facing copy has already been cleaned of Mastodon/Flare/Prism references).
- Instagram/LinkedIn "shadow account" code still exists (used for sign-in detection only).
- Dependency updates (SKIE/Kotlin versions are intentionally coupled at Kotlin `2.4.20` + SKIE `0.10.15` until both are verified together).
