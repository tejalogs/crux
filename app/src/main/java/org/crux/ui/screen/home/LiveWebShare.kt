package org.crux.ui.screen.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import org.crux.R
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.screen.crux.CruxPlatform
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
            BackHandler(enabled = page.canGoBack) {
                page.goBack()
            }
            LaunchedEffect(site, text) {
                if (site == LiveWebSite.Instagram) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Crux Caption", text))
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.live_share_instagram_hint),
                            Toast.LENGTH_LONG,
                        ).show()
                }
            }
            DisposableEffect(page) {
                onDispose { page.destroy() }
            }
            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .imePadding(),
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
    CruxScaffold(
        topBar = {
            CruxTopAppBar(
                title = { Text(text = site.label) },
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
    ) { contentPadding ->
        val startUrl = initialUrl ?: site.homeUrl
        key(site, startUrl) {
            val page = remember { LiveWebPage(site, context, startUrl = startUrl) }
            BindFileChooser(page)
            WebSessionLifecycleSuspender(webView = page.webView, isVisible = true)
            BackHandler(enabled = page.canGoBack) {
                page.goBack()
            }
            DisposableEffect(page) {
                onDispose {
                    page.destroy()
                    pages?.reload(site)
                }
            }
            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .imePadding(),
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
        }
    }
}
