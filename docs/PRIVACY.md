# Crux privacy policy

Crux is an Android app published under the GNU AGPL-3.0. This page says what it stores on your device and what it sends over the network. If the code and this page ever disagree, the code is the source of truth, and an issue at https://github.com/tejalogs/crux/issues is welcome.

## Short version

- Crux has no servers, no account, no ads, no analytics and no crash reporting. Nothing is ever sent to the Crux developer.
- Everything Crux stores stays on your device. Android backup and device-to-device transfer are switched off for the app.
- Crux talks directly to the sites you open, to the RSS feeds you follow, and to translation or AI providers only when you use those features.

## What Crux stores on your device

All of this lives in the app's private storage and is removed when you uninstall the app or clear its data.

- **Web sessions.** Instagram, X, LinkedIn and Reddit each load in their own WebView profile when the device's WebView supports multiple profiles, so cookies, local storage and cache of one site are not shared with another. On a device without that support the sites share the WebView's storage, which is still private to Crux. You sign in on the site's own page; Crux never sees or stores your password. Inside Crux you can clear one site's data (Settings > Privacy) or sign out on the site.
- **RSS data.** The feeds you follow, articles you opened, cached feed content, your reading list (including offline copies of text and images you saved), and the place where you stopped reading each article.
- **Your choices.** Settings, content filters, search history, the list of platforms you turned on, and any API keys or server addresses you type into the AI or translation settings. API keys are stored as plain text inside the app's private storage.
- **Local history.** Posts and articles you viewed can be kept for searching on your device, with a retention period you set under Storage.
- **Diagnostics.** An optional on-device app log and a traffic log that lists requests made by the web tabs, so you can see what was blocked. They stay on the device.

### Data export

Settings > Storage > Export data writes a JSON file to a location you choose. It contains your feeds, filters, search history and settings, including API keys you entered for AI or translation providers. It does not contain web sessions or cookies: those stay in each site's WebView profile. Treat the file as private; Crux shows a warning before exporting.

## What Crux sends over the network

- **The web sites you open.** The Instagram, X, LinkedIn and Reddit tabs load the real sites in a WebView, so those companies receive the usual requests and see your activity while you are signed in, under their own privacy policies. Crux's page tweaks only change what is displayed, and the optional Strict mode blocks known tracker requests; neither makes you invisible to the site itself. Crux removes tracking parameters from links it opens and sends the Global Privacy Control signal if you enable it.
- **RSS feeds.** Crux fetches the feeds you add, and the article pages and images they point to, directly from their publishers. A feed address that starts with `rsshub://` is fetched through the public RSSHub server you choose in the feed editor, so that server sees the feed path.
- **Links.** Links you tap open in your phone's default browser. Links to Instagram, X, LinkedIn and Reddit open in Crux's own web tab for that site.
- **Safe Browsing.** Android's WebView may check page addresses against Google Safe Browsing in the Instagram tab and in the built-in browser page. This is a WebView feature, not something Crux operates.

### Translation (only when you use it)

Text is sent for translation only when you tap Translate on an article, or after you turn on auto-translate (off by default). Before the first translation Crux tells you which provider will receive the text and asks you to continue. The provider is your choice in Settings > Translation:

- Google Translate web endpoint (translate.google.com), the default
- DeepL (api.deepl.com or api-free.deepl.com), with your own key
- Google Cloud Translation, with your own key
- A LibreTranslate server you enter
- The on-device translation engine (which sends nothing) or an optional translation server you configure

The provider's own privacy policy applies to the text you send.

### AI features (smart replies, summaries, carousels)

Crux AI features run entirely on your device with zero data egress. No text is ever sent to an external AI server, and no AI API keys or cloud accounts are used.

## Permissions

Internet access (all of the above), notifications, and foreground media playback for audio and video. Writing to external storage is requested only on Android 9 and older, to save images you export.

## Children

Crux is not directed at children and collects no data about anyone.

## Changes

Changes to this policy are made in the public repository, where the history is visible.

## Contact

Open an issue at https://github.com/tejalogs/crux/issues.
