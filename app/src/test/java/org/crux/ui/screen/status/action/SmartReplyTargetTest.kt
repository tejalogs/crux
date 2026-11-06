package org.crux.ui.screen.status.action

import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartReplyTargetTest {
    @Test
    fun liveWebTabSitesUseCopyOnlySmartReply() {
        assertTrue(
            isCopyOnlySmartReplyTarget(
                statusKey = MicroBlogKey("123", "linkedin"),
                accountType = AccountType.Guest,
            ),
        )
        assertTrue(
            isCopyOnlySmartReplyTarget(
                statusKey = MicroBlogKey("456", "x"),
                accountType = AccountType.Guest,
            ),
        )
        assertTrue(
            isCopyOnlySmartReplyTarget(
                statusKey = MicroBlogKey("789", "reddit"),
                accountType = AccountType.Guest,
            ),
        )
        assertTrue(
            isCopyOnlySmartReplyTarget(
                statusKey = MicroBlogKey("101", "instagram.com"),
                accountType = AccountType.Specific(MicroBlogKey("u1", "instagram.com")),
            ),
        )
        assertTrue(
            isCopyOnlySmartReplyTarget(
                statusKey = MicroBlogKey("202", "rss"),
                accountType = AccountType.Specific(MicroBlogKey("u2", "example.com")),
            ),
        )
    }

    @Test
    fun nativeMicroblogStatusesAllowReplyInThread() {
        assertFalse(
            isCopyOnlySmartReplyTarget(
                statusKey = MicroBlogKey("999", "example.com"),
                accountType = AccountType.Specific(MicroBlogKey("user1", "example.com")),
            ),
        )
        assertFalse(
            isCopyOnlySmartReplyTarget(
                statusKey = MicroBlogKey("888", "bsky.app"),
                accountType = AccountType.Specific(MicroBlogKey("user2", "bsky.app")),
            ),
        )
    }
}
