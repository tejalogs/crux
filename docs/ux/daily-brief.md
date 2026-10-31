# Daily Brief ("Catch Me Up") Redesign Plan

## 1. Problem Statement
The current Daily Briefing ("Catch Me Up" / Executive Pulse) has several UX, accessibility, and code quality issues:
1. **Intrusive and uncollapsed by default**: The inline `CatchMeUpDigestCard` renders expanded by default, taking up more than a full screen of vertical space above the home feed.
2. **Sub-48dp tap targets**: Header icon buttons are sized at 30dp.
3. **Marketing jargon and misleading copy**: Labels like "Signals", "Morning Pulse / Evening Dispatch", "Curated Takeaways • 100% Private", "Distilled locally • 100% Zero Egress", and "On-Device Speech Synthesis • Gemma 4 Edge" (the TTS is standard Android `TextToSpeech`, not Gemma).
4. **Massive duplication**: `CatchMeUpDigestCard.kt` (~900 lines) and `ExecutiveBriefingBottomSheet.kt` (~810 lines) duplicate almost identical layout logic, animations, and audio script synthesis.
5. **Continuous animation**: Equalizers animate continuously in a background loop even when audio is not playing or when reduced motion is preferred by the user, wasting battery.

---

## 2. ASCII Wireframes

### A. Inline Card (`CatchMeUpDigestCard` in Home Timeline)
Max height: ~25% of viewport height. Collapsed, compact, clean.

```text
+-------------------------------------------------------------+
|  [Sparkles]  Catch up                                   [X] |
|              24 posts since 9:00 am                         |
|                                                             |
|  [Avatar]  AI in Production                                 |
|            pgvector slashes cluster maintenance while...    |
|                                                             |
|  [Avatar]  Clean Energy                                     |
|            Solid-state battery yields reach volume p...     |
|                                                             |
|  [Avatar]  Open Source Craft                                |
|            SQLite adds vector search support in lat...      |
|                                                             |
|  [ Open brief ]                                   [Listen]  |
+-------------------------------------------------------------+
```

### B. Full Brief Modal (`ExecutiveBriefingBottomSheet`)
Reads like a calm, structured news briefing.

```text
+-------------------------------------------------------------+
|  Your catch-up                                          [:] |
|  24 posts since 9:00 am                                 [X] |
|                                                             |
|  [ All (6) ] [ AI ] [ Energy ] [ Open Source ]              |
|                                                             |
|  +-------------------------------------------------------+  |
|  | [Avatar] Alex Vance  @alex@tech.social  [Mastodon]    |  |
|  | AI in Production                                      |  |
|  | Moving from dedicated vector clusters back to Postgres|  |
|  | simplified operations and cut cloud spend by 40%.     |  |
|  | 42 replies · 128 boosts                               |  |
|  |                                          [Read post]  |  |
|  +-------------------------------------------------------+  |
|  | [Avatar] Elena Rostova  @elena@climate.org [Bluesky]  |  |
|  | Clean Energy                                          |  |
|  | Breakthrough in solid-state battery manufacturing...  |  |
|  | 19 replies · 84 likes                                 |  |
|  |                                          [Read post]  |  |
|  +-------------------------------------------------------+  |
|                                                             |
|  =========================================================  |
|  [Play/Pause]  Catch-up audio                 [Speed: 1x ▾] |
+-------------------------------------------------------------+
```

---

## 3. List of Composables & Architecture

All shared UI components are extracted into focused composables in `compose-ui/src/commonMain/kotlin/dev/dimension/flare/ui/component/status/`, each under ~150 lines:

1. **`DigestHighlightRow.kt`**:
   - `DigestCompactHighlightRow`: Compact row for the inline card (author avatar 24dp, one-line bold topic, one-line summary with ellipsis).
   - `DigestDetailHighlightCard`: Full card for the bottom sheet (author row with avatar, name, handle, platform pill, topic title, full summary, engagement text, "Read post" button).
   - TalkBack accessibility semantics merged: `"$topic, by ${authorName ?: "author"}: $summary"`.
2. **`DigestAudioBar.kt`**:
   - Docked audio playback control for the bottom sheet and compact button for the card.
   - Animate equalizer bars **only while speaking** (`isSpeaking == true`).
   - Checks `isReducedMotion()`: static bars when reduced motion is enabled or when stopped.
   - Speed selector menu (1.0x, 1.25x, 1.5x, 2.0x).
   - Handles audio error/unsupported state gracefully.
3. **`DigestTimeFormatting.kt`**:
   - Multiplatform helper formatting `Instant` into localized time (`h:mm a`, e.g. "9:00 am").
4. **`ExpressiveTopicFilterChipStrip`** (in `HomeExpressiveComponents.kt`):
   - Sibling composable to `ExpressiveHomeTabStrip` for filtering string topics using Material 3 Expressive `ToggleButton`.
5. **Refactored `CatchMeUpDigestCard.kt`**:
   - Compact inline card (max 3 highlights, dismiss button 48dp, "Open brief" button, audio listen trigger).
6. **Refactored `ExecutiveBriefingBottomSheet.kt`**:
   - Full-height news-brief sheet using the extracted components, top-bar overflow menu with "Make a carousel", and docked audio bar.

---

## 4. Copy Table (Old Marketing Copy → Plain Language)

| Location | Old Copy | New Copy | Notes |
|---|---|---|---|
| Card title | "Morning Pulse / Midday Brief / Evening Dispatch / Nightly Digest" / "Signals" | "Catch up" | Plain, consistent, honest. |
| Card subtitle | "Curated Takeaways • 100% Private" / "Distilled locally • 100% Zero Egress" | "%d posts since %s" (e.g. "24 posts since 9:00 am") | Concrete information. |
| Card primary CTA | "Full Briefing ↗" / "Tap to expand executive briefing" | "Open brief" | Clear verb phrase. |
| Card audio CTA | "Listen to this digest aloud" / "Hands-Free Audio Dispatch" | "Listen" (contentDescription: "Listen to catch up") | Concise, min 48dp tap target. |
| Card dismiss CTA | 30dp X button with no content description | Dismiss button (contentDescription: "Dismiss catch up") | Min 48dp tap target. |
| Sheet title | "Executive Briefing" / "Daily Briefing • Executive Pulse" | "Your catch-up" | Sentence case, friendly. |
| Sheet subtitle | "N curated signals distilled from N unread posts" | "%d posts since %s" | Clear count and timeline. |
| Carousel CTA | "✨ Synthesize Carousel" | "Make a carousel" | Moved to top bar overflow menu. |
| Read post CTA | "Full Post ↗" / "Deep Insight" | "Read post" | Standard action. |
| Audio header | "Executive Audio Briefing Playing" / "On-Device Speech Synthesis • Gemma 4 Edge" | "Catch-up audio" (Playing / Paused) | Doesn't claim Gemma when using standard TTS. |
| Audio speed | "1.0x Speed", "1.25x Speed" | "1.0x", "1.25x", "1.5x", "2.0x" | Clean dropdown menu. |
| Empty state | (none) | "No highlights in this catch-up" | Helpful fallback. |
| Audio unavailable | (none) | "Audio playback is unavailable on this device" | Honest device error. |
