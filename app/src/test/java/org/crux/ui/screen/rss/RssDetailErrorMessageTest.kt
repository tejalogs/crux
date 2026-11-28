package org.crux.ui.screen.rss

import org.crux.R
import org.crux.rss.RssArticleFailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

class RssDetailErrorMessageTest {
    @Test
    fun eachFailureHasItsOwnPlainMessage() {
        assertEquals(R.string.rss_detail_error_unreachable, articleErrorMessageRes(RssArticleFailureReason.Unreachable))
        assertEquals(R.string.rss_detail_error_timeout, articleErrorMessageRes(RssArticleFailureReason.TimedOut))
        assertEquals(R.string.rss_detail_error_no_article, articleErrorMessageRes(RssArticleFailureReason.NoArticle))
    }

    @Test
    fun unknownFailuresFallBackToTheGenericMessage() {
        assertEquals(R.string.rss_detail_unavailable, articleErrorMessageRes(RssArticleFailureReason.Other))
        assertEquals(R.string.rss_detail_unavailable, articleErrorMessageRes(RssArticleFailureReason.HttpError))
    }
}
