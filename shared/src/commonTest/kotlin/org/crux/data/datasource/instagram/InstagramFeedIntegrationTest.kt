package org.crux.data.datasource.instagram

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.crux.common.JSON
import org.crux.common.decodeFromStringWithNullableFallback
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.platform.InstagramCredential
import org.crux.data.platform.InstagramPlatformSpec
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.render.RenderContent
import org.crux.ui.render.RenderRun
import org.crux.ui.route.DeeplinkRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstagramFeedIntegrationTest {
    private val accountKey = MicroBlogKey(id = "123456", host = "instagram.com")

    @Test
    fun extractsCsrfAndUserIdFromCookiesCorrectly() {
        val cookies = "mid=Zf123; ig_did=ABC; ds_user_id=987654321; csrftoken=xYz789Token; sessionid=12345%3ASession"
        assertEquals("xYz789Token", InstagramFeedService.extractCsrfToken(cookies))
        assertEquals("987654321", InstagramFeedService.extractUserId(cookies))
        assertTrue(InstagramFeedService.hasActiveSession(cookies))

        val incompleteCookies = "mid=Zf123; csrftoken=abc"
        assertFalse(InstagramFeedService.hasActiveSession(incompleteCookies))
    }

    @Test
    fun mapsSinglePhotoPostCorrectly() {
        val mediaOrAd =
            InstagramMediaOrAd(
                id = "3350123456789_123456",
                code = "C5zAbCdEf",
                taken_at = 1713000000L,
                media_type = 1,
                caption = InstagramCaption(text = "Exploring the mountain trail! #hiking"),
                user =
                    InstagramUser(
                        pk = "123456",
                        username = "trailblazer",
                        full_name = "Alex Explorer",
                        profile_pic_url = "https://instagram.example.com/avatar.jpg",
                        is_verified = true,
                    ),
                image_versions2 =
                    InstagramImageVersions(
                        candidates =
                            listOf(
                                InstagramImageCandidate(url = "https://instagram.example.com/photo_high.jpg", width = 1080, height = 1080),
                                InstagramImageCandidate(url = "https://instagram.example.com/photo_low.jpg", width = 320, height = 320),
                            ),
                    ),
                like_count = 1420L,
                has_liked = true,
                comment_count = 35L,
            )

        val rendered = InstagramTimelineMapper.render(mediaOrAd, accountKey)
        assertNotNull(rendered)

        val post = rendered.post
        assertEquals("instagram", post.platformId)
        assertEquals(UiIcon.Instagram, post.platformIcon)
        assertEquals("Alex Explorer", post.user?.name?.innerText)
        assertEquals("trailblazer", post.user?.handle?.normalizedRaw)
        assertEquals("https://instagram.example.com/avatar.jpg", post.user?.avatar?.url)
        assertEquals(MicroBlogKey(id = "3350123456789_123456", host = "instagram.com"), post.statusKey)
        assertEquals("Exploring the mountain trail! #hiking", post.content.original.innerText)

        assertEquals(1, post.images.size)
        val image = post.images[0] as UiMedia.Image
        assertEquals("https://instagram.example.com/photo_high.jpg", image.url)
        assertEquals(1080f, image.width)
        assertEquals(1080f, image.height)

        assertEquals(3, post.actions.size)
        val commentAction = post.actions[0] as org.crux.data.datasource.microblog.ActionMenu.Item
        val likeAction = post.actions[1] as org.crux.data.datasource.microblog.ActionMenu.Item
        val moreAction = post.actions[2] as org.crux.data.datasource.microblog.ActionMenu.Group

        assertEquals(org.crux.data.datasource.microblog.PostActionFamily.Comment, commentAction.actionFamily)
        val clickEvent = commentAction.clickEvent
        assertTrue(clickEvent is org.crux.ui.model.ClickEvent.Deeplink)
        val route =
            org.crux.ui.route.DeeplinkRoute
                .parse(clickEvent.url)
        assertTrue(route is org.crux.ui.route.DeeplinkRoute.Status.Detail)
        assertEquals(org.crux.data.datasource.microblog.PostActionFamily.Like, likeAction.actionFamily)
        assertEquals(UiIcon.Unlike, likeAction.icon)
        assertEquals(org.crux.data.datasource.microblog.ActionMenu.Item.Color.Red, likeAction.color)
        assertTrue(likeAction.clickEvent is org.crux.ui.model.ClickEvent.Deeplink)

        assertEquals(UiIcon.More, moreAction.displayItem.icon)
        assertEquals(1, moreAction.actions.size)
        val shareAction = moreAction.actions[0] as org.crux.data.datasource.microblog.ActionMenu.Item
        assertEquals(org.crux.data.datasource.microblog.PostActionFamily.Share, shareAction.actionFamily)
    }

    @Test
    fun testsInstagramLikeEventNextActionMenuTogglesState() {
        val event =
            org.crux.data.datasource.microblog.PostEvent.Instagram.Like(
                postKey = MicroBlogKey(id = "123", host = "instagram.com"),
                liked = true,
                count = 10,
                accountKey = accountKey,
            )
        val next = event.nextActionMenu()
        assertEquals(UiIcon.Like, next.icon)
        assertNull(next.color)
        assertEquals(9L, next.count?.value)

        val unlikedEvent =
            org.crux.data.datasource.microblog.PostEvent.Instagram.Like(
                postKey = MicroBlogKey(id = "123", host = "instagram.com"),
                liked = false,
                count = 0,
                accountKey = accountKey,
            )
        val nextLiked = unlikedEvent.nextActionMenu()
        assertEquals(UiIcon.Unlike, nextLiked.icon)
        assertEquals(org.crux.data.datasource.microblog.ActionMenu.Item.Color.Red, nextLiked.color)
        assertEquals(1L, nextLiked.count?.value)
    }

    @Test
    fun mapsCarouselWithPhotosAndVideos() {
        val mediaOrAd =
            InstagramMediaOrAd(
                id = "3350999999999_123456",
                code = "C5zMulti",
                taken_at = 1713005000L,
                media_type = 8,
                caption = InstagramCaption(text = "Weekend trip dump"),
                user =
                    InstagramUser(
                        pk = "123456",
                        username = "trailblazer",
                        full_name = "Alex Explorer",
                        profile_pic_url = "https://instagram.example.com/avatar.jpg",
                    ),
                carousel_media =
                    listOf(
                        InstagramCarouselItem(
                            id = "carousel_item_1",
                            media_type = 1,
                            image_versions2 =
                                InstagramImageVersions(
                                    candidates =
                                        listOf(
                                            InstagramImageCandidate(
                                                url = "https://instagram.example.com/carousel_1.jpg",
                                                width = 1080,
                                                height = 1350,
                                            ),
                                        ),
                                ),
                        ),
                        InstagramCarouselItem(
                            id = "carousel_item_2",
                            media_type = 2,
                            video_versions =
                                listOf(
                                    InstagramVideoVersion(url = "https://instagram.example.com/video_2.mp4", width = 720, height = 1280),
                                ),
                            image_versions2 =
                                InstagramImageVersions(
                                    candidates =
                                        listOf(
                                            InstagramImageCandidate(
                                                url = "https://instagram.example.com/video_2_thumb.jpg",
                                                width = 720,
                                                height = 1280,
                                            ),
                                        ),
                                ),
                        ),
                    ),
                like_count = 500L,
                has_liked = false,
                comment_count = 12L,
            )

        val rendered = InstagramTimelineMapper.render(mediaOrAd, accountKey)
        assertNotNull(rendered)

        assertEquals(2, rendered.post.images.size)
        assertTrue(rendered.post.images[0] is UiMedia.Image)
        assertTrue(rendered.post.images[1] is UiMedia.Video)
    }

    @Test
    fun filtersOutAdsAndSponsoredContent() {
        val sponsoredMedia =
            InstagramMediaOrAd(
                id = "ad_123",
                user = InstagramUser(username = "brand"),
                is_paid_partnership = true,
            )
        assertNull(InstagramTimelineMapper.render(sponsoredMedia, accountKey))

        val adActionMedia =
            InstagramMediaOrAd(
                id = "ad_456",
                user = InstagramUser(username = "brand"),
                ad_action = "Install Now",
            )
        assertNull(InstagramTimelineMapper.render(adActionMedia, accountKey))
    }

    @Test
    fun homeLoaderAddsNothingToMergedFeeds() =
        runTest {
            val loader =
                InstagramHomeTimelineRemoteLoader(
                    accountKey = accountKey,
                    credentialFlow = flowOf(InstagramCredential(cookies = "sessionid=123; ds_user_id=u1; csrftoken=token", userId = "u1")),
                )

            val result = loader.load(pageSize = 20, request = PagingRequest.Refresh)
            assertEquals(0, result.data.size)
            assertEquals(null, result.nextKey)
        }

    @Test
    fun platformSpecHasCorrectMetadataAndId() {
        assertEquals("instagram", InstagramPlatformSpec.platformId)
        assertEquals("Instagram", InstagramPlatformSpec.metadata.displayName)
        assertEquals(UiIcon.Instagram, InstagramPlatformSpec.metadata.icon)
        assertEquals(1, InstagramPlatformSpec.timelineSpecs.size)
    }

    @Test
    fun convertsShortcodeAndCompositeIdsToNumericMediaId() {
        assertEquals("3350123456789", InstagramFeedService.resolveNumericMediaId("3350123456789_123456"))
        assertEquals("3350123456789", InstagramFeedService.resolveNumericMediaId("3350123456789"))

        // Shortcode "B" -> index 1 -> 1L
        assertEquals(1L, InstagramFeedService.shortcodeToMediaId("B"))
        // Shortcode "BA" -> 1 * 64 + 0 = 64L
        assertEquals(64L, InstagramFeedService.shortcodeToMediaId("BA"))
        assertEquals("64", InstagramFeedService.resolveNumericMediaId("BA"))
    }

    @Test
    fun unpacksPastPostsDemarcatorWhenFeedRecsHeadLoadReached() =
        runTest {
            val demarcatorJson =
                """
                {
                  "feed_items": [
                    {
                      "end_of_feed_demarcator": {
                        "id": 999,
                        "group_set": {
                          "id": 100,
                          "active_group_id": "past_posts",
                          "groups": [
                            {
                              "id": "past_posts",
                              "title": "Older Posts",
                              "next_max_id": "chrono_cursor_42",
                              "feed_items": [
                                {
                                  "media_or_ad": {
                                    "id": "chrono_item_1",
                                    "code": "CHRONO1",
                                    "taken_at": 1712000000,
                                    "media_type": 1,
                                    "caption": { "text": "Pure chronological post" },
                                    "user": {
                                      "pk": "u99",
                                      "username": "chronouser",
                                      "profile_pic_url": "https://example.com/u99.jpg"
                                    },
                                    "image_versions2": {
                                      "candidates": [
                                        { "url": "https://example.com/chrono.jpg", "width": 1080, "height": 1080 }
                                      ]
                                    }
                                  }
                                }
                              ]
                            }
                          ]
                        }
                      }
                    }
                  ],
                  "next_max_id": "feed_recs_head_load",
                  "more_available": true
                }
                """.trimIndent()

            val items = InstagramFeedService.extractMediaItemsFromPayload(demarcatorJson)
            assertEquals(1, items.size)
            assertEquals("chrono_item_1", items[0].id)
            assertEquals("Pure chronological post", items[0].caption?.text)
        }

    @Test
    fun loadsSinglePostViaContextLoaderFromCache() =
        runTest {
            val targetStatusKey = MicroBlogKey(id = "3350001_10", host = "instagram.com")
            val syncService = InstagramSyncService()
            syncService.cacheItems(
                listOf(
                    InstagramMediaOrAd(
                        id = "3350001_10",
                        pk = "3350001",
                        code = "C5zPost",
                        taken_at = 1713000000,
                        media_type = 1,
                        caption = InstagramCaption(text = "Main focal Instagram post"),
                        user =
                            InstagramUser(
                                pk = "10",
                                username = "creator_handle",
                                full_name = "Creator Name",
                                profile_pic_url = "https://example.com/creator.jpg",
                            ),
                        image_versions2 =
                            InstagramImageVersions(
                                candidates =
                                    listOf(
                                        InstagramImageCandidate(url = "https://example.com/main.jpg", width = 1080, height = 1080),
                                    ),
                            ),
                        like_count = 250,
                        comment_count = 2,
                    ),
                ),
            )

            val credentialFlow = flowOf(InstagramCredential(cookies = "sessionid=123; ds_user_id=10; csrftoken=t", userId = "10"))
            val contextLoader =
                InstagramContextRemoteLoader(
                    statusKey = targetStatusKey,
                    accountKey = accountKey,
                    credentialFlow = credentialFlow,
                    syncService = syncService,
                )

            val refreshResult = contextLoader.load(pageSize = 20, request = PagingRequest.Refresh)
            assertEquals(1, refreshResult.data.size)
            assertEquals(targetStatusKey, refreshResult.data[0].statusKey)

            val update = contextLoader.contextUpdate(PagingRequest.Refresh, refreshResult, initial = true)
            assertEquals(1, update.posts.size)
        }

    @Test
    fun loadsUserProfileAndUserTimelineCorrectlyFromCache() =
        runTest {
            val user =
                InstagramUser(
                    pk = "777",
                    username = "studio_artist",
                    full_name = "Studio Artist",
                    biography = "Digital painter & visual storyteller",
                    profile_pic_url = "https://example.com/low.jpg",
                    hd_profile_pic_url_info =
                        InstagramImageCandidate(
                            url = "https://example.com/hd_avatar.jpg",
                            width = 1080,
                            height = 1080,
                        ),
                    is_verified = true,
                    follower_count = 12500,
                    following_count = 340,
                    media_count = 88,
                )

            val profile = InstagramTimelineMapper.renderProfile(user, accountKey)
            assertNotNull(profile)
            assertEquals("Studio Artist", profile.name.innerText)
            assertEquals("https://example.com/hd_avatar.jpg", profile.avatar?.url)
            assertEquals("Digital painter & visual storyteller", profile.description?.innerText)
            assertEquals(12500L, profile.matrices.fansCount)
            assertEquals(340L, profile.matrices.followsCount)
            assertEquals(88L, profile.matrices.statusesCount)

            val syncService = InstagramSyncService()
            syncService.cacheItems(
                listOf(
                    InstagramMediaOrAd(
                        id = "9001_777",
                        pk = "9001",
                        code = "ART1",
                        taken_at = 1713000500,
                        media_type = 1,
                        caption = InstagramCaption(text = "New canvas study"),
                        user = user,
                    ),
                ),
            )

            val userLoader =
                InstagramUserTimelineRemoteLoader(
                    userKey = MicroBlogKey(id = "777", host = "instagram.com"),
                    accountKey = accountKey,
                    credentialFlow = flowOf(InstagramCredential(cookies = "sessionid=123; ds_user_id=10; csrftoken=t", userId = "10")),
                    syncService = syncService,
                )
            val feedResult = userLoader.load(pageSize = 12, request = PagingRequest.Refresh)
            assertEquals(1, feedResult.data.size)
        }

    @Test
    fun testCommentsJsonParsingWithNumericPk() {
        val json =
            """
            {
              "comments": [
                {
                  "pk": 17989938422711285,
                  "text": "This is an example comment!",
                  "created_at": 1697054108,
                  "comment_like_count": 14,
                  "child_comment_count": 2,
                  "has_liked_comment": false,
                  "user": {
                    "pk": 459545336,
                    "username": "example_user"
                  }
                }
              ],
              "comment_count": 1,
              "has_more_comments": false,
              "status": "ok"
            }
            """.trimIndent()

        val parsed =
            JSON.decodeFromStringWithNullableFallback(
                InstagramCommentsResponse.serializer(),
                json,
            )

        assertEquals(1, parsed.comments.size)
        assertEquals("17989938422711285", parsed.comments[0].pk)
        assertEquals("459545336", parsed.comments[0].user?.resolvedId)
    }

    @Test
    fun testInstagramRichTextTokenizesMentionsHashtagsAndUrls() {
        val caption = "Check out @rishi and @team on #CruxApp! Read more at https://flare.social"
        val media =
            InstagramMediaOrAd(
                id = "998877_10",
                code = "Cxyz123",
                taken_at = 1713000000L,
                media_type = 1,
                caption = InstagramCaption(text = caption),
                user =
                    InstagramUser(
                        pk = "10",
                        username = "author",
                        full_name = "Author Name",
                    ),
            )

        val rendered = InstagramTimelineMapper.render(media, accountKey)
        assertNotNull(rendered)
        val originalText = rendered.post.content.original
        assertEquals(caption, originalText.innerText)

        val textContent = assertIs<RenderContent.Text>(originalText.renderRuns.first())
        val runs = textContent.runs

        // 1. "@rishi" -> Profile link
        val rishiRun = runs.firstOrNull { it is RenderRun.Text && it.text == "@rishi" } as? RenderRun.Text
        assertNotNull(rishiRun)
        assertNotNull(rishiRun.style.link)
        val rishiRoute = DeeplinkRoute.parse(rishiRun.style.link)
        val profileRoute = assertIs<DeeplinkRoute.Profile.UserNameWithHost>(rishiRoute)
        assertEquals("rishi", profileRoute.userName)
        assertEquals("instagram.com", profileRoute.host)

        // 2. "#CruxApp" -> Search link
        val hashtagRun = runs.firstOrNull { it is RenderRun.Text && it.text == "#CruxApp" } as? RenderRun.Text
        assertNotNull(hashtagRun)
        assertNotNull(hashtagRun.style.link)
        val hashtagRoute = DeeplinkRoute.parse(hashtagRun.style.link)
        val searchRoute = assertIs<DeeplinkRoute.Search>(hashtagRoute)
        assertEquals("#CruxApp", searchRoute.query)

        // 3. "https://flare.social" -> URL link
        val urlRun = runs.firstOrNull { it is RenderRun.Text && it.text == "https://flare.social" } as? RenderRun.Text
        assertNotNull(urlRun)
        assertEquals("https://flare.social", urlRun.style.link)
    }

    @Test
    fun testCommentReplyPrepopulatesUsername() {
        val comments =
            listOf(
                InstagramComment(
                    pk = "c_401",
                    text = "Inspiring post!",
                    created_at = 1713000500,
                    comment_like_count = 3,
                    child_comment_count = 0,
                    user =
                        InstagramUser(
                            pk = "88",
                            username = "sarah_designer",
                            full_name = "Sarah",
                        ),
                ),
            )

        val parentPostKey = MicroBlogKey("998877_10", "instagram.com")
        val rendered =
            InstagramTimelineMapper.renderComments(
                comments = comments,
                parentPostKey = parentPostKey,
                parentAuthorHandle = "author",
                accountKey = accountKey,
            )

        assertEquals(1, rendered.size)
        val commentPost = assertIs<UiTimelineV2.TimelinePostItem>(rendered[0]).post

        // Reply action is index 0 in comment actions
        val replyAction = assertIs<org.crux.data.datasource.microblog.ActionMenu.Item>(commentPost.actions[0])
        val clickEvent = assertIs<ClickEvent.Deeplink>(replyAction.clickEvent)
        val parsedRoute = DeeplinkRoute.parse(clickEvent.url)
        val composeReply = assertIs<DeeplinkRoute.Compose.Reply>(parsedRoute)

        assertEquals("@sarah_designer ", composeReply.initialText)
        assertEquals("998877_10#comment_c_401", composeReply.statusKey.id)
    }

    @Test
    fun testChildCommentsResolutionAndExpandableItem() =
        runTest {
            val topCommentId = "c_500"
            val mediaId = "888001"

            val comments =
                listOf(
                    InstagramComment(
                        pk = topCommentId,
                        text = "Top-level comment with many replies",
                        created_at = 1713000100,
                        child_comment_count = 5,
                        user =
                            InstagramUser(
                                pk = "30",
                                username = "thread_starter",
                            ),
                        preview_child_comments =
                            listOf(
                                InstagramComment(
                                    pk = "c_501",
                                    text = "Preview reply 1",
                                    created_at = 1713000200,
                                    user = InstagramUser(pk = "31", username = "replier_one"),
                                ),
                                InstagramComment(
                                    pk = "c_502",
                                    text = "Resolved child reply 2",
                                    created_at = 1713000300,
                                    user = InstagramUser(pk = "32", username = "replier_two"),
                                ),
                                InstagramComment(
                                    pk = "c_503",
                                    text = "Resolved child reply 3",
                                    created_at = 1713000400,
                                    user = InstagramUser(pk = "33", username = "replier_three"),
                                ),
                            ),
                    ),
                )

            val parentPostKey = MicroBlogKey(id = "${mediaId}_10", host = "instagram.com")
            val commentItems =
                InstagramTimelineMapper.renderComments(
                    comments = comments,
                    parentPostKey = parentPostKey,
                    parentAuthorHandle = "creator",
                    accountKey = accountKey,
                )

            // Expected:
            // 1. Top-level comment (c_500)
            // 2. Child reply 1 (c_501)
            // 3. Child reply 2 (c_502)
            // 4. Child reply 3 (c_503)
            // 5. Expandable Message item ("View 2 more replies...")
            assertEquals(5, commentItems.size)

            val messageItem = assertIs<UiTimelineV2.Message>(commentItems.last())
            val rawMessage = assertIs<UiTimelineV2.Message.Type.Raw>(messageItem.type)
            assertEquals("View 2 more replies...", rawMessage.content)

            val messageDeeplink = assertIs<ClickEvent.Deeplink>(messageItem.clickEvent)
            val parsedReply = assertIs<DeeplinkRoute.Compose.Reply>(DeeplinkRoute.parse(messageDeeplink.url))
            assertEquals("@thread_starter ", parsedReply.initialText)
        }

    @Test
    fun testPublishThrowsUnsupportedOperationExceptionEvenWithCookies() =
        runTest {
            val service = InstagramFeedService()
            val cookies = "sessionid=123; ds_user_id=10; csrftoken=token123"
            val dataSource =
                InstagramDataSource(
                    accountKey = accountKey,
                    credentialFlow = flowOf(InstagramCredential(cookies = cookies, userId = "10")),
                    service = service,
                )
            val composeData =
                org.crux.data.datasource.microblog.ComposeData(
                    content = "Trying to comment with saved cookies",
                    referenceStatus =
                        org.crux.data.datasource.microblog.ComposeData.ReferenceStatus(
                            composeStatus =
                                org.crux.ui.presenter.compose.ComposeStatus.Reply(
                                    statusKey = MicroBlogKey(id = "123#comment_999", host = "instagram.com"),
                                ),
                        ),
                )
            kotlin.test.assertFailsWith<UnsupportedOperationException> {
                dataSource.publish(composeData) {}
            }
        }
}
