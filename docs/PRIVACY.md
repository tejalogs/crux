# Crux privacy policy

Crux is an Android app published under the GNU AGPL-3.0. This page says what it stores on your device and what it sends over the network. If the code and this page ever disagree, the code is the source of truth, and an issue at https://github.com/tejalogs/crux/issues is welcome.

## Short version

- Crux has no servers, no account, no ads, no analytics and no crash reporting. Nothing is ever sent to the Crux developer.
- Everything Crux stores stays on your device. Android backup and device-to-device transfer are switched off for the app.
- Crux talks directly to the sites you open, to the RSS feeds you follow, to GitHub to check for app updates (you can turn this off), and to Hugging Face when you download an on-device AI model.
- Inside a site you are signed in to, that site still sees and records what you do there. See [What Crux cannot protect inside a signed-in site](#what-crux-cannot-protect-inside-a-signed-in-site).

## What Crux stores on your device

All of this lives in the app's private storage and is removed when you uninstall the app or clear its data.

- **Web sessions.** Instagram, X, LinkedIn, Reddit and mastodon.social each load in their own WebView profile when the device's WebView supports multiple profiles, so the cookies, local storage and cache of one site are not shared with another. On a device without that support the sites share one WebView cookie jar and storage, which is still private to Crux. You sign in on the site's own page; Crux never sees or stores your password. Inside Crux you can clear one site's data (Settings > Privacy) or sign out on the site.
- **RSS data.** The feeds you follow, articles you opened, cached feed content, your reading list (including offline copies of text and images you saved), and the place where you stopped reading each article.
- **Your choices.** Settings, content filters, search history and the list of platforms you turned on. Current versions of Crux have no field for AI or translation API keys. Translation keys saved by an older version can still be in your settings; they are stored encrypted, with a key kept in the app's private storage. An AI server address and key saved by an older version are dropped when Crux reads its settings.
- **Local history.** Posts and articles you viewed can be kept for searching on your device, with a retention period you set under Storage.
- **Diagnostics.** An optional on-device app log, and an optional traffic log of the hosts Crux's own HTTP client contacts (feeds, articles and similar), with what was removed or blocked. The traffic log does not include requests made by the web tabs. Both stay on the device.

### Data export

Settings > Storage > Export data writes a JSON file to a location you choose. It contains your feeds, filters, search history and settings. **The settings in the file include any API keys still saved in Crux, in readable form**, for example translation keys left over from an older version. It does not contain web sessions or cookies: those stay in each site's WebView profile. Treat the file as private; Crux shows a warning before exporting.

## What Crux sends over the network

- **The web sites you open.** The Instagram, X, LinkedIn, Reddit and mastodon.social tabs load the real sites in a WebView, so those companies receive the usual requests and see your activity while you are signed in, under their own privacy policies. Most of Crux's page tweaks only change what is displayed. The optional Strict level refuses requests to known tracker hosts; it is the default for Reddit and mastodon.social only. Crux removes tracking parameters from links it opens. If you turn on Global Privacy Control (off by default), Crux sets the `navigator.globalPrivacyControl` JavaScript property on sites set to Strict only. It is not sent as a `Sec-GPC` request header, and sites may ignore it.
- **RSS feeds.** Crux fetches the feeds you add, and the article pages and images they point to, directly from their publishers. A feed address that starts with `rsshub://` is fetched through the public RSSHub server you choose in the feed editor, so that server sees the feed path.
- **Links.** Links you tap open in your phone's default browser. Links to the sites Crux shows can open in that site's own Crux tab instead.
- **App update check.** Unless you turn it off in Settings > About, Crux asks the GitHub releases API (api.github.com) whether a newer version exists. It checks at most once per app launch, at least 6 hours after the last check, with the user agent `Crux-Android-App`. GitHub sees your IP address and that a Crux install checked for updates. If you choose to install an update, the APK is downloaded from GitHub.
- **On-device AI model downloads.** When you download an AI model, it comes from Hugging Face (huggingface.co and its download servers). Hugging Face sees your IP address and which model you chose. Your prompts are not sent.
- **Safe Browsing.** Android's WebView checks page addresses against Google Safe Browsing in every site tab and in the built-in browser page. It compares a hash of the address with a list kept on the device; only when part of a hash matches does it ask Google about that short hash prefix, not the full address. It needs Google Play Services and does nothing on devices without them. This is a WebView feature, not something Crux operates.

### AI features (smart replies, summaries, carousels)

Crux AI features run on your device with an on-device model. Prompts and the text they are about are not sent to any AI server, and no cloud AI accounts or keys are used. The only network traffic is the model download described above. If you enter the local address of an Ollama server running on this phone (off unless you change it; only loopback addresses are accepted), the AI agent sends prompts to that server. Any app on the phone could be listening on that address.

Older versions of Crux could point AI at an OpenAI-compatible server. A setting like that is never used. When Crux reads its settings it switches that setting to on-device and drops the server address and key, so AI features, the AI agent, Smart Reply and exports all see on-device. Importing a backup never changes the AI setting on this device.

## What Crux cannot protect inside a signed-in site

Crux shows each site's real web page. Some things no app can stop without changing how the site works, and trying would make your account look like it runs a modified client, which is how accounts get flagged or locked.

- **The site's own tracking.** While you are signed in, the site's pages record what you view, how long you look at it, how far you scroll, which videos you watch, what you type and search, and what you tap. This goes to the same servers that send you the feed, so it cannot be blocked separately.
- **Ads.** Ads are still requested, chosen using your profile, delivered inside the feed and counted as shown. Crux can hide some of them on screen, but the site still records them.
- **Your activity on the site.** The site knows everything you do on it: follows, likes, messages, posts and searches. That is the service itself.
- **Linking across sites.** All sites see the same IP address and the same device details (screen, fonts, graphics, time zone, and a browser version that says it is an Android WebView). Sites and ad networks can use these to link your visits even with separate profiles. Crux does not fake these details, because faking them on signed-in accounts is detectable and gets accounts flagged. Only a VPN changes the IP address, and some sites challenge sign-ins from VPN or Tor addresses.
- **Signing in with Google or Apple.** Using Google or Apple to sign in tells that company which service you use. Instagram's sign-in can also link your account with Facebook and Meta's Accounts Center. Signing in with an email address and password avoids this.
- **Short links.** Opening a t.co or lnkd.in link tells X or LinkedIn that the link was opened.
- **The WebView's own traffic.** The Android WebView, which is part of your phone, downloads its own updates and Safe Browsing lists and may send crash reports if you allowed usage and diagnostics on your phone. Crux turns off the WebView's usage statistics, which is the only switch an app has.
- **Keyboard and accessibility apps.** Your keyboard app can learn from, and sometimes sync, what you type. Accessibility tools you installed can read the screen. Crux cannot fully control either.

What you can do: change the settings on the site itself. Settings > Privacy > Privacy Checkup opens the privacy settings each site offers (for example ad personalisation, off-site activity, AI training on X and LinkedIn, and download your data) in that site's Crux tab. Crux only opens the page; you make the change there.

## Permissions

- **Internet**, for all of the above.
- **Notifications**, for update and download notices.
- **Foreground service and media playback**, for audio and video that keep playing.
- **Install other apps** (`REQUEST_INSTALL_PACKAGES`), so Crux can hand an update you downloaded from GitHub to Android's installer. Android asks you before anything is installed.
- **Write to external storage**, requested only on Android 9 and older, to save images you export.

## Children

Crux is not directed at children and collects no data about anyone.

## Changes

Changes to this policy are made in the public repository, where the history is visible.

## Contact

Open an issue at https://github.com/tejalogs/crux/issues.
