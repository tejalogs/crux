package org.crux.ui.component.status

import kotlinx.collections.immutable.persistentListOf
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.UiTranslatableText
import org.crux.ui.render.toUi
import org.crux.ui.render.toUiPlainText
import org.crux.ui.route.DeeplinkRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class MediaClickPolicyTest {
    @Test
    fun openingTheSecondIdenticalImageKeepsItsIndex() {
        val image = UiMedia.Image("image", "preview", null, 100f, 100f, false)
        val post =
            UiTimelineV2.Post(
                platformId = "test",
                images = persistentListOf(image, image),
                sensitive = false,
                contentWarning = null,
                user = null,
                content = UiTranslatableText("post".toUiPlainText()),
                actions = persistentListOf(),
                poll = null,
                statusKey = MicroBlogKey("post", "example.com"),
                card = null,
                createdAt = Instant.fromEpochSeconds(1).toUi(),
                clickEvent = ClickEvent.Noop,
                accountType = AccountType.Guest,
            )
        var opened: DeeplinkRoute? = null

        post.openMedia(index = 1, launcher = { opened = DeeplinkRoute.parse(it) })

        val route = assertIs<DeeplinkRoute.Media.StatusMedia>(opened)
        assertEquals(1, route.index)
        assertEquals("preview", route.preview)
        assertEquals(2, post.images.size)
    }
}
