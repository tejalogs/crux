package org.crux.ui.screen.home

import android.view.View
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class LiveWebKeyboardTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun siteWebViewsAskTheKeyboardNotToLearnWhatIsTyped() {
        val view = IncognitoKeyboardWebView(context)
        val attrs = EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_SEARCH }
        view.onCreateInputConnection(attrs)
        assertEquals(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, attrs.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
    }

    @Test
    fun captureRulesKeepPageTextOutOfContentCaptureButLeaveAutofillOn() {
        val view = IncognitoKeyboardWebView(context)
        view.applyLiveWebCaptureRules()
        assertEquals(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS, view.importantForContentCapture)
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_AUTO, view.importantForAutofill)
        view.applyPrivateScreenAutofill("https://www.linkedin.com/messaging/thread/1/")
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, view.importantForAutofill)
        view.applyPrivateScreenAutofill("https://www.linkedin.com/login")
        assertTrue(view.importantForAutofill == View.IMPORTANT_FOR_AUTOFILL_AUTO)
    }
}
