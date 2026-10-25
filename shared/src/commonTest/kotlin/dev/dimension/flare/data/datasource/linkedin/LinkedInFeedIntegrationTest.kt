package dev.dimension.flare.data.datasource.linkedin

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.platform.LinkedInCredential
import dev.dimension.flare.data.platform.LinkedInPlatformSpec
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkedInFeedIntegrationTest {

    private val accountKey = MicroBlogKey(id = "li_user_123", host = "linkedin.com")

    @Test
    fun extractsCsrfAndUserIdFromCookiesCorrectly() {
        val cookies = "bcookie=\"v=2\"; JSESSIONID=\"ajax:9876543210\"; li_at=AQEDATejaSessionToken12345; lang=v=2"
        assertEquals("ajax:9876543210", LinkedInFeedService.extractCsrfToken(cookies))
        assertNotNull(LinkedInFeedService.extractUserId(cookies))
        assertTrue(LinkedInFeedService.hasActiveSession(cookies))

        val incompleteCookies = "bcookie=\"v=2\"; lang=v=2"
        assertFalse(LinkedInFeedService.hasActiveSession(incompleteCookies))
    }

    @Test
    fun mapsSinglePostWithCommentaryAndAuthorCorrectly() {
        val update = LinkedInUpdate(
            id = "urn:li:activity:7123456789",
            actor = LinkedInActor(
                urn = "urn:li:member:998877",
                name = LinkedInText(text = "Satya Nadella"),
                description = LinkedInText(text = "Chairman and CEO at Microsoft"),
                navigationUrl = "https://www.linkedin.com/in/satyanadella",
                image = LinkedInImageAttributes(
                    attributes = listOf(
                        LinkedInImageDetail(
                            sourceWebImageUrl = "https://media.licdn.com/dms/image/satya.jpg",
                        )
                    )
                ),
            ),
            commentary = LinkedInCommentary(
                text = LinkedInText(text = "Excited about our new AI advancements and partner ecosystem!"),
            ),
            content = LinkedInContent(
                imageComponent = LinkedInImageComponent(
                    url = "https://media.licdn.com/dms/image/keynote.jpg",
                    width = 1200,
                    height = 630,
                    altText = "Keynote photo",
                ),
            ),
            socialDetail = LinkedInSocialDetail(
                totalSocialActivityCounts = LinkedInSocialCounts(
                    numLikes = 15200L,
                    numComments = 840L,
                    numShares = 412L,
                ),
                reactions = LinkedInReactions(
                    hasLiked = true,
                ),
            ),
        )

        val rendered = LinkedInTimelineMapper.render(update, accountKey)
        assertNotNull(rendered)

        val post = rendered.post
        assertEquals("linkedin", post.platformId)
        assertEquals(UiIcon.LinkedIn, post.platformIcon)
        assertEquals("Satya Nadella", post.user?.name?.innerText)
        assertEquals("Chairman and CEO at Microsoft", post.user?.handle?.normalizedRaw)
        assertEquals("https://media.licdn.com/dms/image/satya.jpg", post.user?.avatar?.url)
        assertEquals(MicroBlogKey(id = "urn:li:activity:7123456789", host = "linkedin.com"), post.statusKey)
        assertEquals("Excited about our new AI advancements and partner ecosystem!", post.content.original.innerText)

        assertEquals(1, post.images.size)
        val image = post.images[0] as UiMedia.Image
        assertEquals("https://media.licdn.com/dms/image/keynote.jpg", image.url)

        assertEquals(3, post.actions.size)
    }

    @Test
    fun mapsArticleCardCorrectly() {
        val update = LinkedInUpdate(
            id = "urn:li:activity:7199999999",
            actor = LinkedInActor(
                name = LinkedInText(text = "Tech Lead"),
            ),
            content = LinkedInContent(
                articleComponent = LinkedInArticleComponent(
                    title = "Building Scalable Kotlin Multiplatform Social Apps",
                    subtitle = "A deep dive into sovereign, privacy-preserving client architecture.",
                    navigationUrl = "https://blog.example.com/kmp-social-hub",
                    thumbnail = "https://blog.example.com/thumb.jpg",
                ),
            ),
        )

        val rendered = LinkedInTimelineMapper.render(update, accountKey)
        assertNotNull(rendered)
        val card = rendered.post.card
        assertNotNull(card)
        assertEquals("Building Scalable Kotlin Multiplatform Social Apps", card.title)
        assertEquals("A deep dive into sovereign, privacy-preserving client architecture.", card.description)
        assertEquals("https://blog.example.com/kmp-social-hub", card.url)
    }

    @Test
    fun mapsResharedUpdateCorrectly() {
        val original = LinkedInUpdate(
            id = "urn:li:activity:111111",
            actor = LinkedInActor(
                name = LinkedInText(text = "Linus Torvalds"),
                description = LinkedInText(text = "Linux Creator"),
            ),
            commentary = LinkedInCommentary(
                text = LinkedInText(text = "Linux kernel 6.14 released!"),
            ),
        )

        val repostUpdate = LinkedInUpdate(
            id = "urn:li:activity:222222",
            actor = LinkedInActor(
                name = LinkedInText(text = "Open Source Advocate"),
            ),
            commentary = LinkedInCommentary(
                text = LinkedInText(text = "Great news for developers."),
            ),
            resharedUpdate = original,
        )

        val rendered = LinkedInTimelineMapper.render(repostUpdate, accountKey)
        assertNotNull(rendered)
        assertEquals(1, rendered.presentation.quotes.size)
        val quote = rendered.presentation.quotes[0]
        assertEquals("Linus Torvalds", quote.user?.name?.innerText)
        assertEquals("Linux kernel 6.14 released!", quote.content.original.innerText)
    }

    @Test
    fun filtersOutSponsoredUpdates() {
        val sponsored = LinkedInUpdate(
            id = "ad_li_999",
            actor = LinkedInActor(name = LinkedInText(text = "Sponsored Brand")),
            isSponsored = true,
        )
        assertNull(LinkedInTimelineMapper.render(sponsored, accountKey))
    }

    @Test
    fun remoteLoaderPaginatesWithMockEngine() = runTest {
        val sampleJson = """
            {
              "elements": [
                {
                  "id": "li_update_1",
                  "actor": {
                    "urn": "urn:li:member:1",
                    "name": { "text": "Alice Engineer" }
                  },
                  "commentary": {
                    "text": { "text": "Hello LinkedIn from Prism!" }
                  }
                }
              ],
              "metadata": {
                "paginationToken": "next_token_456",
                "count": 1
              }
            }
        """.trimIndent()

        val mockEngine = MockEngine { _ ->
            respond(
                content = sampleJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val service = LinkedInFeedService(HttpClient(mockEngine))
        val credentialFlow = flowOf(
            LinkedInCredential(
                cookies = "li_at=token123; JSESSIONID=\"ajax:456\"",
                userId = "u_alice",
            )
        )

        val loader = LinkedInHomeTimelineRemoteLoader(
            service = service,
            accountKey = accountKey,
            credentialFlow = credentialFlow,
        )

        val result = loader.load(pageSize = 20, request = PagingRequest.Refresh)
        assertEquals(1, result.data.size)
        assertEquals("next_token_456", result.nextKey)
    }

    @Test
    fun platformSpecHasCorrectMetadataAndId() {
        assertEquals("linkedin", LinkedInPlatformSpec.platformId)
        assertEquals("LinkedIn", LinkedInPlatformSpec.metadata.displayName)
        assertEquals(UiIcon.LinkedIn, LinkedInPlatformSpec.metadata.icon)
        assertEquals(1, LinkedInPlatformSpec.timelineSpecs.size)
    }

    @Test
    fun parsesNormalizedVoyagerPayloadWithIncludedArray() {
        val normalizedJson = """
            {
              "data": {
                "*elements": ["urn:li:activity:999888"]
              },
              "included": [
                {
                  "${'$'}type": "com.linkedin.voyager.feed.render.UpdateV2",
                  "urn": "urn:li:activity:999888",
                  "actor": {
                    "name": { "text": "Sundar Pichai" },
                    "description": { "text": "CEO at Alphabet and Google" }
                  },
                  "commentary": {
                    "text": { "text": "Excited for Google I/O and Gemma developments!" }
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = LinkedInFeedParser.parse(normalizedJson)
        assertEquals(1, parsed.elements.size)
        val update = parsed.elements[0]
        assertEquals("urn:li:activity:999888", update.urn)
        assertEquals("Sundar Pichai", update.actor?.name?.text)
        assertEquals("Excited for Google I/O and Gemma developments!", update.commentary?.text?.text)
    }

    @Test
    fun parsesValueEnvelopePayloadWithPrimitiveStrings() {
        val envelopeJson = """
            {
              "elements": [
                {
                  "value": {
                    "com.linkedin.voyager.feed.render.UpdateV2": {
                      "id": "li_env_1",
                      "actor": {
                        "name": "Demis Hassabis",
                        "description": "CEO at Google DeepMind"
                      },
                      "commentary": "Announcing breakthrough reasoning models."
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = LinkedInFeedParser.parse(envelopeJson)
        assertEquals(1, parsed.elements.size)
        val update = parsed.elements[0]
        assertEquals("li_env_1", update.id)
        assertEquals("Demis Hassabis", update.actor?.name?.text)
        assertEquals("Announcing breakthrough reasoning models.", update.commentary?.text?.text)
    }

    @Test
    fun fallsBackToCachedUpdatesWhenNetworkReturnsEmpty() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val service = LinkedInFeedService(HttpClient(mockEngine))
        val credentialFlow = flowOf(
            LinkedInCredential(
                cookies = "li_at=token123; JSESSIONID=\"ajax:456\"",
                userId = "u_cached",
            )
        )

        val syncService = LinkedInSyncService()
        syncService.cacheFeedPayload("""
            {
              "elements": [
                {
                  "id": "li_cached_99",
                  "actor": {
                    "name": { "text": "Cached Author" }
                  },
                  "commentary": {
                    "text": { "text": "Cached update from WebView bridge" }
                  }
                }
              ]
            }
        """.trimIndent())

        val loader = LinkedInHomeTimelineRemoteLoader(
            service = service,
            accountKey = accountKey,
            credentialFlow = credentialFlow,
            syncService = syncService,
        )

        val result = loader.load(pageSize = 20, request = PagingRequest.Refresh)
        assertEquals(1, result.data.size)
        val post = (result.data[0] as? dev.dimension.flare.ui.model.UiTimelineV2.TimelinePostItem)?.post
        assertNotNull(post)
        assertEquals("Cached Author", post.user?.name?.innerText)
        assertEquals("Cached update from WebView bridge", post.content.original.innerText)
    }

    @Test
    fun parsesDashGraphQLUpdatesWithEntityUrnAndTitleSubtitle() {
        val dashJson = """
            {
              "data": {
                "feedDashUpdates": {
                  "elements": [
                    {
                      "entityUrn": "urn:li:fsd_update:(urn:li:activity:7999888777)",
                      "actor": {
                        "title": { "text": "Sundar Pichai" },
                        "subtitle": { "text": "CEO at Alphabet and Google" },
                        "navigationUrl": "https://www.linkedin.com/in/sundarpichai",
                        "image": {
                          "url": "https://media.licdn.com/dms/image/sundar.jpg"
                        }
                      },
                      "commentary": {
                        "text": { "text": "Excited to share our latest breakthroughs in on-device AI models." }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val parsed = LinkedInFeedParser.parse(dashJson)
        assertEquals(1, parsed.elements.size)
        val update = parsed.elements[0]
        assertEquals("urn:li:fsd_update:(urn:li:activity:7999888777)", update.id)
        assertEquals("Sundar Pichai", update.actor?.title?.text)
        assertEquals("CEO at Alphabet and Google", update.actor?.subtitle?.text)

        val rendered = LinkedInTimelineMapper.render(update, accountKey)
        assertNotNull(rendered)
        assertEquals("Sundar Pichai", rendered.post.user?.name?.innerText)
        assertEquals("CEO at Alphabet and Google", rendered.post.user?.description?.innerText)
        assertEquals("Excited to share our latest breakthroughs in on-device AI models.", rendered.post.content.original.innerText)
    }

    @Test
    fun mapsDocumentCarouselAndUpdateWithPollCorrectly() {
        val updateWithDocAndPoll = LinkedInUpdate(
            id = "urn:li:activity:doc_and_poll",
            actor = LinkedInActor(
                name = LinkedInText("Tech Analyst"),
                title = LinkedInText("Tech Analyst"),
            ),
            commentary = LinkedInCommentary(
                text = LinkedInText("Check out our new comprehensive benchmark guide and vote in our poll!"),
            ),
            content = LinkedInContent(
                documentComponent = LinkedInDocumentComponent(
                    title = "State of On-Device AI 2026",
                    totalPageCount = 18,
                    thumbnail = "https://media.licdn.com/doc/thumb.jpg",
                    navigationUrl = "https://www.linkedin.com/feed/update/urn:li:activity:doc_and_poll/",
                ),
                pollComponent = LinkedInPollComponent(
                    question = LinkedInText("Which on-device model architecture do you prefer?"),
                    totalVotes = 3420L,
                    pollStatus = "OPEN",
                    options = listOf(
                        LinkedInPollOption(optionText = LinkedInText("Gemma 4 Edge"), votePercentage = 68, voteCount = 2325L),
                        LinkedInPollOption(optionText = LinkedInText("Others"), votePercentage = 32, voteCount = 1095L),
                    ),
                ),
            ),
        )

        val rendered = LinkedInTimelineMapper.render(updateWithDocAndPoll, accountKey)
        assertNotNull(rendered)
        assertEquals(1, rendered.post.images.size)
        assertEquals("https://media.licdn.com/doc/thumb.jpg", (rendered.post.images[0] as? UiMedia.Image)?.url)
        val poll = rendered.post.poll
        assertNotNull(poll)
        assertEquals(2, poll.options.size)
        assertEquals("Gemma 4 Edge", poll.options[0].title)
        assertEquals(0.68f, poll.options[0].percentage)
    }

    @Test
    fun paginatesThroughMultipleCachedPagesSmoothlyWhenOffline() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val service = LinkedInFeedService(HttpClient(mockEngine))
        val credentialFlow = flowOf(
            LinkedInCredential(
                cookies = "li_at=token123; JSESSIONID=\"ajax:456\"",
                userId = "u_cached",
            )
        )

        val syncService = LinkedInSyncService()
        val updates = (1..15).map { index ->
            LinkedInUpdate(
                id = "li_offline_$index",
                actor = LinkedInActor(name = LinkedInText("Author $index")),
                commentary = LinkedInCommentary(text = LinkedInText("Post number $index content")),
            )
        }
        syncService.cacheUpdates(updates)

        val loader = LinkedInHomeTimelineRemoteLoader(
            service = service,
            accountKey = accountKey,
            credentialFlow = credentialFlow,
            syncService = syncService,
        )

        // Page 1: 10 items
        val page1 = loader.load(pageSize = 10, request = PagingRequest.Refresh)
        assertEquals(10, page1.data.size)
        assertEquals("10", page1.nextKey)

        // Page 2: remaining 5 items
        val page2 = loader.load(pageSize = 10, request = PagingRequest.Append("10"))
        assertEquals(5, page2.data.size)
        assertNull(page2.nextKey)
    }
}
