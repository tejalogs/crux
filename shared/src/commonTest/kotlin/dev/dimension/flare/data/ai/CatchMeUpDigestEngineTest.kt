package dev.dimension.flare.data.ai

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CatchMeUpDigestEngineTest {

    private fun createPost(
        id: String,
        text: String,
        authorHandle: String = "teja@flare.local",
    ): UiTimelineV2.TimelinePostItem {
        val root = UiTimelineV2.Post(
            platformId = "prism",
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = UiProfile(
                key = MicroBlogKey(authorHandle, "test.local"),
                handle = UiHandle("teja", "test.local"),
                avatar = "https://example.com/avatar.png".toUiImage(),
                nameInternal = "Teja".toUiPlainText(),
                platformId = "prism",
                clickEvent = ClickEvent.Noop,
                banner = null,
                description = null,
                matrices = UiProfile.Matrices(0L, 0L, 0L),
                mark = persistentListOf(),
                bottomContent = null,
            ),
            content = UiTranslatableText(text.toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = MicroBlogKey(id, "test.local"),
            card = null,
            createdAt = Instant.fromEpochMilliseconds(1729850000000L).toUi(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Guest,
        )
        return UiTimelineV2.TimelinePostItem(post = root)
    }

    @Test
    fun returnsNullWhenLessThanThreePostsProvided() {
        val posts = listOf(
            createPost("1", "Short post"),
            createPost("2", "Another short post"),
        )
        assertNull(CatchMeUpDigestEngine.generateDigest(posts))
    }

    @Test
    fun generatesStructuredDigestWithHashtagsAndTopicHighlights() {
        val posts = listOf(
            createPost("1", "Exciting news! #KotlinMultiplatform 2.1 is bringing massive performance gains to mobile apps."),
            createPost("2", "We just enabled on-device intelligence with #Gemma and LiteRT-LM for sovereign social feeds."),
            createPost("3", "Testing out the new #KotlinMultiplatform Compose UI layout on Android and Desktop devices!"),
            createPost("4", "Privacy first: all embeddings and inference remain 100% offline with zero cloud data egress."),
        )

        val digest = CatchMeUpDigestEngine.generateDigest(posts)
        assertNotNull(digest)
        assertEquals("Catch Me Up", digest.title)
        assertTrue(digest.subtitle.contains("Gemma 4 Edge"), "Subtitle should mention Gemma 4 Edge")
        assertTrue(digest.subtitle.contains("Zero Egress"), "Subtitle should guarantee zero egress")
        assertEquals(4, digest.postCount)

        assertTrue(digest.topTopics.isNotEmpty(), "Top topics should be populated")
        assertTrue(digest.topTopics.any { it.contains("kotlinmultiplatform", ignoreCase = true) }, "Should extract #kotlinmultiplatform")
        assertTrue(digest.highlights.isNotEmpty(), "Highlights should not be empty")

        for (highlight in digest.highlights) {
            assertTrue(highlight.summary.isNotBlank(), "Summary should not be blank")
            assertNotNull(highlight.authorHandle)
        }
    }
}
