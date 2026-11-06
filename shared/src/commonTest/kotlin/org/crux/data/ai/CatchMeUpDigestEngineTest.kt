package org.crux.data.ai

import kotlinx.collections.immutable.persistentListOf
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiHandle
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.UiTranslatableText
import org.crux.ui.model.toUiImage
import org.crux.ui.render.toUi
import org.crux.ui.render.toUiPlainText
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
        val root =
            UiTimelineV2.Post(
                platformId = "prism",
                images = persistentListOf(),
                sensitive = false,
                contentWarning = null,
                user =
                    UiProfile(
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
        val posts =
            listOf(
                createPost("1", "Short post"),
                createPost("2", "Another short post"),
            )
        assertNull(CatchMeUpDigestEngine.generateDigest(posts))
    }

    @Test
    fun generatesStructuredDigestWithHashtagsAndTopicHighlights() {
        val posts =
            listOf(
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

    @Test
    fun synthesizesMultiSentenceNarrativeWithoutGenericVerbTopics() {
        val posts =
            listOf(
                createPost(
                    "1",
                    "JerryRigEverything's wife had a horse riding accident.\nShe now required a wheel chair to move about\nAll high quality outdoor wheelchairs were expensive\nSo he built one and started a company selling them at low cost",
                ),
                createPost(
                    "2",
                    "Open source hardware and wheelchair accessibility engineering are transforming mobility for thousands of families.",
                ),
                createPost(
                    "3",
                    "Building accessible wheelchair technology at low cost requires rethinking manufacturing from first principles.",
                ),
            )

        val digest = CatchMeUpDigestEngine.generateDigest(posts)
        assertNotNull(digest)
        // Generic verbs like 'started', 'required', 'selling' must not appear as topics
        val lowerTopics = digest.topTopics.map { it.lowercase() }
        assertTrue("started" !in lowerTopics, "Generic verb 'started' should be filtered from topics")
        assertTrue("required" !in lowerTopics, "Generic verb 'required' should be filtered from topics")
        assertTrue(
            digest.highlights
                .first()
                .summary
                .contains("built one"),
            "Executive summary should capture the payoff sentence",
        )
    }
}
