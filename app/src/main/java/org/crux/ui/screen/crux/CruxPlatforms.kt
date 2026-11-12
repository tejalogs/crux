package org.crux.ui.screen.crux

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.brands.LinkedinIn
import compose.icons.fontawesomeicons.brands.RedditAlien
import compose.icons.fontawesomeicons.brands.XTwitter
import compose.icons.fontawesomeicons.solid.SquareRss
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.data.datastore.model.AppSettings
import org.crux.data.platform.INSTAGRAM_PLATFORM_ID
import org.crux.data.platform.LINKEDIN_PLATFORM_ID
import org.crux.data.repository.SettingsRepository
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.invoke
import org.crux.ui.route.Route
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.clearSession
import org.crux.ui.screen.home.cookieManager
import org.crux.ui.screen.settings.AccountManagementPresenter
import org.koin.compose.koinInject

/**
 * The networks Crux is built around. Every social network is the real site in an isolated WebView;
 * RSS is native. Order here is the order used everywhere in the UI.
 */
internal enum class CruxPlatform(
    val displayName: String,
    val brandColor: Color,
    @StringRes val tagline: Int,
    val site: LiveWebSite?,
    /** The site's own composer can be opened prefilled from the Crux compose button. */
    val canPost: Boolean,
    /** Useful without signing in (Reddit can be browsed signed out, RSS has no account). */
    val worksSignedOut: Boolean,
) {
    X(
        displayName = "X",
        brandColor = Color(0xFF14171A),
        tagline = R.string.crux_platform_x_tagline,
        site = LiveWebSite.X,
        canPost = true,
        worksSignedOut = false,
    ),
    Instagram(
        displayName = "Instagram",
        brandColor = Color(0xFFE1306C),
        tagline = R.string.crux_platform_instagram_tagline,
        site = LiveWebSite.Instagram,
        canPost = true,
        worksSignedOut = false,
    ),
    LinkedIn(
        displayName = "LinkedIn",
        brandColor = Color(0xFF0A66C2),
        tagline = R.string.crux_platform_linkedin_tagline,
        site = LiveWebSite.LinkedIn,
        canPost = true,
        worksSignedOut = false,
    ),
    Reddit(
        displayName = "Reddit",
        brandColor = Color(0xFFFF4500),
        tagline = R.string.crux_platform_reddit_tagline,
        site = LiveWebSite.Reddit,
        canPost = true,
        worksSignedOut = true,
    ),
    Rss(
        displayName = "Feeds",
        brandColor = Color(0xFFF26522),
        tagline = R.string.crux_platform_rss_tagline,
        site = null,
        canPost = false,
        worksSignedOut = true,
    ),
    ;

    val icon: ImageVector
        get() =
            when (this) {
                X -> FontAwesomeIcons.Brands.XTwitter
                Instagram -> FontAwesomeIcons.Brands.Instagram
                LinkedIn -> FontAwesomeIcons.Brands.LinkedinIn
                Reddit -> FontAwesomeIcons.Brands.RedditAlien
                Rss -> FontAwesomeIcons.Solid.SquareRss
            }

    /** Where tapping the platform in the side panel or accounts screen goes. */
    val route: Route
        get() =
            when (this) {
                Instagram -> Route.InstagramWeb()
                LinkedIn -> Route.LinkedInWeb()
                Rss -> Route.Rss.Sources
                X, Reddit -> Route.LiveWeb(name)
            }

    companion object {
        fun fromName(name: String): CruxPlatform? = entries.firstOrNull { it.name == name }

        fun forSite(site: LiveWebSite): CruxPlatform = entries.first { it.site == site }

        /** Platforms whose own composer the post panel can open: the one source for every share and compose list. */
        val shareTargets: List<CruxPlatform>
            get() = entries.filter { it.canPost && it.site != null }
    }
}

/** Reads the site's own isolated cookie jar. Nothing leaves the device. */
internal fun LiveWebSite.hasSession(): Boolean =
    runCatching {
        val cm = cookieManager()
        cookieDomains
            .flatMap { listOf("https://$it/", "https://www.$it/") }
            .flatMap { cm.getCookie(it).orEmpty().split(";") }
            .any { it.trim().substringBefore("=") in sessionCookies }
    }.getOrDefault(false)

internal fun CruxPlatform.isSignedIn(): Boolean = site?.hasSession() ?: true

@Stable
internal class CruxConnectionsState(
    val loaded: Boolean,
    val onboardingCompleted: Boolean,
    val enabled: List<CruxPlatform>,
    val signedIn: Set<CruxPlatform>,
    private val onSetEnabled: (CruxPlatform, Boolean) -> Unit,
    private val onCompleteOnboarding: (List<CruxPlatform>) -> Unit,
    private val onResetOnboarding: () -> Unit,
    private val onSignOut: (CruxPlatform) -> Unit,
    private val onRefresh: () -> Unit,
) {
    fun isEnabled(platform: CruxPlatform) = platform in enabled

    fun isSignedIn(platform: CruxPlatform) = platform in signedIn

    /** Enabled platforms whose site composer can be opened, signed-in ones first. */
    val postTargets: List<CruxPlatform>
        get() = enabled.filter { it.canPost }.sortedByDescending { it in signedIn }

    fun setEnabled(
        platform: CruxPlatform,
        enabled: Boolean,
    ) = onSetEnabled(platform, enabled)

    fun completeOnboarding(platforms: List<CruxPlatform>) = onCompleteOnboarding(platforms)

    fun resetOnboarding() = onResetOnboarding()

    fun signOut(platform: CruxPlatform) = onSignOut(platform)

    /** Re-reads cookie jars, e.g. after returning from a sign-in page. */
    fun refresh() = onRefresh()
}

private data class StoredConnections(
    val loaded: Boolean,
    val onboardingCompleted: Boolean,
    val platforms: List<String>,
)

/**
 * The one connections state for the whole home screen. [HomeScreen][org.crux.ui.screen.home.HomeScreen]
 * creates it and shares it down the tree, so the dock, the compose sheet and settings all read the
 * same state instead of each running their own cookie checks.
 */
internal val LocalCruxConnections = staticCompositionLocalOf<CruxConnectionsState?> { null }

/** The shared state when there is one, otherwise a private one (previews, stand-alone screens). */
@Composable
internal fun rememberCruxConnections(): CruxConnectionsState = LocalCruxConnections.current ?: rememberCruxConnectionsState()

/** Reads every live site's cookie jar once. Cheap, but not free, so it is event-driven, never polled. */
internal fun readSignedInPlatforms(): Set<CruxPlatform> = CruxPlatform.entries.filter { it.site != null && it.isSignedIn() }.toSet()

/**
 * Which platforms are in use and which are signed in. Choices live in [AppSettings]. Signed-in state
 * is read from each site's isolated cookie jar when the app resumes, when a screen is closed
 * ([CruxConnectionsState.refresh]) and when a site page finishes loading, so signing in shows up
 * without any polling.
 */
@Composable
internal fun rememberCruxConnectionsState(): CruxConnectionsState {
    val settingsRepository = koinInject<SettingsRepository>()
    val scope = rememberCoroutineScope()
    val stored by remember(settingsRepository) {
        settingsRepository.appSettings.map {
            StoredConnections(
                loaded = true,
                onboardingCompleted = it.onboardingCompleted,
                platforms = it.cruxPlatforms,
            )
        }
    }.collectAsState(
        // Not loaded yet: callers show a blank surface instead of guessing onboarding is done.
        initial =
            StoredConnections(
                loaded = false,
                onboardingCompleted = false,
                platforms = AppSettings.DEFAULT_CRUX_PLATFORMS,
            ),
    )
    val accountState = remember { AccountManagementPresenter() }.invoke()
    var signedIn by remember { mutableStateOf(readSignedInPlatforms()) }
    val refresh = remember { { signedIn = readSignedInPlatforms() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val enabled = remember(stored.platforms) { CruxPlatform.entries.filter { it.name in stored.platforms } }

    fun persist(
        platforms: List<CruxPlatform>,
        platformsToAdd: Set<CruxPlatform> = platforms.toSet(),
        block: AppSettings.() -> AppSettings = { this },
    ) {
        val names = CruxPlatform.entries.filter { it in platforms }.map { it.name }
        val addNames = platformsToAdd.map { it.name }.toSet()
        scope.launch {
            settingsRepository.updateAppSettings { block().copy(cruxPlatforms = names) }
            settingsRepository.syncLiveWebTabs(enabledPlatforms = names.toSet(), platformsToAdd = addNames)
        }
    }

    fun signOut(platform: CruxPlatform) {
        platform.site?.clearSession()
        // Instagram and LinkedIn sessions are also mirrored as accounts so their feeds show up as pills.
        val platformId =
            when (platform) {
                CruxPlatform.Instagram -> INSTAGRAM_PLATFORM_ID
                CruxPlatform.LinkedIn -> LINKEDIN_PLATFORM_ID
                else -> null
            }
        if (platformId != null) {
            accountState.accounts.onSuccess { items ->
                items.filter { it.account.platformId == platformId }.forEach { accountState.logout(it.account.accountKey) }
            }
        }
        refresh()
    }

    return CruxConnectionsState(
        loaded = stored.loaded,
        onboardingCompleted = stored.onboardingCompleted,
        enabled = enabled,
        signedIn = signedIn,
        onSetEnabled = { platform, on ->
            val updated = if (on) enabled + platform else enabled - platform
            persist(updated, platformsToAdd = if (on) setOf(platform) else emptySet())
            if (!on) signOut(platform)
        },
        onCompleteOnboarding = { platforms ->
            persist(platforms, platformsToAdd = platforms.toSet()) { copy(onboardingCompleted = true) }
        },
        onResetOnboarding = {
            scope.launch { settingsRepository.updateAppSettings { copy(onboardingCompleted = false) } }
        },
        onSignOut = ::signOut,
        onRefresh = refresh,
    )
}
