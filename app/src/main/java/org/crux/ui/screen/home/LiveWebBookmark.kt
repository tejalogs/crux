package org.crux.ui.screen.home

import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.brands.LinkedinIn
import compose.icons.fontawesomeicons.brands.RedditAlien
import compose.icons.fontawesomeicons.brands.XTwitter
import compose.icons.fontawesomeicons.solid.Bolt
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.Briefcase
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Fire
import compose.icons.fontawesomeicons.solid.Folder
import compose.icons.fontawesomeicons.solid.FolderPlus
import compose.icons.fontawesomeicons.solid.Heart
import compose.icons.fontawesomeicons.solid.LayerGroup
import compose.icons.fontawesomeicons.solid.Microchip
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Rss
import compose.icons.fontawesomeicons.solid.Star
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.crux.common.OfflineMediaPrefetcher
import org.crux.data.readinglist.BookmarkFolder
import org.crux.data.readinglist.DEFAULT_BOOKMARK_FOLDER_ID
import org.crux.data.readinglist.ReadingListItem
import org.crux.data.readinglist.ReadingListRepository
import org.crux.di.koinInject
import org.crux.ui.component.FAIcon

internal data class ExtractedSocialPost(
    val id: String,
    val title: String,
    val authorName: String,
    val authorHandle: String,
    val authorAvatarUrl: String?,
    val contentText: String,
    val sourceUrl: String,
    val platformId: String,
    val mediaUrls: List<String>,
)

internal fun LiveWebSite.bookmarkPlatformId(): String =
    when (this) {
        LiveWebSite.X -> "x"
        LiveWebSite.Instagram -> "instagram"
        LiveWebSite.LinkedIn -> "linkedin"
        LiveWebSite.Reddit -> "reddit"
    }

/**
 * Extracts the social media post closest to the viewport center on X, Instagram, LinkedIn, or Reddit,
 * including author name, handle, avatar, post text, media images, and canonical post URL.
 */
private val EXTRACT_BOOKMARK_POST_SCRIPT =
    """
    (function(){
      try {
        var host = (location.hostname || '').toLowerCase();
        var mid = window.innerHeight / 2;
        var sel = 'article[data-testid="tweet"],shreddit-post,div.feed-shared-update-v2,div[data-id*="activity"],article,[role="article"]';
        var candidates = document.querySelectorAll(sel);
        var best = null, bestDist = 1e9, latestTap = 0, now = Date.now();
        for (var i = 0; i < candidates.length; i++) {
          var el = candidates[i];
          var tapTs = Number(el.getAttribute('data-cx-tapped') || 0);
          if (tapTs > latestTap && (now - tapTs) < 6000) {
            latestTap = tapTs;
            best = el;
          }
        }
        if (!best) {
          for (var j = 0; j < candidates.length; j++) {
            var el2 = candidates[j];
            var r = el2.getBoundingClientRect();
            if (r.height < 48 || r.bottom < 40 || r.top > window.innerHeight - 40) continue;
            var d = Math.abs((r.top + r.bottom) / 2 - mid);
            if (d < bestDist) { bestDist = d; best = el2; }
          }
        }
        var root = best || document.querySelector('main') || document.body;
        var title = '';
        var authorName = '';
        var authorHandle = '';
        var avatar = '';
        var text = '';
        var link = location.href;
        var media = [];

        var shPost = (best && best.tagName && best.tagName.toLowerCase() === 'shreddit-post')
          ? best
          : (best && best.querySelector ? best.querySelector('shreddit-post') : null);
        if (shPost) {
          title = (shPost.getAttribute('post-title') || '').trim();
          var sub = (shPost.getAttribute('subreddit-prefixed-name') || '').trim();
          var auth = (shPost.getAttribute('author') || '').trim();
          authorName = sub || (auth ? 'u/' + auth : 'Reddit');
          authorHandle = auth ? ('u/' + auth) : sub;
          var pl = shPost.getAttribute('permalink') || shPost.getAttribute('content-href') || '';
          if (pl) link = new URL(pl, location.origin).href;
          var bodyEl = shPost.querySelector('[slot="text-body"], [id$="-post-rtjson-content"], shreddit-post-text-body, p');
          var bodyTxt = bodyEl ? (bodyEl.innerText || '').trim() : '';
          text = bodyTxt || title;
          var cHref = shPost.getAttribute('content-href') || '';
          if (/\.(jpg|jpeg|png|webp|gif)(\?|$)/i.test(cHref) || /i\.redd\.it|preview\.redd\.it/i.test(cHref)) {
            media.push(cHref);
          }
        } else if (host.indexOf('x.com') !== -1 || host.indexOf('twitter.com') !== -1) {
          var un = root.querySelector('[data-testid="User-Name"]');
          if (un) {
            var parts = (un.innerText || '').split('\n').map(function(s){return s.trim()}).filter(Boolean);
            if (parts.length > 0) authorName = parts[0];
            for (var p = 0; p < parts.length; p++) {
              if (parts[p].indexOf('@') === 0) { authorHandle = parts[p]; break; }
            }
          }
          var tt = root.querySelector('[data-testid="tweetText"]');
          text = tt ? (tt.innerText || '').trim() : '';
          var sl = root.querySelector('a[href*="/status/"]:has(time)') || root.querySelector('a[href*="/status/"]');
          if (sl && sl.href) link = sl.href.split('?')[0];
          var avImg = root.querySelector('[data-testid*="UserAvatar"] img, img[src*="profile_images"]');
          if (avImg && avImg.src) avatar = avImg.src;
        } else if (host.indexOf('instagram.com') !== -1) {
          var igPath = location.pathname || '';
          var igUserMatch = igPath.match(/^\/([a-zA-Z0-9._]+)\/(?:reel|p)\//);
          var hdrLinks = root.querySelectorAll('header a, a[role="link"]');
          for (var h = 0; h < hdrLinks.length; h++) {
            var ht = (hdrLinks[h].innerText || '').trim();
            if (ht && ht.length < 40 && ht.indexOf(' ') === -1 && !/^(Follow|Following|Audio|Original)$/i.test(ht)) {
              authorName = ht;
              authorHandle = '@' + ht.replace(/^@/, '');
              break;
            }
          }
          if (!authorName && igUserMatch && igUserMatch[1]) {
            authorName = igUserMatch[1];
            authorHandle = '@' + igUserMatch[1];
          }
          var onIgDetail = /^\/(?:p|reels?)\/|^\/[^\/]+\/(?:p|reel)\//i.test(igPath);
          if (onIgDetail) {
            link = location.href.split('?')[0];
          } else {
            var igLink = root.querySelector('a[href*="/p/"], a[href*="/reel/"]');
            if (igLink && igLink.href) link = igLink.href.split('?')[0];
          }
          var igAv = root.querySelector('header img');
          if (igAv && igAv.src) avatar = igAv.src;
          var spans = root.querySelectorAll('span[dir="auto"], h1');
          var longest = '';
          for (var s = 0; s < spans.length; s++) {
            var st = (spans[s].innerText || '').trim();
            if (st.length > longest.length && st !== authorName) longest = st;
          }
          text = longest;
        } else if (host.indexOf('linkedin.com') !== -1) {
          var actName = root.querySelector('.update-components-actor__title span[ aria-hidden="true" ], .update-components-actor__name, .feed-shared-actor__name, [data-tracking-control-name*="actor"]');
          if (actName) authorName = (actName.innerText || '').trim().split('\n')[0];
          var actSub = root.querySelector('.update-components-actor__description span[aria-hidden="true"], .update-components-actor__description, .feed-shared-actor__description');
          if (actSub) authorHandle = (actSub.innerText || '').trim().split('\n')[0];
          var liBody = root.querySelector('.update-components-text, .feed-shared-update-v2__description, .attributed-text-segment-list__content');
          if (liBody) text = (liBody.innerText || '').trim();
          var urn = best ? (best.getAttribute('data-urn') || best.getAttribute('data-id') || '') : '';
          if (urn && urn.indexOf('urn:li:') === 0) {
            link = 'https://www.linkedin.com/feed/update/' + urn + '/';
          } else {
            var liLink = root.querySelector('a[href*="/feed/update/"], a[href*="/posts/"]');
            if (liLink && liLink.href) link = liLink.href.split('?')[0];
          }
          var liAv = root.querySelector('.update-components-actor__avatar img, .feed-shared-actor__avatar img');
          if (liAv && liAv.src) avatar = liAv.src;
        }

        if (!text && best) {
          text = (best.innerText || '').replace(/\s+/g, ' ').trim();
        }
        if (!text) {
          text = (document.title || location.href).trim();
        }
        if (!title) {
          var firstLine = text.split('\n')[0].trim();
          title = firstLine.length > 90 ? firstLine.slice(0, 90) + '…' : firstLine;
        }

        var imgs = root.querySelectorAll('img[src^="https://"]');
        for (var m = 0; m < imgs.length && media.length < 6; m++) {
          var im = imgs[m];
          var src = im.src || '';
          if (!src || src === avatar || src.indexOf('profile_images') !== -1 || src.indexOf('emoji') !== -1) continue;
          var w = im.naturalWidth || im.width || im.clientWidth || 0;
          var h2 = im.naturalHeight || im.height || im.clientHeight || 0;
          if ((w >= 96 || h2 >= 96) && media.indexOf(src) === -1) {
            media.push(src);
          }
        }

        return JSON.stringify({
          title: title.slice(0, 140),
          authorName: (authorName || host.replace(/^www\./, '')).slice(0, 80),
          authorHandle: authorHandle.slice(0, 80),
          avatar: avatar,
          text: text.slice(0, 2000),
          url: link || location.href,
          media: media
        });
      } catch (e) {
        return JSON.stringify({
          title: document.title || '',
          authorName: location.hostname.replace(/^www\./, ''),
          authorHandle: '',
          avatar: '',
          text: document.title || location.href,
          url: location.href,
          media: []
        });
      }
    })();
    """.trimIndent()

internal fun extractPostFromWebView(
    site: LiveWebSite,
    webView: WebView,
    overrideImageUrl: String? = null,
    onExtracted: (ExtractedSocialPost) -> Unit,
) {
    webView.evaluateJavascript(EXTRACT_BOOKMARK_POST_SCRIPT) { raw ->
        val parsed = parseExtractedSocialPost(site, raw, webView.url ?: site.homeUrl, overrideImageUrl)
        onExtracted(parsed)
    }
}

private fun String?.takeIfWebUrl(): String? {
    val trimmed = this?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    return trimmed.takeIf { (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank() }
}

internal fun parseExtractedSocialPost(
    site: LiveWebSite,
    rawJsonResult: String?,
    fallbackUrl: String,
    overrideImageUrl: String? = null,
): ExtractedSocialPost {
    val safeFallbackUrl = fallbackUrl.takeIfWebUrl() ?: site.homeUrl
    val safeOverrideImage = overrideImageUrl.takeIfPublicImageUrl()
    val platformId = site.bookmarkPlatformId()
    val defaultAuthor =
        when (site) {
            LiveWebSite.X -> "X"
            LiveWebSite.Instagram -> "Instagram"
/**
 * Images and avatars are loaded later by the image loader, so a page could otherwise point them
 * at the phone's own network for tracking or probing. Social CDNs use HTTPS host names, never
 * raw IP addresses or local names.
 */
internal fun String?.takeIfPublicImageUrl(): String? {
    val url = takeIfWebUrl() ?: return null
    val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase()?.trimEnd('.') ?: return null
    val isIpLiteral = host.startsWith("[") || host.all { it.isDigit() || it == '.' }
    val isLocalName = host == "localhost" || !host.contains('.') || LOCAL_SUFFIXES.any { host.endsWith(it) }
    return url.takeIf { it.startsWith("https://", ignoreCase = true) && !isIpLiteral && !isLocalName }
}

private val LOCAL_SUFFIXES = listOf(".local", ".localhost", ".internal", ".home.arpa", ".lan")

            LiveWebSite.LinkedIn -> "LinkedIn"
            LiveWebSite.Reddit -> "Reddit"
        }
    return runCatching {
        val unwrapped =
            rawJsonResult
                ?.takeIf { it != "null" && it.isNotBlank() }
                ?.let { Json.parseToJsonElement(it).jsonPrimitive.contentOrNull }
                ?: throw IllegalArgumentException("Empty JS result")
        val obj = Json.parseToJsonElement(unwrapped).jsonObject
        val url = obj["url"]?.jsonPrimitive?.contentOrNull.takeIfWebUrl() ?: safeFallbackUrl
        val text = obj["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: url
        val title =
            obj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: text.lineSequence().firstOrNull().orEmpty().take(90)
        val authorName = obj["authorName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: defaultAuthor
        val authorHandle = obj["authorHandle"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val avatar = obj["avatar"]?.jsonPrimitive?.contentOrNull.takeIfPublicImageUrl()
        val mediaArr = obj["media"]?.jsonArray
        val mediaList =
            buildList {
                if (safeOverrideImage != null) add(safeOverrideImage)
                mediaArr?.forEach { elem ->
                    val m = elem.jsonPrimitive.contentOrNull.takeIfPublicImageUrl()
                    if (m != null && m !in this) add(m)
                }
            }
        ExtractedSocialPost(
            id = if (safeOverrideImage != null) "$url#$safeOverrideImage" else url,
            title = title,
            authorName = authorName,
            authorHandle = authorHandle,
            authorAvatarUrl = avatar,
            contentText = text,
            sourceUrl = url,
            platformId = platformId,
            mediaUrls = mediaList,
        )
    }.getOrElse {
        ExtractedSocialPost(
            id = safeOverrideImage ?: safeFallbackUrl,
            title = "$defaultAuthor Post",
            authorName = defaultAuthor,
            authorHandle = "",
            authorAvatarUrl = null,
            contentText = safeFallbackUrl,
            sourceUrl = safeFallbackUrl,
            platformId = platformId,
            mediaUrls = listOfNotNull(safeOverrideImage),
        )
    }
}

internal val BOOKMARK_FOLDER_ICONS: List<Pair<String, ImageVector>> =
    listOf(
        "bolt" to FontAwesomeIcons.Solid.Bolt,
        "sparkles" to FontAwesomeIcons.Solid.WandMagicSparkles,
        "microchip" to FontAwesomeIcons.Solid.Microchip,
        "layer_group" to FontAwesomeIcons.Solid.LayerGroup,
        "briefcase" to FontAwesomeIcons.Solid.Briefcase,
        "heart" to FontAwesomeIcons.Solid.Heart,
        "fire" to FontAwesomeIcons.Solid.Fire,
        "star" to FontAwesomeIcons.Solid.Star,
        "folder" to FontAwesomeIcons.Solid.Folder,
    )

internal val BOOKMARK_FOLDER_COLORS: List<Long> =
    listOf(
        0xFF6366F1L, // Indigo
        0xFFEC4899L, // Pink
        0xFF10B981L, // Emerald
        0xFFF59E0BL, // Amber
        0xFF0A66C2L, // Ocean Blue
        0xFF8B5CF6L, // Violet
        0xFFEF4444L, // Coral Red
        0xFF14B8A6L, // Teal
    )

internal fun folderIconForName(iconName: String): ImageVector =
    BOOKMARK_FOLDER_ICONS.firstOrNull { it.first == iconName }?.second ?: FontAwesomeIcons.Solid.Folder

@Composable
internal fun SocialPlatformBadge(
    platformId: String,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
) {
    val (icon, brush) =
        when (platformId.lowercase()) {
            "x", "twitter" -> {
                FontAwesomeIcons.Brands.XTwitter to
                    Brush.linearGradient(listOf(Color(0xFF1F2428), Color(0xFF0F1419)))
            }

            "instagram" -> {
                FontAwesomeIcons.Brands.Instagram to
                    Brush.linearGradient(listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045)))
            }

            "linkedin" -> {
                FontAwesomeIcons.Brands.LinkedinIn to
                    Brush.linearGradient(listOf(Color(0xFF0A66C2), Color(0xFF004182)))
            }

            "reddit" -> {
                FontAwesomeIcons.Brands.RedditAlien to
                    Brush.linearGradient(listOf(Color(0xFFFF4500), Color(0xFFFF6830)))
            }

            "rss" -> {
                FontAwesomeIcons.Solid.Rss to
                    Brush.linearGradient(listOf(Color(0xFFF26522), Color(0xFFFF9900)))
            }

            else -> {
                FontAwesomeIcons.Solid.Bookmark to
                    Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5)))
            }
        }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        FAIcon(
            imageVector = icon,
            contentDescription = platformId,
            tint = Color.White,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/** Author avatar with a corner platform mini-badge when an avatar exists, or the platform badge alone. */
@Composable
internal fun AuthorWithPlatformAvatar(
    avatarUrl: String?,
    platformId: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    if (avatarUrl.isNullOrBlank()) {
        SocialPlatformBadge(platformId = platformId, modifier = modifier, size = size)
        return
    }
    Box(modifier = modifier.size(size)) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        SocialPlatformBadge(
            platformId = platformId,
            size = (size * 0.48f).coerceAtLeast(14.dp),
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

internal fun platformDisplayName(platformId: String): String =
    when (platformId.lowercase()) {
        "x", "twitter" -> "X"
        "instagram" -> "Instagram"
        "linkedin" -> "LinkedIn"
        "reddit" -> "Reddit"
        "rss" -> "RSS Feed"
        else -> platformId.replaceFirstChar { it.uppercase() }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SaveToBookmarkFolderSheet(
    post: ExtractedSocialPost,
    onDismiss: () -> Unit,
    onSmartReply: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository by koinInject<ReadingListRepository>()
    val prefetcher = remember { OfflineMediaPrefetcher(context) }
    val folders by repository.folders.collectAsState()
    val items by repository.items.collectAsState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val noteSectionRequester = remember { BringIntoViewRequester() }
    var isNoteFocused by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible

    val existingItem = remember(items, post.id) { items.firstOrNull { it.id == post.id } }
    var selectedFolderId by remember(existingItem) {
        mutableStateOf(existingItem?.folderId ?: DEFAULT_BOOKMARK_FOLDER_ID)
    }
    var noteText by remember(existingItem) {
        mutableStateOf(existingItem?.note.orEmpty())
    }
    LaunchedEffect(isNoteFocused, isImeVisible, scrollState.maxValue, noteText.length) {
        if (isNoteFocused && isImeVisible) {
            noteSectionRequester.bringIntoView()
        }
    }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    if (showCreateFolderDialog) {
        CreateBookmarkFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name, iconName, colorHex ->
                scope.launch {
                    val created =
                        repository.createFolder(
                            name = name,
                            iconName = iconName,
                            colorHex = colorHex,
                            createdAtTimestamp = System.currentTimeMillis(),
                        )
                    selectedFolderId = created.id
                    showCreateFolderDialog = false
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { BottomSheetDefaults.modalWindowInsets.union(WindowInsets.ime) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column {
                        Text(
                            text = if (existingItem != null) "Saved in Universal Bookmarks" else "Save to Universal Bookmarks",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Organize across folders with offline copies",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Live Social Post Preview Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AuthorWithPlatformAvatar(
                            avatarUrl = post.authorAvatarUrl,
                            platformId = post.platformId,
                            size = 34.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = post.authorName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (post.authorHandle.isNotBlank() && post.authorHandle != post.authorName) {
                                Text(
                                    text = post.authorHandle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = platformDisplayName(post.platformId),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }

                    Text(
                        text = post.contentText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (post.mediaUrls.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(post.mediaUrls) { mediaUrl ->
                                AsyncImage(
                                    model = mediaUrl,
                                    contentDescription = null,
                                    modifier =
                                        Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }

            // Folder Picker Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Choose folder",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = { showCreateFolderDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.FolderPlus,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New folder", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Folder Cards Strip
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(folders, key = { it.id }) { folder ->
                    val isSelected = selectedFolderId == folder.id
                    val accentColor = Color(folder.colorHex)
                    val count = items.count { it.folderId == folder.id }
                    Surface(
                        onClick = { selectedFolderId = folder.id },
                        shape = RoundedCornerShape(16.dp),
                        color =
                            if (isSelected) {
                                accentColor.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        border =
                            BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(accentColor.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                FAIcon(
                                    imageVector = folderIconForName(folder.iconName),
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "$count saved",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Check,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(noteSectionRequester),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Optional personal note
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                isNoteFocused = focusState.isFocused
                                if (focusState.isFocused) {
                                    scope.launch { noteSectionRequester.bringIntoView() }
                                }
                            },
                    placeholder = { Text("Add a personal note or takeaway (optional)…") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                )

                // Primary Save CTA + Optional Smart Reply button
                val selectedFolderName =
                    folders.firstOrNull { it.id == selectedFolderId }?.name ?: "Quick Saves"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onSmartReply != null) {
                        FilledTonalButton(
                            onClick = {
                                onDismiss()
                                onSmartReply()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(50.dp),
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reply", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = {
                            val item =
                                ReadingListItem(
                                    id = post.id,
                                    title = post.title,
                                    authorName = post.authorName,
                                    authorHandle = post.authorHandle,
                                    authorAvatarUrl = post.authorAvatarUrl,
                                    contentText = post.contentText,
                                    sourceUrl = post.sourceUrl,
                                    platformId = post.platformId,
                                    savedAtTimestamp = System.currentTimeMillis(),
                                    mediaUrls = post.mediaUrls,
                                    folderId = selectedFolderId,
                                    note = noteText.trim(),
                                )
                            scope.launch {
                                repository.save(item)
                                prefetcher.prefetchItem(item)
                                Toast
                                    .makeText(
                                        context,
                                        "Saved to $selectedFolderName",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text =
                                if (existingItem != null) {
                                    "Update in $selectedFolderName"
                                } else {
                                    "Save to $selectedFolderName"
                                },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateBookmarkFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, iconName: String, colorHex: Long) -> Unit,
    initialName: String = "",
    initialIcon: String = "bolt",
    initialColor: Long = BOOKMARK_FOLDER_COLORS.first(),
    isEditing: Boolean = false,
) {
    var folderName by remember(initialName) { mutableStateOf(initialName) }
    var selectedIcon by remember(initialIcon) { mutableStateOf(initialIcon) }
    var selectedColor by remember(initialColor) { mutableLongStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Folder" else "New Bookmark Folder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    placeholder = { Text("e.g. Design Inspo, AI Research, Must Read") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BOOKMARK_FOLDER_COLORS.forEach { hex ->
                        val color = Color(hex)
                        val isSelected = selectedColor == hex
                        Box(
                            modifier =
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BOOKMARK_FOLDER_ICONS.forEach { (iconKey, iconVec) ->
                        val isSelected = selectedIcon == iconKey
                        val accent = Color(selectedColor)
                        Surface(
                            onClick = { selectedIcon = iconKey },
                            shape = RoundedCornerShape(10.dp),
                            color =
                                if (isSelected) {
                                    accent.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            border =
                                if (isSelected) {
                                    BorderStroke(1.5.dp, accent)
                                } else {
                                    null
                                },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                FAIcon(
                                    imageVector = iconVec,
                                    contentDescription = iconKey,
                                    tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isNotBlank()) {
                        onCreate(folderName.trim(), selectedIcon, selectedColor)
                    }
                },
                enabled = folderName.isNotBlank(),
            ) {
                if (!isEditing) {
                    FAIcon(imageVector = FontAwesomeIcons.Solid.Plus, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(if (isEditing) "Save" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
