# Crux status — 6 Nov 2026

**Branch:** `crux/live-web-and-reader` · **Nothing committed or pushed tonight** (≈2,770 changed files, including your `org.crux` rename).
**Debug APK:** `app/build/outputs/apk/debug/app-debug.apk` (app ID `org.crux.social` — installs fresh, alongside the old `org.prism.social`).

## Verified tonight (on the Mac, no device)
- `./gradlew :app:assembleDebug` passes.
- 1,134 unit tests, 0 failures: app 100, shared 773, RSS 79, agent 59, login 2, login-api 17, compose-ui 104.
- ktlint clean on app, shared, compose-ui, feature:subscription.
- **Not run on any phone or emulator.** Every page tweak/selector and all UI flows are untested on screen.

## What Crux is now
Android only. Platforms: **Instagram, X, LinkedIn, Reddit** (live web tabs, each in its own browser profile) + **RSS** with the paged reader.

### Removed
- Native platforms: Mastodon, Bluesky, Misskey, Nostr, Pixiv, Fanbox, Weibo (modules + code).
- Desktop, iOS and web targets.
- Dead screens: Discover, Notifications, Search, DMs, Lists, classic composer, draft box, "Add account" login screens, local follows UI (DB tables kept for upgrades).

### Added / fixed
- **Home:** a pill for every platform you enable (IG and LinkedIn no longer need a sign-in to appear), "All RSS" pill, empty state, no onboarding flash, no 1.5 s polling loop.
- **Privacy & Tweaks** (Settings, and side panel): per site Standard/Strict protection (Standard = hide-only, default for IG/X/LinkedIn; Strict = also blocks trackers, default for Reddit), focus toggles (Reels/Explore, Who to follow/trends, promoted, app nags), block autoplay, clear one site's data, screenshot protection, Global Privacy Control, link cleaning.
- **Links:** every link opens in your default browser (tracking removed first), except Instagram/X/LinkedIn/Reddit links, which open in their Crux tab.
- **Share / quick tile / shortcut** open the Crux compose sheet (text only).
- **RSS:** lenient feed parsing, US time-zone dates, `<br>`/numbered lists/relative images in articles, defaults seeded once, no duplicate feeds, sturdier OPML import, reader remembers position as a fraction, bookmark waits for the article.

## Check first when you install
1. First launch → onboarding → home shows your chosen pills.
2. Log in inside each tab (I never enter passwords). Instagram: keep to normal use; Standard protection.
3. Privacy & Tweaks: toggles apply after the tab reloads; selectors may miss on live sites.
4. Tap links in an RSS article → default browser. Tap a reddit.com link inside X → Reddit tab.
5. Share text from another app → Crux compose sheet.

## Known gaps
- Share targets for images/video were removed (compose sheet is text only).
- Removing a tab in tab settings can bring it back while the platform is enabled.
- Some non-English strings still mention Mastodon.
- `androidx.browser` dependency now unused; `HardenedBrowserScreen` still present.
- Instagram/LinkedIn "shadow account" code still exists (used for sign-in detection only).
