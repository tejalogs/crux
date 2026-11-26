package org.crux.ui.screen.crux

import org.crux.data.datastore.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CruxOnboardingLogicTest {
    private val allSteps = OnboardingStep.entries.toList()
    private val withoutSignIn = listOf(OnboardingStep.Welcome, OnboardingStep.Platforms, OnboardingStep.Calm)

    @Test
    fun signInStepIsHiddenWhenOnlyFeedsArePicked() {
        assertEquals(withoutSignIn, visibleOnboardingSteps(listOf(CruxPlatform.Rss)))
        assertEquals(withoutSignIn, visibleOnboardingSteps(emptyList()))
        assertEquals(allSteps, visibleOnboardingSteps(listOf(CruxPlatform.Rss, CruxPlatform.Mastodon)))
    }

    @Test
    fun backFromAppearanceSkipsHiddenSignInStep() {
        assertEquals(OnboardingStep.Platforms, previousOnboardingStep(OnboardingStep.Calm, withoutSignIn))
        assertEquals(OnboardingStep.SignIn, previousOnboardingStep(OnboardingStep.Calm, allSteps))
        assertNull(previousOnboardingStep(OnboardingStep.Welcome, allSteps))
    }

    @Test
    fun nextFollowsVisibleSteps() {
        assertEquals(OnboardingStep.Calm, nextOnboardingStep(OnboardingStep.Platforms, withoutSignIn))
        assertEquals(OnboardingStep.SignIn, nextOnboardingStep(OnboardingStep.Platforms, allSteps))
        assertNull(nextOnboardingStep(OnboardingStep.Calm, allSteps))
        // A restored SignIn step that is no longer visible still moves forward and back sensibly.
        assertEquals(OnboardingStep.Calm, nextOnboardingStep(OnboardingStep.SignIn, withoutSignIn))
        assertEquals(OnboardingStep.Platforms, previousOnboardingStep(OnboardingStep.SignIn, withoutSignIn))
    }

    @Test
    fun stepPositionCountsOnlyVisibleSteps() {
        assertEquals(1, onboardingStepPosition(OnboardingStep.Welcome, withoutSignIn))
        assertEquals(3, onboardingStepPosition(OnboardingStep.Calm, withoutSignIn))
        assertEquals(4, onboardingStepPosition(OnboardingStep.Calm, allSteps))
    }

    @Test
    fun freshInstallStartsWithOnlyFeeds() {
        val stored = AppSettings.DEFAULT_CRUX_PLATFORMS.mapNotNull { CruxPlatform.fromName(it) }
        assertEquals(listOf(CruxPlatform.Rss), initialOnboardingPicks(stored, emptySet()))
        assertEquals(listOf(CruxPlatform.Rss), initialOnboardingPicks(emptyList(), emptySet()))
    }

    @Test
    fun existingChoicesOrSignInsAreKept() {
        val stored = AppSettings.DEFAULT_CRUX_PLATFORMS.mapNotNull { CruxPlatform.fromName(it) }
        assertEquals(stored, initialOnboardingPicks(stored, setOf(CruxPlatform.Instagram)))
        val custom = listOf(CruxPlatform.Rss, CruxPlatform.Reddit)
        assertEquals(custom, initialOnboardingPicks(custom, emptySet()))
    }

    @Test
    fun replayedTourKeepsStoredDefaultsForReturningUser() {
        val stored = AppSettings.DEFAULT_CRUX_PLATFORMS.mapNotNull { CruxPlatform.fromName(it) }
        assertEquals(stored, initialOnboardingPicks(stored, emptySet(), returningUser = true))
        assertEquals(listOf(CruxPlatform.Rss), initialOnboardingPicks(emptyList(), emptySet(), returningUser = true))
    }

    @Test
    fun returningUserIsDetectedFromSettingsOrFeeds() {
        val fresh = AppSettings(version = "")
        assertFalse(isReturningOnboardingUser(fresh, existingFeedCount = 0))
        assertTrue(isReturningOnboardingUser(fresh.copy(liveWebTabsMigrated = true), existingFeedCount = 0))
        assertTrue(isReturningOnboardingUser(fresh.copy(rssDefaultsSeeded = true), existingFeedCount = 0))
        assertTrue(isReturningOnboardingUser(fresh, existingFeedCount = 3))
    }

    @Test
    fun skippingAReplayBeforePlatformsKeepsEveryTab() {
        val stored = AppSettings.DEFAULT_CRUX_PLATFORMS.mapNotNull { CruxPlatform.fromName(it) }
        val feedsOnly = listOf(CruxPlatform.Rss)
        // Returning user, or settings not loaded yet: Skip on Welcome keeps what they have.
        assertEquals(stored, onboardingPlatformsToSave(feedsOnly, stored, returningUser = true, platformsVisited = false))
        assertEquals(stored, onboardingPlatformsToSave(feedsOnly, stored, returningUser = null, platformsVisited = false))
        // A known new user gets the honest Feeds-only start.
        assertEquals(feedsOnly, onboardingPlatformsToSave(feedsOnly, stored, returningUser = false, platformsVisited = false))
        // Whoever reached Platforms saves exactly what they picked.
        assertEquals(feedsOnly, onboardingPlatformsToSave(feedsOnly, stored, returningUser = true, platformsVisited = true))
        assertEquals(feedsOnly, onboardingPlatformsToSave(feedsOnly, emptyList(), returningUser = null, platformsVisited = false))
    }

    @Test
    fun bannerSplitsSignedOutSitesFromSitesThatNeedSignIn() {
        val picks = listOf(CruxPlatform.Rss, CruxPlatform.X, CruxPlatform.Instagram, CruxPlatform.Reddit, CruxPlatform.Mastodon)
        val parts = signInBannerParts(picks, signedIn = setOf(CruxPlatform.X))
        assertEquals(listOf(CruxPlatform.Reddit, CruxPlatform.Mastodon), parts.browseSignedOut)
        assertEquals(listOf(CruxPlatform.Instagram), parts.needSignIn)
    }

    @Test
    fun xInstagramAndLinkedInNeedSignInWhileRedditAndMastodonDoNot() {
        assertFalse(CruxPlatform.X.worksSignedOut)
        assertFalse(CruxPlatform.Instagram.worksSignedOut)
        assertFalse(CruxPlatform.LinkedIn.worksSignedOut)
        assertTrue(CruxPlatform.Reddit.worksSignedOut)
        assertTrue(CruxPlatform.Mastodon.worksSignedOut)
    }

    @Test
    fun namesJoinWithFinalJoiner() {
        val and = { a: String, b: String -> "$a and $b" }
        assertEquals("", joinPlatformNames(emptyList(), and))
        assertEquals("Reddit", joinPlatformNames(listOf("Reddit"), and))
        assertEquals("Reddit and Mastodon", joinPlatformNames(listOf("Reddit", "Mastodon"), and))
        assertEquals("X, Instagram and LinkedIn", joinPlatformNames(listOf("X", "Instagram", "LinkedIn"), and))
    }

    @Test
    fun removeOffersChoiceOnlyWhenASessionWouldBeLost() {
        assertTrue(removeNeedsChoice(CruxPlatform.Instagram, signedIn = true))
        assertFalse(removeNeedsChoice(CruxPlatform.Instagram, signedIn = false))
        assertFalse(removeNeedsChoice(CruxPlatform.Rss, signedIn = true))
    }
}
