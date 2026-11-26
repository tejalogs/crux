package org.crux.ui.screen.home

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class LiveWebPrivacyStoreMigrationTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun freshPrefs() {
        val context: Context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("web_privacy_migration_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    /** What 1.0.12's write() left behind after any one change: every key, with 1.0.12's defaults. */
    private fun writeAs1012(extra: SharedPreferences.Editor.() -> Unit = {}) {
        val editor = prefs.edit()
        LiveWebSite.entries.forEach { site ->
            editor.putString("protection.${site.name}", site.defaultProtection.name)
            editor.putBoolean("block_autoplay_v2.${site.name}", false)
            SiteTweak.forSite(site).forEach { tweak ->
                val oldDefault = if (tweak == SiteTweak.RedditUnblurNsfw) true else tweak.defaultOn
                editor.putBoolean("tweak_v2.${tweak.name}", oldDefault)
            }
        }
        editor.putBoolean("hide_engagement_counts", false)
        editor.extra()
        editor.commit()
    }

    @Test
    fun settingsSavedBy1012GetNsfwOffAndTapToPlayOnRedditAndLinkedIn() {
        writeAs1012()

        val settings = LiveWebPrivacyStore(prefs).settings.value

        assertFalse(settings.site(LiveWebSite.Reddit).isOn(SiteTweak.RedditUnblurNsfw))
        assertTrue(settings.site(LiveWebSite.Reddit).blockAutoplay)
        assertTrue(settings.site(LiveWebSite.LinkedIn).blockAutoplay)
        assertEquals(WEB_PRIVACY_SCHEMA_VERSION, prefs.getInt(KEY_WEB_PRIVACY_SCHEMA, 0))
    }

    @Test
    fun migrationLeavesOtherSavedChoicesAlone() {
        writeAs1012 {
            putBoolean("tweak_v2.${SiteTweak.LinkedInSuggested.name}", true)
            putBoolean("block_autoplay_v2.${LiveWebSite.Instagram.name}", true)
            putBoolean("hide_engagement_counts", true)
        }

        val settings = LiveWebPrivacyStore(prefs).settings.value

        assertTrue(settings.site(LiveWebSite.LinkedIn).isOn(SiteTweak.LinkedInSuggested))
        assertTrue(settings.site(LiveWebSite.Instagram).blockAutoplay)
        assertTrue(settings.hideEngagementCounts)
    }

    @Test
    fun aChoiceMadeAfterTheMigrationSurvivesTheNextLaunch() {
        writeAs1012()
        LiveWebPrivacyStore(prefs).update { settings ->
            settings
                .updateSite(LiveWebSite.Reddit) { it.withTweak(SiteTweak.RedditUnblurNsfw, true) }
                .updateSite(LiveWebSite.LinkedIn) { it.withBlockAutoplay(false) }
        }

        val reopened = LiveWebPrivacyStore(prefs).settings.value

        assertTrue(reopened.site(LiveWebSite.Reddit).isOn(SiteTweak.RedditUnblurNsfw))
        assertFalse(reopened.site(LiveWebSite.LinkedIn).blockAutoplay)
        assertTrue(reopened.site(LiveWebSite.Reddit).blockAutoplay)
    }

    @Test
    fun migrationRunsOnlyOnce() {
        writeAs1012()
        migrateWebPrivacyPrefs(prefs)
        prefs.edit().putBoolean("tweak_v2.${SiteTweak.RedditUnblurNsfw.name}", true).commit()

        migrateWebPrivacyPrefs(prefs)

        assertTrue(prefs.getBoolean("tweak_v2.${SiteTweak.RedditUnblurNsfw.name}", false))
        assertTrue(LiveWebPrivacyStore(prefs).settings.value.site(LiveWebSite.Reddit).isOn(SiteTweak.RedditUnblurNsfw))
    }

    @Test
    fun aFreshInstallGetsTheNewDefaultsAndAStampedVersion() {
        val settings = LiveWebPrivacyStore(prefs).settings.value

        assertEquals(WebPrivacySettings.Default, settings)
        assertEquals(WEB_PRIVACY_SCHEMA_VERSION, prefs.getInt(KEY_WEB_PRIVACY_SCHEMA, 0))
    }

    @Test
    fun everyWriteStampsTheSchemaVersion() {
        prefs.edit().putInt(KEY_WEB_PRIVACY_SCHEMA, WEB_PRIVACY_SCHEMA_VERSION).commit()
        val store = LiveWebPrivacyStore(prefs)
        prefs.edit().remove(KEY_WEB_PRIVACY_SCHEMA).commit()

        store.update { it.copy(hideEngagementCounts = true) }

        assertEquals(WEB_PRIVACY_SCHEMA_VERSION, prefs.getInt(KEY_WEB_PRIVACY_SCHEMA, 0))
    }

    @Test
    fun afterTheResetRedditTakesBackTheOver18CookieCruxSetIn1012() {
        writeAs1012()
        val tweakOn = LiveWebPrivacyStore(prefs).configFor(LiveWebSite.Reddit).tweaks.contains(SiteTweak.RedditUnblurNsfw)
        val over18Prefs = redditOver18Prefs(RuntimeEnvironment.getApplication())
        over18Prefs.edit().clear().commit()

        // 1.0.12 never wrote the flag, so the missing flag must read as "maybe set by Crux".
        val maybeSetByCrux = redditOver18MaybeSetByCrux(over18Prefs)

        assertFalse(tweakOn)
        assertTrue(maybeSetByCrux)
        // Signed out or signed in, the cookie Crux set in 1.0.12 is taken back.
        assertEquals(
            RedditOver18CookieAction.Expire,
            redditOver18CookieAction(tweakOn = tweakOn, signedIn = false, maybeSetByCrux = maybeSetByCrux),
        )
        assertEquals(
            RedditOver18CookieAction.Expire,
            redditOver18CookieAction(tweakOn = tweakOn, signedIn = true, maybeSetByCrux = maybeSetByCrux),
        )
    }

    @Test
    fun onceCruxHasTakenTheCookieBackItLeavesTheReadersOwnAnswerAlone() {
        val over18Prefs = redditOver18Prefs(RuntimeEnvironment.getApplication())
        over18Prefs.edit().clear().putBoolean("over18_maybe_set_by_crux", false).commit()

        val maybeSetByCrux = redditOver18MaybeSetByCrux(over18Prefs)

        assertFalse(maybeSetByCrux)
        assertEquals(
            RedditOver18CookieAction.None,
            redditOver18CookieAction(tweakOn = false, signedIn = false, maybeSetByCrux = maybeSetByCrux),
        )
    }
}
