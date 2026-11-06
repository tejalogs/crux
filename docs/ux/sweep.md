# UX Sweep: Custom Screens Audit & Fixes

This document records the visual and interaction quality sweep across Flare's custom screens following Material 3 Expressive guidelines, touch target requirements (minimum 48dp), localized plain language, and theme-token conformance (zero hardcoded hex colors).

Format: `screen, problem, fix applied, file:line`

---

### 1. Smart Reply (`SmartReplyBottomSheet.kt`)
* **Screen**: Smart Reply Bottom Sheet
* **Problem**: Hardcoded rainbow gradient brushes (`gemmaSpectrumBrush`, `neuralShimmerBrush`), marketing buzzwords ("NEURAL SYNTHESIS", "On-Device Gemma synthesizing contextual reasoning • Zero Egress", fake latency/confidence), non-standard tone options, sub-48dp action buttons, unstructured preview card.
* **Fix Applied**: Refactored entirely to Material 3 Expressive theme color roles; eliminated hardcoded hex colors; replaced marketing fluff with honest, plain language ("Generating reply options on device…"); implemented 4 core requested tone chips (Casual, Professional, Direct, Skeptical) with clear active selection styling; created structured reply preview card with 48dp Copy & Reply buttons.
* **Location**: `app/src/main/java/org/crux/ui/screen/status/action/SmartReplyBottomSheet.kt:180-350`

---

### 2. OmniScribe / Carousel Exporter (`OmniScribeBottomSheet.kt` & `CarouselBitmapRenderer.kt`)
* **Screen**: OmniScribe Post Formatter & Carousel Exporter
* **Problem**: Hardcoded marketing headlines ("OmniScribe Neural Thread & Deck Synthesizer", "Source Context Matrix"), hardcoded `#00C853` hex accent, sub-48dp quick export buttons and pager arrow navigation targets.
* **Fix Applied**: Replaced titles with localized plain language strings (`stringResource(R.string.omni_scribe_format_post)`, `stringResource(R.string.omni_scribe_source_post)`, `stringResource(R.string.omni_scribe_voice_style)`); removed hex colors in favor of `MaterialTheme.colorScheme.primary`; increased export buttons ("LinkedIn PDF", "Save PNGs") and action buttons ("Share", "Composer", "Broadcast") to min 48dp height; updated pager previous/next buttons to 48dp with localized accessibility descriptions.
* **Location**: `app/src/main/java/org/crux/ui/screen/status/action/OmniScribeBottomSheet.kt:270-380, 740-800`

---

### 3. LinkedIn Web Screen & Bridge (`LinkedInWebScreen.kt`)
* **Screen**: LinkedIn Web Screen & Bridge
* **Problem**: Hardcoded LinkedIn brand blue `#0A66C2` in top app bar, progress bar, and floating dock; intrusive sync badge and non-standard actions; noisy "Flare LinkedIn Shield" marketing dialog; sub-48dp tap targets on floating dock icon buttons (36dp); lack of standard overflow menu and browser intent.
* **Fix Applied**: Cleaned top app bar with theme color roles (`primaryContainer`/`primary`); quiet non-blocking sync status text (`R.string.web_status_synced` / `R.string.web_status_syncing`); 48dp external browser action button; 48dp overflow menu with "Privacy info", "Reload", and "Clear session"; replaced confusing jargon in privacy dialog with plain-language explanation (`R.string.web_privacy_dialog_description`); increased floating dock buttons to 48dp.
* **Location**: `app/src/main/java/org/crux/ui/screen/linkedin/LinkedInWebScreen.kt:360-480, 665-790, 800-845`

---

### 4. Instagram Web Screen & Bridge (`InstagramWebScreen.kt`)
* **Screen**: Instagram Web Screen & Bridge
* **Problem**: Raw hex gradient on favicon container (`#833AB4`, `#FD1D1D`, `#FCB045`); marketing badge "Privacy Sandbox Active"; verbose sandbox marketing bullet points in dialog ("Meta Pixels", "Zero Checkpoint Ban Risk"); sub-48dp floating dock toggle (44dp) and action buttons; missing overflow menu.
* **Fix Applied**: Replaced favicon gradient with `primaryContainer`/`onPrimaryContainer`; quiet non-blocking sync status under title; added 48dp external browser button and 48dp overflow menu with "Privacy info", "Reload", and "Clear session"; replaced dialog content with plain-language explanation (`R.string.web_privacy_dialog_description`); updated floating dock toggle and actions to 48dp.
* **Location**: `app/src/main/java/org/crux/ui/screen/instagram/InstagramWebScreen.kt:395-445, 465-595, 765-895`

---

### 5. Navigation Drawer Header (`HomeScreen.kt`)
* **Screen**: Navigation Drawer Header
* **Problem**: Legacy name `PrismDrawerHeader`, "Prism / Sovereign Client" branding, uppercase drawer section title ("SOVEREIGNTY AND SYSTEM"), account switcher button lacked explicit 48dp min touch target.
* **Fix Applied**: Renamed `PrismDrawerHeader` to `FlareDrawerHeader`; updated drawer app title and subtitle to "Flare" / "Decentralized and multi-protocol"; updated section header to sentence case "Settings and system"; enforced `Modifier.defaultMinSize(minHeight = 48.dp)` on the account switcher button.
* **Location**: `app/src/main/java/org/crux/ui/screen/home/HomeScreen.kt:164-167, 275, 765-840`

---

### 6. Main Settings Screen (`SettingsScreen.kt`)
* **Screen**: Main Settings Screen
* **Problem**: Hardcoded marketing jargon: "Sovereign Privacy Hub & Zero-Trust Egress", "Live zero-trust network inspector, tracker blocker, and sovereign bridges", "On-Device Gemma 4 E2B AI, zero-egress semantic search, and feed digests".
* **Fix Applied**: Replaced with localized plain-language strings: `R.string.privacy_hub_title` ("Privacy and network monitor"), `R.string.privacy_hub_subtitle` ("Local on-device processing"), and `R.string.settings_ai_config_description` ("Set up AI providers and summarization").
* **Location**: `app/src/main/java/org/crux/ui/screen/settings/SettingsScreen.kt:293-311, 391-394`

---

### 7. Privacy and Network Monitor (`PrivacySettingsScreen.kt`)
* **Screen**: Privacy and Network Monitor
* **Problem**: Marketing buzzwords ("Zero-Trust Egress Monitor", "100% On-Device Sovereign Privacy", "Cloud AI Leakage", "Active Sovereign Web Bridges", "Gemma 4 Local", "Zero Cloud Leakage Verified"); raw hex colors (`#00C853`, `#00B4D8`, `#8A2BE2`).
* **Fix Applied**: Replaced all hex gradients and colors with Material 3 Expressive theme color roles (`primaryContainer`, `secondaryContainer`, `primary`, `secondary`, `tertiary`); plain-language descriptions (`R.string.privacy_hub_title`, `R.string.privacy_hub_subtitle`, `R.string.privacy_cloud_requests`, `R.string.privacy_active_bridges`); renamed `SovereignBridgeChip` to `WebBridgeChip`; updated benchmark card labels to "On-device AI benchmark" and "Hardware acceleration and performance".
* **Location**: `app/src/main/java/org/crux/ui/screen/settings/PrivacySettingsScreen.kt:130-210, 350-440, 510-590`

---

### 8. AI Configuration (`AiConfigScreen.kt`)
* **Screen**: AI Configuration Screen
* **Problem**: Monolithic unorganized list of AI settings without clear visual separation between provider setup and AI feature switches.
* **Fix Applied**: Added structured category headers with `MaterialTheme.typography.titleSmall` and `MaterialTheme.colorScheme.primary`: "Model provider" (`R.string.settings_ai_section_provider`) above the provider selector, and "AI features" (`R.string.settings_ai_section_features`) above the AI Agent and Summarization toggles.
* **Location**: `app/src/main/java/org/crux/ui/screen/settings/AiConfigScreen.kt:127-135, 631-640`

---

### 9. Storage & Cache Management (`StorageScreen.kt`)
* **Screen**: Storage and Cache Management Screen
* **Problem**: Missing category header for initial cache-clearing section; hardcoded string headers ("Offline Reading & Media Cache", "Database Health & Retention").
* **Fix Applied**: Added initial category header `R.string.settings_storage_section_data_cache` ("Data and cache management"); localized existing headers using `R.string.settings_storage_section_offline` and `R.string.settings_storage_section_database`.
* **Location**: `app/src/main/java/org/crux/ui/screen/settings/StorageScreen.kt:255-265, 400-410, 525-535`
