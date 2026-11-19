package org.crux.ui.screen.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.RotateRight
import kotlinx.coroutines.delay
import org.crux.R
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.FAIcon
import org.crux.ui.screen.crux.CruxPlatform
import org.crux.ui.screen.crux.LocalCruxConnections
import org.crux.ui.screen.crux.PlatformBadge
import org.crux.ui.screen.crux.hasSession
import org.koin.compose.koinInject
import java.net.URLEncoder

/** Sites the post panel can open a ready-to-post composer on. Instagram opens home with caption copied to clipboard. */
internal val LIVE_SHARE_SITES: List<LiveWebSite>
    get() = CruxPlatform.shareTargets.mapNotNull { it.site }

/** Display name shown in the post walk-through. */
internal val LiveWebSite.label: String
    get() = CruxPlatform.forSite(this).displayName

private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

/**
 * The site's own share link. It only fills in the composer: the person always presses Post
 * themselves, because automating that inside a logged-in account is what gets accounts flagged.
 */
internal fun LiveWebSite.shareUrl(
    text: String,
    url: String,
): String? =
    when (this) {
        LiveWebSite.X -> {
            buildString {
                append("https://x.com/intent/post?text=").append(text.encoded())
                if (url.isNotBlank()) append("&url=").append(url.encoded())
            }
        }

        LiveWebSite.LinkedIn -> {
            val body = listOf(text, url).filter { it.isNotBlank() }.joinToString("\n\n")
            "https://www.linkedin.com/feed/?shareActive=true&text=${body.encoded()}"
        }

        LiveWebSite.Reddit -> {
            val target = if (url.isBlank()) "text=${text.encoded()}" else "url=${url.encoded()}"
            "https://www.reddit.com/submit?title=${text.lineSequence().first().take(REDDIT_TITLE_LIMIT).encoded()}&$target"
        }

        LiveWebSite.Instagram -> {
            null
        }
    }

private const val REDDIT_TITLE_LIMIT = 300

/** Walks through the chosen sites one at a time, each with its composer already filled in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveShareScreen(
    siteNames: List<String>,
    text: String,
    url: String,
    slidesSaved: Boolean,
    onBack: () -> Unit,
) {
    val sites = remember(siteNames) { siteNames.mapNotNull { name -> LiveWebSite.fromName(name) } }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val site = sites.getOrNull(index)
    if (site == null) {
        DisposableEffect(Unit) {
            onBack()
            onDispose { }
        }
        return
    }
    val context = LocalContext.current
    val slidesHint = stringResource(R.string.live_share_slides_hint)
    DisposableEffect(slidesSaved) {
        if (slidesSaved) Toast.makeText(context, slidesHint, Toast.LENGTH_LONG).show()
        onDispose { }
    }
    CruxScaffold(
        topBar = {
            CruxTopAppBar(
                title = { Text(text = stringResource(R.string.live_share_step, site.label, index + 1, sites.size)) },
                navigationIcon = { BackButton(onBack = onBack) },
                actions = {
                    TextButton(onClick = { index += 1 }) {
                        Text(
                            text =
                                stringResource(
                                    if (index == sites.lastIndex) R.string.live_share_done else R.string.live_share_next,
                                ),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        // A fresh page and view per site, so each composer opens with this post's text.
        key(site) {
            val page =
                remember {
                    LiveWebPage(site, context, startUrl = site.shareUrl(text, url) ?: site.homeUrl)
                }
            BindFileChooser(page)
            WebSessionLifecycleSuspender(webView = page.webView, isVisible = true)
            val webBackgroundColor = MaterialTheme.colorScheme.background.toArgb()
            LaunchedEffect(page, webBackgroundColor) {
                page.webView.setBackgroundColor(webBackgroundColor)
            }
            BackHandler(enabled = page.customVideoView != null || page.canGoBack) {
                if (page.customVideoView != null) {
                    page.exitFullscreenVideo()
                } else {
                    page.goBack()
                }
            }
            val instagramHint = stringResource(R.string.live_share_instagram_hint)
            LaunchedEffect(site, text) {
                if (site == LiveWebSite.Instagram) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Crux Caption", text))
                    Toast
                        .makeText(
                            context,
                            instagramHint,
                            Toast.LENGTH_LONG,
                        ).show()
                }
            }
            DisposableEffect(page) {
                onDispose { page.destroy() }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .imePadding(),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        FrameLayout(viewContext).apply {
                            addView(
                                page.webView,
                                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                            )
                        }
                    },
                    onRelease = { it.removeAllViews() },
                )
                AnimatedVisibility(
                    visible = page.isLoading && page.loadError == null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                ) {
                    LinearProgressIndicator(
                        progress = { (page.loadProgress / 100f).coerceIn(0.08f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
                if (page.loadError != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PlatformBadge(platform = CruxPlatform.forSite(site), size = 56.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.crux_web_error_title, site.label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.crux_web_error_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            FilledTonalButton(onClick = { page.webView.reload() }) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.RotateRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.crux_web_error_retry))
                            }
                        }
                    }
                }
                FullscreenWebVideoOverlay(page = page)
            }
        }
    }
}

/**
 * A platform's real site on its own page, used to sign in or browse outside the home pills.
 * Shares the site's isolated cookie jar with its home tab, which is reloaded on the way out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveWebScreen(
    siteName: String,
    onBack: () -> Unit,
    initialUrl: String? = null,
    forSignIn: Boolean = false,
) {
    val site = remember(siteName) { LiveWebSite.fromName(siteName) }
    if (site == null) {
        DisposableEffect(Unit) {
            onBack()
            onDispose { }
        }
        return
    }
    val context = LocalContext.current
    val pages = LocalLiveWebPages.current
    val connections = LocalCruxConnections.current
    val customUrl = initialUrl?.takeIf { it.isNotBlank() }
    val startUrl =
        remember(site, customUrl, forSignIn) {
            when {
                customUrl != null -> customUrl
                forSignIn && !site.hasSession() -> site.loginUrl
                else -> site.startUrlForSession()
            }
        }
    val autoReturnOnSignIn =
        remember(site, customUrl, forSignIn) {
            forSignIn || (customUrl != null && LiveWebSessionState.isAuthOrLoginUrl(customUrl) && !site.hasSession())
        }
    key(site, startUrl) {
        val ownsPage = (customUrl != null && !forSignIn) || pages == null
        var crashGeneration by remember(site, startUrl) { mutableIntStateOf(0) }
        val page =
            remember(site, startUrl, pages, crashGeneration) {
                if (!ownsPage && pages != null) {
                    pages.page(site, context)
                } else {
                    LiveWebPage(site, context, startUrl = startUrl)
                }
            }
        LaunchedEffect(page.rendererCrashed) {
            if (page.rendererCrashed) {
                if (!ownsPage && pages != null) {
                    pages.evictCrashed(site)
                } else {
                    page.destroy()
                }
                crashGeneration += 1
            }
        }
        LaunchedEffect(page, site, forSignIn) {
            if (forSignIn && !site.hasSession()) {
                val current = page.webView.url
                if (!LiveWebSessionState.isAuthOrLoginUrl(current) || isExternalOAuthUrl(current)) {
                    page.webView.loadUrl(site.loginUrl)
                }
            }
        }
        LaunchedEffect(site, page, autoReturnOnSignIn) {
            if (!autoReturnOnSignIn) return@LaunchedEffect
            while (true) {
                site.cookieManager().flush()
                if (site.hasSession()) {
                    delay(350L)
                    if (!ownsPage) {
                        page.webView.loadUrl(site.homeUrl)
                    } else {
                        pages?.reload(site)
                    }
                    connections?.refresh()
                    Toast.makeText(context, "Signed in to ${site.label}", Toast.LENGTH_SHORT).show()
                    onBack()
                    break
                }
                delay(300L)
            }
        }
        var pendingBookmarkPost by remember { mutableStateOf<ExtractedSocialPost?>(null) }
        DisposableEffect(page, site) {
            val trigger = {
                extractPostFromWebView(site, page.webView) { extracted ->
                    pendingBookmarkPost = extracted
                }
            }
            page.onNativeBookmarkTap = trigger
            onDispose {
                if (page.onNativeBookmarkTap === trigger) page.onNativeBookmarkTap = null
            }
        }
        pendingBookmarkPost?.let { postToSave ->
            SaveToBookmarkFolderSheet(
                post = postToSave,
                onDismiss = { pendingBookmarkPost = null },
            )
        }
        CruxScaffold(
            topBar = {
                CruxTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PlatformBadge(platform = CruxPlatform.forSite(site), size = 28.dp)
                            Column {
                                Text(
                                    text = if (autoReturnOnSignIn) "Sign in to ${site.label}" else site.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subtitle =
                                    if (autoReturnOnSignIn) {
                                        "Returns automatically once signed in"
                                    } else {
                                        formatCompactHostPath(page.currentUrl)
                                    }
                                if (subtitle != null) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        BackButton(
                            onBack = {
                                if (page.customVideoView != null) {
                                    page.exitFullscreenVideo()
                                } else if (isExternalOAuthUrl(page.webView.url)) {
                                    page.goBack()
                                } else {
                                    onBack()
                                }
                            },
                        )
                    },
                    actions = {
                        if (autoReturnOnSignIn) {
                            IconButton(onClick = { page.webView.reload() }) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.RotateRight,
                                    contentDescription = stringResource(R.string.web_nav_reload),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    extractPostFromWebView(site, page.webView) { extracted ->
                                        pendingBookmarkPost = extracted
                                    }
                                },
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Bookmark,
                                    contentDescription = "Save to Bookmark Folder",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                )
            },
        ) { contentPadding ->
            BindFileChooser(page)
            WebSessionLifecycleSuspender(webView = page.webView, isVisible = true)
            val webBackgroundColor = MaterialTheme.colorScheme.background.toArgb()
            LaunchedEffect(page, webBackgroundColor) {
                page.webView.setBackgroundColor(webBackgroundColor)
            }
            BackHandler(enabled = page.customVideoView != null || page.canGoBack) {
                if (page.customVideoView != null) {
                    page.exitFullscreenVideo()
                } else {
                    page.goBack()
                }
            }
            DisposableEffect(page, ownsPage) {
                onDispose {
                    (page.webView.parent as? ViewGroup)?.removeView(page.webView)
                    if (ownsPage) {
                        page.destroy()
                        pages?.reload(site)
                    } else if (site.hasSession() && LiveWebSessionState.isAuthOrLoginUrl(page.webView.url)) {
                        page.webView.loadUrl(site.homeUrl)
                    } else if (!site.hasSession() && isExternalOAuthUrl(page.webView.url)) {
                        page.webView.loadUrl(site.startUrlForSession())
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .imePadding(),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        FrameLayout(viewContext).apply {
                            (page.webView.parent as? ViewGroup)?.removeView(page.webView)
                            addView(
                                page.webView,
                                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                            )
                        }
                    },
                    onRelease = { it.removeAllViews() },
                )
                AnimatedVisibility(
                    visible = page.isLoading && page.loadError == null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                ) {
                    LinearProgressIndicator(
                        progress = { (page.loadProgress / 100f).coerceIn(0.08f, 1f) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                    )
                }
                AnimatedVisibility(
                    visible = !page.hasLoadedOnce && page.isLoading && page.loadProgress < 75 && page.loadError == null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PlatformBadge(platform = CruxPlatform.forSite(site), size = 56.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Opening ${site.label}…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (page.loadError != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PlatformBadge(platform = CruxPlatform.forSite(site), size = 56.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.crux_web_error_title, site.label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.crux_web_error_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            FilledTonalButton(onClick = { page.webView.reload() }) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.RotateRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.crux_web_error_retry))
                            }
                        }
                    }
                }
                FullscreenWebVideoOverlay(page = page)
            }
        }
    }
}
