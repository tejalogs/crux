package org.crux.ui.presenter.home

import org.crux.data.repository.KeywordFilterPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelineKeywordFilterSafetyTest {
    private fun regexFilter(pattern: String): KeywordFilterPattern =
        KeywordFilterPattern(
            keyword = pattern,
            isRegex = true,
            regex = runCatching { Regex(pattern, setOf(RegexOption.IGNORE_CASE)) }.getOrNull(),
        )

    private fun plainFilter(keyword: String): KeywordFilterPattern = KeywordFilterPattern(keyword = keyword, isRegex = false)

    @Test
    fun backtrackingShapesAreDetected() {
        listOf(
            // Repeated group containing a quantifier.
            "(\\w+\\s?)*!$",
            "(a+)+",
            "(a*b?)*c",
            "(x{2,})+",
            "((ab)+c)*",
            "(?:a+)+",
            "(?<name>a*)+",
            // Repeated group containing an alternation.
            "(\\w|\\d)+!",
            "(foo|bar)+",
            "(a|a){30}",
            // More than one unbounded quantifier (polynomial backtracking).
            ".*.*.*.*.*.*!",
            "\\w*\\w*\\w*!",
            "\\(a+\\)+",
            ".{0,100}.*x",
            // Too many bounded choice points.
            "a?a?a?a?a?a?aaaaaa",
        ).forEach { assertTrue(hasCatastrophicBacktrackingRisk(it), "expected backtracking risk in $it") }
    }

    @Test
    fun ordinaryPatternsAreNotFlagged() {
        listOf(
            "crypto\\s+giveaway",
            "(https?://)?spam\\.example",
            "\\(a+\\)",
            "[(a+)]+",
            "(?i)hello\\s+world",
            "(colou?r)?s",
            "^bye",
            "\\d{4}-\\d{2}-\\d{2}",
            "\\btrump\\b",
            "foo|bar|baz",
            ".*!",
        ).forEach { assertFalse(hasCatastrophicBacktrackingRisk(it), "did not expect backtracking risk in $it") }
    }

    @Test
    fun filtersThatMatchMostPostsAreDropped() {
        val kept =
            listOf(
                regexFilter("\\w"),
                regexFilter("\\b"),
                regexFilter("[a-z]"),
                regexFilter("\\s"),
                regexFilter("[^!]"),
                regexFilter("e"),
                plainFilter("e"),
                plainFilter("a"),
                regexFilter("\\btrump\\b"),
                regexFilter("#\\w+"),
                plainFilter("news"),
                // A single character can be a real keyword in other scripts.
                plainFilter("の"),
            ).safeForTimeline()

        assertEquals(listOf("\\btrump\\b", "#\\w+", "news", "の"), kept.map { it.keyword })
    }

    @Test
    fun polynomialBacktrackingRegexesAreDropped() {
        val kept =
            listOf(
                regexFilter(".*.*.*.*.*.*!"),
                regexFilter("\\w*\\w*\\w*!"),
                regexFilter("(\\w|\\d)+!"),
                regexFilter("spam\\s+bot"),
            ).safeForTimeline()

        assertEquals(listOf("spam\\s+bot"), kept.map { it.keyword })
    }

    @Test
    fun safeForTimelineDropsDangerousFilters() {
        val kept =
            listOf(
                regexFilter("(\\w+\\s?)*!$"),
                regexFilter("."),
                regexFilter(".*"),
                regexFilter("x?"),
                regexFilter("\\S"),
                regexFilter("("),
                regexFilter("a".repeat(MAX_TIMELINE_REGEX_LENGTH + 1)),
                plainFilter(""),
                plainFilter("   "),
                plainFilter("b".repeat(MAX_TIMELINE_KEYWORD_LENGTH + 1)),
                regexFilter("hello\\s+kotlin"),
                plainFilter("spoiler"),
                // A plain keyword is literal text even if it looks like a regex.
                plainFilter(".*"),
            ).safeForTimeline()

        assertEquals(listOf("hello\\s+kotlin", "spoiler", ".*"), kept.map { it.keyword })
        assertEquals(listOf(true, false, false), kept.map { it.isRegex })
    }

    @Test
    fun safeForTimelineCapsFilterCount() {
        val filters = (0 until MAX_TIMELINE_KEYWORD_FILTERS + 10).map { plainFilter("word$it") }

        assertEquals(MAX_TIMELINE_KEYWORD_FILTERS, filters.safeForTimeline().size)
    }
}
