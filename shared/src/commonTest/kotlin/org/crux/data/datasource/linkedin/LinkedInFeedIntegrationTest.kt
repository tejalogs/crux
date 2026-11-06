package org.crux.data.datasource.linkedin

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.platform.LINKEDIN_HOST
import org.crux.data.platform.LinkedInCredential
import org.crux.data.platform.LinkedInPlatformSpec
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
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
        val update =
            LinkedInUpdate(
                id = "urn:li:activity:7123456789",
                actor =
                    LinkedInActor(
                        urn = "urn:li:member:998877",
                        name = LinkedInText(text = "Satya Nadella"),
                        description = LinkedInText(text = "Chairman and CEO at Microsoft"),
                        navigationUrl = "https://www.linkedin.com/in/satyanadella",
                        image =
                            LinkedInImageAttributes(
                                attributes =
                                    listOf(
                                        LinkedInImageDetail(
                                            sourceWebImageUrl = "https://media.licdn.com/dms/image/satya.jpg",
                                        ),
                                    ),
                            ),
                    ),
                commentary =
                    LinkedInCommentary(
                        text = LinkedInText(text = "Excited about our new AI advancements and partner ecosystem!"),
                    ),
                content =
                    LinkedInContent(
                        imageComponent =
                            LinkedInImageComponent(
                                url = "https://media.licdn.com/dms/image/keynote.jpg",
                                width = 1200,
                                height = 630,
                                altText = "Keynote photo",
                            ),
                    ),
                socialDetail =
                    LinkedInSocialDetail(
                        totalSocialActivityCounts =
                            LinkedInSocialCounts(
                                numLikes = 15200L,
                                numComments = 840L,
                                numShares = 412L,
                            ),
                        reactions =
                            LinkedInReactions(
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
        assertEquals("satyanadella", post.user?.handle?.normalizedRaw)
        assertEquals("Chairman and CEO at Microsoft", post.user?.description?.innerText)
        assertEquals("https://media.licdn.com/dms/image/satya.jpg", post.user?.avatar?.url)
        assertEquals(MicroBlogKey(id = "urn:li:activity:7123456789", host = "linkedin.com"), post.statusKey)
        assertEquals("Excited about our new AI advancements and partner ecosystem!", post.content.original.innerText)

        assertEquals(1, post.images.size)
        val image = post.images[0] as UiMedia.Image
        assertEquals("https://media.licdn.com/dms/image/keynote.jpg", image.url)

        assertEquals(3, post.actions.size)
        val commentAction = post.actions[0] as org.crux.data.datasource.microblog.ActionMenu.Item
        val likeAction = post.actions[1] as org.crux.data.datasource.microblog.ActionMenu.Item
        val moreAction = post.actions[2] as org.crux.data.datasource.microblog.ActionMenu.Group

        assertEquals(org.crux.data.datasource.microblog.PostActionFamily.Reply, commentAction.actionFamily)
        assertEquals(org.crux.data.datasource.microblog.PostActionFamily.Like, likeAction.actionFamily)
        assertEquals(UiIcon.Unlike, likeAction.icon)
        assertEquals(org.crux.data.datasource.microblog.ActionMenu.Item.Color.Red, likeAction.color)
        assertTrue(likeAction.clickEvent is org.crux.ui.model.ClickEvent.Deeplink)

        assertEquals(UiIcon.More, moreAction.displayItem.icon)
        assertEquals(2, moreAction.actions.size)
        val smartReplyAction = moreAction.actions[0] as org.crux.data.datasource.microblog.ActionMenu.Item
        assertEquals(org.crux.data.datasource.microblog.PostActionFamily.Reply, smartReplyAction.actionFamily)
        val shareAction = moreAction.actions[1] as org.crux.data.datasource.microblog.ActionMenu.Item
        assertEquals(org.crux.data.datasource.microblog.PostActionFamily.Share, shareAction.actionFamily)

        // Verify tapping the post in the feed navigates to the native Status Detail screen
        assertTrue(post.clickEvent is org.crux.ui.model.ClickEvent.Deeplink)
        val expectedUri =
            DeeplinkRoute.Status
                .Detail(
                    statusKey = post.statusKey,
                    accountType =
                        org.crux.model.AccountType
                            .Specific(accountKey),
                ).toUri()
        assertEquals(expectedUri, post.clickEvent.url)
    }

    @Test
    fun testsLinkedInLikeEventNextActionMenuTogglesState() {
        val event =
            org.crux.data.datasource.microblog.PostEvent.LinkedIn.Like(
                postKey = MicroBlogKey(id = "urn:li:activity:123", host = "linkedin.com"),
                liked = true,
                count = 15200L,
                accountKey = accountKey,
            )
        val next = event.nextActionMenu()
        assertEquals(UiIcon.Like, next.icon)
        assertNull(next.color)
        assertEquals(15199L, next.count?.value)

        val unlikedEvent =
            org.crux.data.datasource.microblog.PostEvent.LinkedIn.Like(
                postKey = MicroBlogKey(id = "urn:li:activity:123", host = "linkedin.com"),
                liked = false,
                count = 0L,
                accountKey = accountKey,
            )
        val nextLiked = unlikedEvent.nextActionMenu()
        assertEquals(UiIcon.Unlike, nextLiked.icon)
        assertEquals(org.crux.data.datasource.microblog.ActionMenu.Item.Color.Red, nextLiked.color)
        assertEquals(1L, nextLiked.count?.value)
    }

    @Test
    fun mapsArticleCardCorrectly() {
        val update =
            LinkedInUpdate(
                id = "urn:li:activity:7199999999",
                actor =
                    LinkedInActor(
                        name = LinkedInText(text = "Tech Lead"),
                    ),
                content =
                    LinkedInContent(
                        articleComponent =
                            LinkedInArticleComponent(
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
        val original =
            LinkedInUpdate(
                id = "urn:li:activity:111111",
                actor =
                    LinkedInActor(
                        name = LinkedInText(text = "Linus Torvalds"),
                        description = LinkedInText(text = "Linux Creator"),
                    ),
                commentary =
                    LinkedInCommentary(
                        text = LinkedInText(text = "Linux kernel 6.14 released!"),
                    ),
            )

        val repostUpdate =
            LinkedInUpdate(
                id = "urn:li:activity:222222",
                actor =
                    LinkedInActor(
                        name = LinkedInText(text = "Open Source Advocate"),
                    ),
                commentary =
                    LinkedInCommentary(
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
        val sponsored =
            LinkedInUpdate(
                id = "ad_li_999",
                actor = LinkedInActor(name = LinkedInText(text = "Sponsored Brand")),
                isSponsored = true,
            )
        assertNull(LinkedInTimelineMapper.render(sponsored, accountKey))
    }

    @Test
    fun platformSpecHasCorrectMetadataAndId() {
        assertEquals("linkedin", LinkedInPlatformSpec.platformId)
        assertEquals("LinkedIn", LinkedInPlatformSpec.metadata.displayName)
        assertEquals(UiIcon.LinkedIn, LinkedInPlatformSpec.metadata.icon)
        assertEquals(1, LinkedInPlatformSpec.timelineSpecs.size)

        val accountKey = MicroBlogKey("user123", "linkedin")
        // Links open in the live web screen, so the spec claims none.
        assertTrue(LinkedInPlatformSpec.deepLinks(accountKey).isEmpty())
    }

    @Test
    fun parsesNormalizedVoyagerPayloadWithIncludedArray() {
        val normalizedJson =
            """
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
        val envelopeJson =
            """
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
    fun homeLoaderAddsNothingToMergedFeeds() =
        runTest {
            val loader =
                LinkedInHomeTimelineRemoteLoader(
                    accountKey = accountKey,
                    credentialFlow = flowOf(LinkedInCredential(cookies = "li_at=token123; JSESSIONID=\"ajax:456\"", userId = "u1")),
                )

            val result = loader.load(pageSize = 20, request = PagingRequest.Refresh)
            assertEquals(0, result.data.size)
            assertEquals(null, result.nextKey)
        }

    @Test
    fun parsesDashGraphQLUpdatesWithEntityUrnAndTitleSubtitle() {
        val dashJson =
            """
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
        assertEquals(
            "Sundar Pichai",
            rendered.post.user
                ?.name
                ?.innerText,
        )
        assertEquals(
            "CEO at Alphabet and Google",
            rendered.post.user
                ?.description
                ?.innerText,
        )
        assertEquals("Excited to share our latest breakthroughs in on-device AI models.", rendered.post.content.original.innerText)
    }

    @Test
    fun mapsDocumentCarouselAndUpdateWithPollCorrectly() {
        val updateWithDocAndPoll =
            LinkedInUpdate(
                id = "urn:li:activity:doc_and_poll",
                actor =
                    LinkedInActor(
                        name = LinkedInText("Tech Analyst"),
                        title = LinkedInText("Tech Analyst"),
                    ),
                commentary =
                    LinkedInCommentary(
                        text = LinkedInText("Check out our new comprehensive benchmark guide and vote in our poll!"),
                    ),
                content =
                    LinkedInContent(
                        documentComponent =
                            LinkedInDocumentComponent(
                                title = "State of On-Device AI 2026",
                                totalPageCount = 18,
                                thumbnail = "https://media.licdn.com/doc/thumb.jpg",
                                navigationUrl = "https://www.linkedin.com/feed/update/urn:li:activity:doc_and_poll/",
                            ),
                        pollComponent =
                            LinkedInPollComponent(
                                question = LinkedInText("Which on-device model architecture do you prefer?"),
                                totalVotes = 3420L,
                                pollStatus = "OPEN",
                                options =
                                    listOf(
                                        LinkedInPollOption(
                                            optionText = LinkedInText("Gemma 4 Edge"),
                                            votePercentage = 68,
                                            voteCount = 2325L,
                                        ),
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
    fun parsesVoyagerFeedUpdatesV2WithVectorImagesAndAvatars() {
        val sampleJson =
            """
            {
              "elements": [
                {
                  "entityUrn": "urn:li:fs_updateV2:(urn:li:activity:7520575003000688640,MAIN_FEED,DEBUG_REASON,DEFAULT,false)",
                  "actor": {
                    "urn": "urn:li:company:13070195",
                    "name": { "text": "Product Marketing Alliance" },
                    "description": { "text": "Leading community for PMMs" },
                    "image": {
                      "attributes": [
                        {
                          "miniCompany": {
                            "name": "Product Marketing Alliance",
                            "logo": {
                              "com.linkedin.common.VectorImage": {
                                "rootUrl": "https://media.licdn.com/dms/image/v2/D4D0BAQHjZkb4ItjcXQ/company-logo_",
                                "artifacts": [
                                  {
                                    "width": 100,
                                    "fileIdentifyingUrlPathSegment": "100_100/logo.png",
                                    "height": 100
                                  },
                                  {
                                    "width": 200,
                                    "fileIdentifyingUrlPathSegment": "200_200/logo.png",
                                    "height": 200
                                  }
                                ]
                              }
                            }
                          }
                        }
                      ]
                    }
                  },
                  "commentary": {
                    "text": { "text": "8 hours a week saved with AI tools." }
                  },
                  "content": {
                    "com.linkedin.voyager.feed.render.ImageComponent": {
                      "images": [
                        {
                          "attributes": [
                            {
                              "vectorImage": {
                                "rootUrl": "https://media.licdn.com/dms/image/v2/D4E22AQHPJyg6KjCamA/feedshare-shrink_480/",
                                "artifacts": [
                                  {
                                    "width": 480,
                                    "fileIdentifyingUrlPathSegment": "0/post.jpg",
                                    "height": 480
                                  }
                                ]
                              }
                            }
                          ]
                        }
                      ]
                    }
                  }
                }
              ],
              "metadata": {
                "paginationToken": "token_next",
                "count": 1
              }
            }
            """.trimIndent()

        val parsed = LinkedInFeedParser.parse(sampleJson)
        assertEquals(1, parsed.elements.size)
        val update = parsed.elements[0]
        assertEquals("Product Marketing Alliance", update.actor?.name?.text)
        assertEquals("8 hours a week saved with AI tools.", update.commentary?.text?.text)
        val content = update.content
        assertNotNull(content)
        val imgComp = content.imageComponent
        assertNotNull(imgComp)
        assertNotNull(imgComp.url)
        assertTrue(imgComp.url.contains("feedshare-shrink_480/0/post.jpg"))

        val rendered = LinkedInTimelineMapper.render(update, accountKey)
        assertNotNull(rendered)
        assertEquals(
            "Product Marketing Alliance",
            rendered.post.user
                ?.name
                ?.raw,
        )
        assertEquals(
            "https://media.licdn.com/dms/image/v2/D4D0BAQHjZkb4ItjcXQ/company-logo_200_200/logo.png",
            rendered.post.user
                ?.avatar
                ?.url,
        )
        assertEquals(1, rendered.post.images.size)
        val img = rendered.post.images[0] as UiMedia.Image
        assertTrue(img.url.contains("feedshare-shrink_480/0/post.jpg"))
    }

    @Test
    fun testPublishThrowsUnsupportedOperationExceptionEvenWithCookies() =
        runTest {
            val cookies = "li_at=token123; JSESSIONID=\"ajax:1234567890\""
            val dataSource =
                LinkedInDataSource(
                    accountKey = accountKey,
                    credentialFlow = flowOf(LinkedInCredential(cookies = cookies, userId = "u1")),
                )
            val composeData =
                org.crux.data.datasource.microblog.ComposeData(
                    content = "Trying to post to LinkedIn with saved cookies",
                )
            kotlin.test.assertFailsWith<UnsupportedOperationException> {
                dataSource.publish(composeData) {}
            }
        }

    @Test
    fun parsesThreadedCommentsAndRendersTimelineItems() {
        val mockJson =
            """
            {
              "data": {
                "voyagerSocialDashNormCommentsByThreadUrn": {
                  "elements": [
                    {
                      "urn": "urn:li:comment:(activity:7123456789,1001)",
                      "commenter": {
                        "name": { "text": "Alice Johnson" },
                        "headline": { "text": "Senior Android Engineer" },
                        "navigationUrl": "https://www.linkedin.com/in/alicejohnson"
                      },
                      "commentary": {
                        "text": { "text": "Incredible insights, completely agree!" }
                      },
                      "socialDetail": {
                        "totalSocialActivityCounts": {
                          "numLikes": 14,
                          "numComments": 1
                        }
                      },
                      "comments": [
                        {
                          "urn": "urn:li:comment:(activity:7123456789,1002)",
                          "commenter": {
                            "name": { "text": "Bob Smith" },
                            "headline": { "text": "Staff Architect" },
                            "navigationUrl": "https://www.linkedin.com/in/bobsmith"
                          },
                          "commentary": {
                            "text": { "text": "Spot on Alice!" }
                          }
                        }
                      ]
                    }
                  ],
                  "paging": {
                    "total": 1,
                    "paginationToken": "cursor_page_2"
                  }
                }
              }
            }
            """.trimIndent()

        val response = LinkedInFeedParser.parseComments(mockJson)
        assertNotNull(response)
        assertEquals(1, response.comments.size)

        val topComment = response.comments[0]
        assertEquals("urn:li:comment:(activity:7123456789,1001)", topComment.urn)
        assertEquals("Alice Johnson", topComment.commenter?.name?.text)
        assertEquals("Incredible insights, completely agree!", topComment.commentary?.text?.text)
        assertEquals(1, topComment.comments.size)

        val childComment = topComment.comments[0]
        assertEquals("Bob Smith", childComment.commenter?.name?.text)
        assertEquals("Spot on Alice!", childComment.commentary?.text?.text)

        val parentPostKey = MicroBlogKey(id = "urn:li:activity:7123456789", host = "linkedin.com")
        val renderedList =
            LinkedInTimelineMapper.renderComments(
                comments = response.comments,
                parentPostKey = parentPostKey,
                parentAuthorHandle = "satyanadella",
                accountKey = accountKey,
            )

        assertEquals(2, renderedList.size)
        val renderedTop = renderedList[0] as UiTimelineV2.TimelinePostItem
        val renderedChild = renderedList[1] as UiTimelineV2.TimelinePostItem

        assertEquals(
            parentPostKey,
            renderedTop.post.references
                .firstOrNull()
                ?.statusKey,
        )
        assertEquals(
            "Alice Johnson",
            renderedTop.post.user
                ?.name
                ?.innerText,
        )
        assertEquals("Incredible insights, completely agree!", renderedTop.post.content.original.innerText)

        assertEquals(
            renderedTop.statusKey,
            renderedChild.post.references
                .firstOrNull()
                ?.statusKey,
        )
        assertEquals(
            "Bob Smith",
            renderedChild.post.user
                ?.name
                ?.innerText,
        )
        assertEquals("Spot on Alice!", renderedChild.post.content.original.innerText)
    }

    @Test
    fun normalizesUrnsCorrectlyForThreadsAndParentComments() {
        val service = LinkedInFeedService()

        // 1. Raw Voyager update envelope
        val envelopeUrn = "urn:li:fs_updateV2:(urn:li:activity:7520786479925710848,MAIN_FEED,DEBUG_REASON,DEFAULT,false)"
        assertEquals("urn:li:activity:7520786479925710848", service.normalizeThreadUrn(envelopeUrn))
        assertNull(service.normalizeParentCommentUrn(envelopeUrn))

        // 2. Canonical activity URN
        val activityUrn = "urn:li:activity:7520786479925710848"
        assertEquals("urn:li:activity:7520786479925710848", service.normalizeThreadUrn(activityUrn))
        assertNull(service.normalizeParentCommentUrn(activityUrn))

        // 3. Thread with child comment reply
        val replyUrn =
            "urn:li:fs_updateV2:(urn:li:activity:7520786479925710848,MAIN_FEED,DEBUG_REASON,DEFAULT,false)" +
                "#urn:li:comment:(activity:7520786479925710848,7520789012345678901)"
        assertEquals("urn:li:activity:7520786479925710848", service.normalizeThreadUrn(replyUrn))
        assertEquals("urn:li:comment:(activity:7520786479925710848,7520789012345678901)", service.normalizeParentCommentUrn(replyUrn))

        // 4. Standalone comment URN
        val commentUrn = "urn:li:comment:(activity:7520786479925710848,7520789012345678901)"
        assertEquals("urn:li:activity:7520786479925710848", service.normalizeThreadUrn(commentUrn))
        assertEquals("urn:li:comment:(activity:7520786479925710848,7520789012345678901)", service.normalizeParentCommentUrn(commentUrn))

        // 5. Plain number or UGC post
        assertEquals("urn:li:activity:123456789", service.normalizeThreadUrn("123456789"))
        assertEquals("urn:li:ugcPost:987654321", service.normalizeThreadUrn("urn:li:ugcPost:987654321"))
    }

    @Test
    fun validatesLinkedInHostAndBridgePayloadCorrectly() {
        assertTrue(LinkedInFeedParser.isValidLinkedInHost("linkedin.com"))
        assertTrue(LinkedInFeedParser.isValidLinkedInHost("www.linkedin.com"))
        assertTrue(LinkedInFeedParser.isValidLinkedInHost("touch.linkedin.com"))
        assertTrue(LinkedInFeedParser.isValidLinkedInHost("media.licdn.com"))
        assertFalse(LinkedInFeedParser.isValidLinkedInHost("attacker.com"))
        assertFalse(LinkedInFeedParser.isValidLinkedInHost("linkedin.com.attacker.com"))
        assertFalse(LinkedInFeedParser.isValidLinkedInHost(null))
        assertFalse(LinkedInFeedParser.isValidLinkedInHost(""))

        assertFalse(LinkedInFeedParser.isValidFeedPayload(""))
        assertFalse(LinkedInFeedParser.isValidFeedPayload("not json"))
        assertFalse(LinkedInFeedParser.isValidFeedPayload("""{"key": "value"}"""))

        val hugePayload = "{" + "\"a\":1,".repeat(200_000) + "\"elements\":[]}"
        assertFalse(LinkedInFeedParser.isValidFeedPayload(hugePayload, maxSizeBytes = 100_000))

        val validJson =
            """
            {
                "data": {
                    "elements": [
                        {
                            "actor": {"name": {"text": "Jane Doe"}},
                            "commentary": {"text": {"text": "Exciting announcement!"}},
                            "urn": "urn:li:activity:999999"
                        }
                    ]
                }
            }
            """.trimIndent()
        assertTrue(LinkedInFeedParser.isValidFeedPayload(validJson))
    }
}
