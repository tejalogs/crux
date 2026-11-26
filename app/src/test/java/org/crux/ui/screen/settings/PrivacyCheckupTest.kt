package org.crux.ui.screen.settings

import org.crux.ui.route.Route
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.PrivacyCheckupKind
import org.crux.ui.screen.home.sanitizeSiteInitialUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class PrivacyCheckupTest {
    @Test
    fun everySiteHasACheckupWithItsDownloadPage() {
        LiveWebSite.entries.forEach { site ->
            val kinds = site.privacyCheckup.map { it.kind }
            assertTrue("${site.name} has no checkup", kinds.isNotEmpty())
            assertTrue("${site.name} has no download page", PrivacyCheckupKind.DownloadData in kinds)
        }
    }

    @Test
    fun checkupUrlsAreHttpsAndStayOnTheSitesOwnHosts() {
        LiveWebSite.entries.forEach { site ->
            site.privacyCheckup.forEach { item ->
                val uri = URI(item.url)
                assertEquals("${item.url} must be https", "https", uri.scheme)
                assertTrue("${site.name} ${item.url} must stay on an allowed host", site.allows(uri.host))
                assertTrue("${item.url} must carry no query", uri.rawQuery.isNullOrEmpty())
            }
        }
    }

    @Test
    fun openingAnItemLandsOnThatExactPageInTheSitesOwnTab() {
        LiveWebSite.entries.forEach { site ->
            site.privacyCheckup.forEach { item ->
                assertEquals(item.url, sanitizeSiteInitialUrl(site, item.url))
                assertEquals(
                    Route.LiveWeb(site = site.name, initialUrl = item.url),
                    privacyCheckupRoute(site, item),
                )
            }
        }
    }

    @Test
    fun noSiteRepeatsAPageOrASetting() {
        LiveWebSite.entries.forEach { site ->
            val items = site.privacyCheckup
            assertEquals("${site.name} repeats a URL", items.size, items.map { it.url }.toSet().size)
            assertEquals("${site.name} repeats a setting", items.size, items.map { it.kind }.toSet().size)
        }
    }

    @Test
    fun existingAiPrivacyLinkIsAlwaysPartOfTheCheckup() {
        LiveWebSite.entries.forEach { site ->
            assertTrue(site.name, site.privacyCheckup.any { it.url == site.aiPrivacyUrl })
        }
    }

    @Test
    fun aiTrainingRowsOnlyWhereTheSiteHasARealOptOut() {
        // Meta offers no AI-training opt-out for everyone, so Instagram's privacy page is not labelled as one.
        assertFalse(LiveWebSite.Instagram.privacyCheckup.any { it.kind == PrivacyCheckupKind.AiTraining })
        listOf(LiveWebSite.X, LiveWebSite.LinkedIn).forEach { site ->
            val ai = site.privacyCheckup.single { it.kind == PrivacyCheckupKind.AiTraining }
            assertEquals(site.aiPrivacyUrl, ai.url)
        }
    }

    @Test
    fun instagramAdAndDataPagesUseAccountsCenter() {
        val hosts =
            LiveWebSite.Instagram.privacyCheckup
                .filter { it.kind != PrivacyCheckupKind.AccountPrivacy }
                .map { URI(it.url).host }
        assertTrue(hosts.all { it == "accountscenter.instagram.com" })
    }

    @Test
    fun everyKindHasDistinctTitleAndReason() {
        val titles = PrivacyCheckupKind.entries.map { it.titleRes() }
        val reasons = PrivacyCheckupKind.entries.map { it.whyRes() }
        assertEquals(titles.size, titles.toSet().size)
        assertEquals(reasons.size, reasons.toSet().size)
        assertTrue(titles.none { it in reasons })
    }

    @Test
    fun checkupListsEveryPlatformSiteOnce() {
        val sites = checkupPlatforms().map { it.second }
        assertEquals(sites.size, sites.toSet().size)
        assertTrue(sites.isNotEmpty())
    }
}
