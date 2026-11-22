package org.crux.ui.common

import androidx.compose.ui.platform.UriHandler
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AppUriHandlerInternalLinkTest {
    @Test
    fun exactAppLinkFromCruxCodeStillOpens() {
        val opened = mutableListOf<String>()
        val handler = AppUriHandler(uriHandler { opened.add(it) }) { error("Must open link") }

        handler.openUri("crux://0a0b0c")

        assertEquals(listOf("crux://0a0b0c"), opened)
    }

    @Test
    fun whitespaceWrappedAppLinkIsRefusedInsteadOfTrusted() {
        var failures = 0
        val handler = AppUriHandler(uriHandler { error("Must not open a padded app link") }) { failures++ }
        val padded = listOf(" crux://0a0b0c", "crux://0a0b0c\n", "\tcrux://0a0b0c ")

        padded.forEach(handler::openUri)

        assertEquals(padded.size, failures)
    }

    @Test
    fun paddedWebLinksStillOpen() {
        val opened = mutableListOf<String>()
        val handler = AppUriHandler(uriHandler { opened.add(it) }) { error("Must open link") }

        handler.openUri("  https://example.com/post  ")

        assertEquals(listOf("https://example.com/post"), opened)
    }

    private fun uriHandler(open: (String) -> Unit): UriHandler =
        object : UriHandler {
            override fun openUri(uri: String) = open(uri)
        }
}
