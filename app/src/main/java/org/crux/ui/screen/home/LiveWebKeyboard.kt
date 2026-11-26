package org.crux.ui.screen.home

import android.content.Context
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView

/**
 * Asks the keyboard not to learn from what is typed: no personal dictionary, no suggestion
 * history, no cloud sync of typed words. Gboard, SwiftKey and Samsung Keyboard honour it. It is a
 * hint between the app and the keyboard only; the page and the site never see it.
 */
internal fun withNoPersonalizedLearning(imeOptions: Int): Int = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING

/**
 * The WebView every site page, popup and sign-in window is built on. The only change from a plain
 * WebView is the keyboard hint in [onCreateInputConnection]: the page's own field types and IME
 * actions (search, send, next) are kept, since the flag is added after WebView fills them in.
 */
internal open class IncognitoKeyboardWebView(
    context: Context,
) : WebView(context) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        outAttrs.imeOptions = withNoPersonalizedLearning(outAttrs.imeOptions)
        return connection
    }
}

/**
 * Keeps page text out of Android's content-capture service (Android System Intelligence, which
 * feeds on-device suggestions and assistants) on Android 11+. Autofill is left on: password
 * managers must keep filling sign-in pages, so only messaging pages switch it off (see
 * [applyPrivateScreenAutofill]).
 */
internal fun View.applyLiveWebCaptureRules() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
    }
    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_AUTO
}

/**
 * Autofill rule for the page currently shown: off for a DM thread (nothing there for a password
 * manager to fill, and the thread text would otherwise reach the autofill service), the system
 * default everywhere else, so sign-in pages keep working with password managers.
 */
internal fun autofillModeFor(url: String?): Int =
    if (isMessagingUrl(url)) View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS else View.IMPORTANT_FOR_AUTOFILL_AUTO

internal fun View.applyPrivateScreenAutofill(url: String?) {
    val mode = autofillModeFor(url)
    if (importantForAutofill != mode) importantForAutofill = mode
}
