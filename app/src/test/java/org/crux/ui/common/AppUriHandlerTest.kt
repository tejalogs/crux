package org.crux.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.compose.ui.platform.AndroidUriHandler
import androidx.compose.ui.platform.UriHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AppUriHandlerTest {
    @Test
    fun rejectsEmptyAndRelativeLinksBeforeLaunching() {
        var failures = 0
        val handler = AppUriHandler(uriHandler { error("Must not launch an activity") }) { failures++ }
        val invalidLinks = listOf("", " ", "#section", "/posts/123", "//example.com/post", "example.com/post", "https://", "https:///post")

        invalidLinks.forEach(handler::openUri)

        assertEquals(invalidLinks.size, failures)
    }

    @Test
    fun handlesTheComposeExceptionWhenNoActivityCanOpenTheLink() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        var failures = 0
        val handler = AppUriHandler(AndroidUriHandler(activity)) { failures++ }

        handler.openUri("uninstalled://post/123")
        handler.openUri("https://example.com/post")

        assertEquals(2, failures)
    }

    @Test
    fun reportsFailureWhenNoBrowserIsInstalled() {
        var failures = 0
        val handler =
            AppUriHandler(uriHandler { throw IllegalArgumentException("Cannot open link", ActivityNotFoundException()) }) { failures++ }

        handler.openUri("https://example.com/post")

        assertEquals(1, failures)
    }

    @Test
    fun sendsWebLinksToTheDefaultBrowserWithTrackingRemoved() {
        val opened = mutableListOf<String>()
        val handler = AppUriHandler(uriHandler { opened.add(it) }) { error("Must open link") }

        handler.openUri("  https://example.com/post?utm_source=x&id=7&fbclid=abc  ")
        handler.openUri("https://l.instagram.com/?u=https%3A%2F%2Fexample.com%2Fa%3Futm_medium%3Dz")

        assertEquals(listOf("https://example.com/post?id=7", "https://example.com/a"), opened)
    }

    @Test
    fun preservesOtherSchemesAndAppLinks() {
        val opened = mutableListOf<String>()
        val handler = AppUriHandler(uriHandler { opened.add(it) }) { error("Must open link") }

        handler.openUri("mailto:hello@example.com")
        handler.openUri("crux://abc123")

        assertEquals(listOf("mailto:hello@example.com", "crux://abc123"), opened)
    }

    @Test
    fun handlesActivityLaunchErrorsButDoesNotHideProgrammingErrors() {
        var failures = 0
        listOf(ActivityNotFoundException(), SecurityException()).forEach { failure ->
            AppUriHandler(uriHandler { throw failure }) { failures++ }.openUri("custom://post")
        }
        assertEquals(2, failures)

        val handler = AppUriHandler(uriHandler { error("Unexpected failure") }) { failures++ }
        assertThrows(IllegalStateException::class.java) { handler.openUri("custom://post") }
        assertEquals(2, failures)
    }

    private fun uriHandler(open: (String) -> Unit): UriHandler =
        object : UriHandler {
            override fun openUri(uri: String) = open(uri)
        }
}
