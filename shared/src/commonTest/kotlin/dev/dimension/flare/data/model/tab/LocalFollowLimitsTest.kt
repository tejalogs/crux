package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.database.app.model.DbLocalFollow
import dev.dimension.flare.model.MicroBlogKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalFollowLimitsTest {
    private fun follow(
        id: Int,
        platformId: String,
    ) = DbLocalFollow(
        accountKey = MicroBlogKey("me", "host"),
        userKey = MicroBlogKey("user$id", "host"),
        platformId = platformId,
        handle = "@user$id",
        displayName = "User $id",
        avatar = null,
        groupId = null,
        createdAt = id.toLong(),
    )

    @Test
    fun instagramAndLinkedInAreNeverFetched() {
        val selected =
            LocalFollowLimits.select(
                listOf(follow(1, "instagram"), follow(2, "linkedin"), follow(3, "mastodon"), follow(4, "xQt")),
            )
        assertEquals(listOf("mastodon"), selected.map { it.platformId })
        assertFalse(LocalFollowLimits.isSupported("instagram"))
        assertTrue(LocalFollowLimits.isSupported("mastodon"))
    }

    @Test
    fun cookieSessionPlatformsKeepOnlyTheNewestFollowsUpToTheCap() {
        val follows = (1..30).map { follow(it, "vvo") }
        val selected = LocalFollowLimits.select(follows)
        assertEquals(LocalFollowLimits.COOKIE_SESSION_MAX_ACCOUNTS, selected.size)
        assertEquals(30L, selected.first().createdAt)
        assertEquals(11L, selected.last().createdAt)
    }

    @Test
    fun otherPlatformsAreNotCapped() {
        assertEquals(30, LocalFollowLimits.select((1..30).map { follow(it, "mastodon") }).size)
    }
}
