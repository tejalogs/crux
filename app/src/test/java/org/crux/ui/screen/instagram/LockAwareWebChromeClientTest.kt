package org.crux.ui.screen.instagram

import android.webkit.JsPromptResult
import android.webkit.JsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class LockAwareWebChromeClientTest {
    /** A JsResult wired to a receiver that counts completions (its constructor is hidden API). */
    private class TrackedResult<T : JsResult>(
        type: Class<T>,
    ) {
        var completions = 0
            private set
        val result: T

        init {
            val receiverType = Class.forName("android.webkit.JsResult\$ResultReceiver")
            val receiver =
                Proxy.newProxyInstance(receiverType.classLoader, arrayOf(receiverType)) { proxy, method, args ->
                    when (method.name) {
                        "onJsResultComplete" -> {
                            completions++
                            null
                        }
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        "toString" -> "TrackedReceiver"
                        else -> null
                    }
                }
            result = type.getDeclaredConstructor(receiverType).apply { isAccessible = true }.newInstance(receiver)
        }

        val confirmed: Boolean
            get() = JsResult::class.java.getDeclaredMethod("getResult").apply { isAccessible = true }.invoke(result) as Boolean
    }

    private val lockedClient = LockAwareWebChromeClient(isUnlocked = { false })
    private val unlockedClient = LockAwareWebChromeClient(isUnlocked = { true })

    // Robolectric's JsResult does not route cancel() to a receiver, so these check the decision:
    // while locked every page dialog is reported as handled (the WebView never shows it).
    @Test
    fun handlesEveryPageDialogWhileLocked() {
        assertTrue(lockedClient.onJsAlert(null, "https://example.com", "hi", null))
        assertTrue(lockedClient.onJsConfirm(null, "https://example.com", "sure?", null))
        assertTrue(lockedClient.onJsPrompt(null, "https://example.com", "password?", "", null))
        assertTrue(lockedClient.onJsBeforeUnload(null, "https://example.com", "leave?", null))
    }

    @Test
    fun handlesMissingResultWhileLocked() {
        assertTrue(lockedClient.onJsAlert(null, null, null, null))
    }

    @Test
    fun leavesDialogsToWebViewWhenUnlocked() {
        val alert = TrackedResult(JsResult::class.java)
        val confirm = TrackedResult(JsResult::class.java)
        val prompt = TrackedResult(JsPromptResult::class.java)
        val beforeUnload = TrackedResult(JsResult::class.java)

        assertFalse(unlockedClient.onJsAlert(null, "https://example.com", "hi", alert.result))
        assertFalse(unlockedClient.onJsConfirm(null, "https://example.com", "sure?", confirm.result))
        assertFalse(unlockedClient.onJsPrompt(null, "https://example.com", "name?", "", prompt.result))
        assertFalse(unlockedClient.onJsBeforeUnload(null, "https://example.com", "leave?", beforeUnload.result))

        assertEquals(0, alert.completions + confirm.completions + prompt.completions + beforeUnload.completions)
    }

    @Test
    fun runIfUnlockedSkipsPageActionsWhileLocked() {
        var ran = false
        lockedClient.runIfUnlocked { ran = true }
        assertFalse(ran)
    }

    @Test
    fun runIfUnlockedRunsPageActionsWhenUnlocked() {
        var ran = false
        unlockedClient.runIfUnlocked { ran = true }
        assertTrue(ran)
    }

    @Test
    fun runIfUnlockedReadsLockStateOnEachCall() {
        var unlocked = true
        val client = LockAwareWebChromeClient(isUnlocked = { unlocked })
        var runs = 0
        client.runIfUnlocked { runs++ }
        unlocked = false
        client.runIfUnlocked { runs++ }
        assertEquals(1, runs)
    }
}
