package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.platform.InstagramCredential
import dev.dimension.flare.data.platform.InstagramPlatformSpec
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
        val mediaOrAd = InstagramMediaOrAd(
            id = "3350123456789_123456",
            code = "C5zAbCdEf",
            taken_at = 1713000000L,
            media_type = 1,
            caption = InstagramCaption(text = "Exploring the mountain trail! #hiking"),
            user = InstagramUser(
                pk = "123456",
                username = "trailblazer",
                full_name = "Alex Explorer",
                profile_pic_url = "https://instagram.example.com/avatar.jpg",
                is_verified = true,
            ),
            image_versions2 = InstagramImageVersions(
                candidates = listOf(
                    InstagramImageCandidate(url = "https://instagram.example.com/photo_high.jpg", width = 1080, height = 1080),
                    InstagramImageCandidate(url = "https://instagram.example.com/photo_low.jpg", width = 320, height = 320),
                )
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
    }

    @Test
    fun mapsCarouselWithPhotosAndVideos() {
        val mediaOrAd = InstagramMediaOrAd(
            id = "3350999999999_123456",
            code = "C5zMulti",
            taken_at = 1713005000L,
            media_type = 8,
            caption = InstagramCaption(text = "Weekend trip dump"),
            user = InstagramUser(
                pk = "123456",
                username = "trailblazer",
                full_name = "Alex Explorer",
                profile_pic_url = "https://instagram.example.com/avatar.jpg",
            ),
            carousel_media = listOf(
                InstagramCarouselItem(
                    id = "carousel_item_1",
                    media_type = 1,
                    image_versions2 = InstagramImageVersions(
                        candidates = listOf(
                            InstagramImageCandidate(url = "https://instagram.example.com/carousel_1.jpg", width = 1080, height = 1350),
                        )
                    ),
                ),
                InstagramCarouselItem(
                    id = "carousel_item_2",
                    media_type = 2,
                    video_versions = listOf(
                        InstagramVideoVersion(url = "https://instagram.example.com/video_2.mp4", width = 720, height = 1280),
                    ),
                    image_versions2 = InstagramImageVersions(
                        candidates = listOf(
                            InstagramImageCandidate(url = "https://instagram.example.com/video_2_thumb.jpg", width = 720, height = 1280),
                        )
                    ),
                )
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
        val sponsoredMedia = InstagramMediaOrAd(
            id = "ad_123",
            user = InstagramUser(username = "brand"),
            is_paid_partnership = true,
        )
        assertNull(InstagramTimelineMapper.render(sponsoredMedia, accountKey))

        val adActionMedia = InstagramMediaOrAd(
            id = "ad_456",
            user = InstagramUser(username = "brand"),
            ad_action = "Install Now",
        )
        assertNull(InstagramTimelineMapper.render(adActionMedia, accountKey))
    }

    @Test
    fun remoteLoaderPaginatesWithMockEngine() = runTest {
        val sampleJson = """
            {
              "feed_items": [
                {
                  "media_or_ad": {
                    "id": "item_1",
                    "code": "CODE1",
                    "taken_at": 1713000000,
                    "media_type": 1,
                    "caption": { "text": "Post 1" },
                    "user": {
                      "pk": "u1",
                      "username": "user1",
                      "profile_pic_url": "https://example.com/p.jpg"
                    },
                    "image_versions2": {
                      "candidates": [
                        { "url": "https://example.com/1.jpg", "width": 800, "height": 800 }
                      ]
                    }
                  }
                }
              ],
              "next_max_id": "cursor_page_2",
              "more_available": true
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            respond(
                content = sampleJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val service = InstagramFeedService(HttpClient(mockEngine))
        val credentialFlow = flowOf(
            InstagramCredential(
                cookies = "sessionid=123; ds_user_id=u1; csrftoken=token",
                userId = "u1",
            )
        )

        val loader = InstagramHomeTimelineRemoteLoader(
            service = service,
            accountKey = accountKey,
            credentialFlow = credentialFlow,
        )

        val result = loader.load(pageSize = 20, request = PagingRequest.Refresh)
        assertEquals(1, result.data.size)
        assertEquals("cursor_page_2", result.nextKey)
    }

    @Test
    fun platformSpecHasCorrectMetadataAndId() {
        assertEquals("instagram", InstagramPlatformSpec.platformId)
        assertEquals("Instagram", InstagramPlatformSpec.metadata.displayName)
        assertEquals(UiIcon.Instagram, InstagramPlatformSpec.metadata.icon)
        assertEquals(1, InstagramPlatformSpec.timelineSpecs.size)
    }
}
