package dev.dimension.flare.data.datasource.mastodon

import kotlin.test.Test
import kotlin.test.assertEquals

class MastodonSearchQueryNormalizationTest {
    @Test
    fun normalizesThreadsProfileUrlsAndHandles() {
        assertEquals("@mosseri@threads.net", normalizeMastodonSearchQuery("threads.net/@mosseri"))
        assertEquals("@mosseri@threads.net", normalizeMastodonSearchQuery("https://www.threads.net/@mosseri"))
        assertEquals("@zuck@threads.net", normalizeMastodonSearchQuery("https://threads.net/@zuck/"))
        assertEquals("@creator@threads.net", normalizeMastodonSearchQuery("  threads.net/@creator  "))
    }

    @Test
    fun normalizesFediverseProfileUrls() {
        assertEquals("@dansup@pixelfed.social", normalizeMastodonSearchQuery("pixelfed.social/@dansup"))
        assertEquals("@art@pixelfed.de", normalizeMastodonSearchQuery("https://pixelfed.de/@art"))
        assertEquals("@user@mastodon.social", normalizeMastodonSearchQuery("https://mastodon.social/@user/"))
    }

    @Test
    fun preservesDirectQueriesAndKeywords() {
        assertEquals("@alice@example.com", normalizeMastodonSearchQuery("@alice@example.com"))
        assertEquals("#photography", normalizeMastodonSearchQuery("#photography"))
        assertEquals("nature photography", normalizeMastodonSearchQuery("nature photography"))
    }
}
