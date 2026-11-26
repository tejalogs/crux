package org.crux.ui.screen.rss

import org.crux.R
import org.crux.data.network.rss.ArticleFailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

class RssDetailErrorMessageTest {
    @Test
    fun eachFailureHasItsOwnPlainMessage() {
        assertEquals(R.string.rss_detail_error_unreachable, articleErrorMessageRes(ArticleFailureReason.Unreachable))
        assertEquals(R.string.rss_detail_error_timeout, articleErrorMessageRes(ArticleFailureReason.TimedOut))
        assertEquals(R.string.rss_detail_error_no_article, articleErrorMessageRes(ArticleFailureReason.NoArticle))
    }

    @Test
    fun unknownFailuresFallBackToTheGenericMessage() {
        assertEquals(R.string.rss_detail_unavailable, articleErrorMessageRes(ArticleFailureReason.Other))
        assertEquals(R.string.rss_detail_unavailable, articleErrorMessageRes(ArticleFailureReason.HttpError))
    }
}
