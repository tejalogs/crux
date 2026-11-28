package org.crux.ui.screen.rss

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowRight
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Font
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.common.OnDeviceAI
import org.crux.data.readinglist.ReadingListRepository
import org.crux.data.repository.SettingsRepository
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.rss.RssArticleFailureReason
import org.crux.rss.RssDocument
import org.crux.rss.RssDocumentSource
import org.crux.rss.RssNetworkClient
import org.crux.rss.RssRepository
import org.crux.rss.rssArticleFailureReason
import org.crux.rss.rssArticleHttpStatus
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.FAIcon
import org.crux.ui.component.RssRichText
import org.crux.ui.component.listCard
import org.crux.ui.component.placeholder
import org.crux.ui.model.UiState
import org.crux.ui.model.isSuccess
import org.crux.ui.model.onError
import org.crux.ui.model.onLoading
import org.crux.ui.model.onSuccess
import org.crux.ui.render.parseHtml
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
import org.crux.ui.screen.home.ExtractedSocialPost
import org.crux.ui.screen.home.SaveToBookmarkFolderSheet
import org.crux.ui.screen.home.openInSystemBrowser
import org.crux.ui.theme.isLightTheme
import org.crux.ui.theme.screenHorizontalPadding
import org.koin.compose.koinInject
import java.net.URI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val PAGE_TURN_MILLIS = 160
private const val PAGE_ZONE = 0.3f
private const val PAGE_SWIPE = 0.12f
private val PAGE_HEADER_HEIGHT = 72.dp
private val PAGE_FOOTER_HEIGHT = 36.dp
private val PAGE_EDGE_FADE = 28.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RssDetailScreen(
    url: String,
    onBack: () -> Unit,
    descriptionHtml: String? = null,
    descriptionTitle: String? = null,
    fallbackHtml: String? = null,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val rssRepo = remember(context) { RssRepository.get(context) }
    val readingListRepo = koinInject<ReadingListRepository>()
    val settingsRepository = koinInject<SettingsRepository>()
    val onDeviceAI = koinInject<OnDeviceAI>()
    val isSavedState by remember(url) { readingListRepo.isSaved(url) }.collectAsState(false)
    val enableTldr by remember(settingsRepository) {
        settingsRepository.appSettings.map { it.aiConfig.tldr }
    }.collectAsState(initial = true)

    var reloadKey by remember(url) { mutableIntStateOf(0) }
    var docState by remember(url) { mutableStateOf<UiState<RssDocument>>(UiState.Loading()) }
    var showTldr by remember(url) { mutableStateOf(false) }
    var tldrRefreshKey by remember(url) { mutableIntStateOf(0) }
    var tldrState by remember(url) { mutableStateOf<UiState<String>?>(null) }

    LaunchedEffect(url, descriptionHtml, descriptionTitle, fallbackHtml, reloadKey) {
        docState = UiState.Loading()
        docState =
            try {
                val doc =
                    rssRepo.loadArticleDocument(
                        url = url,
                        descriptionHtml = descriptionHtml,
                        title = descriptionTitle,
                        fallbackHtml = fallbackHtml,
                        forceRefresh = reloadKey > 0,
                    )
                UiState.Success(doc)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
    }

    val loadedDoc = (docState as? UiState.Success)?.data
    val languageTag = Locale.current.toLanguageTag()
    LaunchedEffect(showTldr, loadedDoc, tldrRefreshKey) {
        if (!showTldr || loadedDoc == null) return@LaunchedEffect
        tldrState = UiState.Loading()
        tldrState =
            try {
                val sourceText =
                    buildString {
                        if (loadedDoc.title.isNotBlank()) appendLine(loadedDoc.title)
                        append(loadedDoc.textContent.take(6_000))
                    }.trim()
                val summary =
                    onDeviceAI.tldr(
                        source = sourceText,
                        targetLanguage = languageTag,
                        prompt = "",
                    )
                if (!summary.isNullOrBlank()) {
                    UiState.Success(summary)
                } else {
                    UiState.Error(IllegalStateException("Empty summary"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
    }

    val scope = rememberCoroutineScope()
    val readerSettings = rememberReaderSettings()
    val reader = readerSettings.value
    var showReaderSettings by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var pendingBookmarkPost by remember { mutableStateOf<ExtractedSocialPost?>(null) }
    val scrollState = rememberScrollState()
    val positions = remember(context) { ReaderPositions(context.applicationContext) }
    val hasContent = docState.isSuccess
    var restored by remember(url) { mutableStateOf(false) }
    // Images load after the text, so the scroll range keeps growing. Re-apply the saved place at each
    // size until the range holds still, unless the reader has already moved on their own.
    LaunchedEffect(url, hasContent) {
        if (!hasContent || restored) return@LaunchedEffect
        val saved = restoredReadingFraction(positions.get(url))
        if (saved <= 0f) {
            restored = true
            return@LaunchedEffect
        }
        var applied: Int? = null
        snapshotFlow { scrollState.maxValue }
            .filter { it > 0 }
            .collectLatest { max ->
                if (restored) return@collectLatest
                if (applied != null && scrollState.value != applied) {
                    restored = true
                    return@collectLatest
                }
                scrollState.scrollTo(scrollPositionFor(saved, max))
                applied = scrollState.value
                delay(READER_LAYOUT_SETTLE_MILLIS)
                restored = true
            }
    }
    DisposableEffect(url) {
        onDispose {
            if (restored && scrollState.maxValue > 0) {
                positions.save(url, scrollFractionOf(scrollState.value, scrollState.maxValue))
            }
        }
    }
    val color =
        reader.theme.background ?: if (isLightTheme()) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.background
        }
    val textColor = reader.theme.text ?: MaterialTheme.colorScheme.onSurface
    val paged = reader.mode == ReaderMode.Pages
    var showChrome by remember { mutableStateOf(true) }
    val bodyStyle = reader.bodyStyle(LocalTextStyle.current).copy(color = textColor)
    val lineHeightPx = with(LocalDensity.current) { bodyStyle.lineHeight.toPx() }
    val view = LocalView.current
    DisposableEffect(view, paged) {
        view.keepScreenOn = paged
        onDispose { view.keepScreenOn = false }
    }
    val turnPage: (Int) -> Unit = { direction ->
        showChrome = false
        scope.launch {
            val step = pageStep(scrollState.viewportSize, lineHeightPx)
            scrollState.animateScrollTo(scrollState.value + direction * step, tween(PAGE_TURN_MILLIS))
        }
    }
    if (showReaderSettings) {
        ReaderSettingsSheet(
            settings = reader,
            onChange = readerSettings::update,
            onDismiss = { showReaderSettings = false },
        )
    }
    pendingBookmarkPost?.let { postToSave ->
        SaveToBookmarkFolderSheet(
            post = postToSave,
            onDismiss = { pendingBookmarkPost = null },
        )
    }
    val readerBar: @Composable () -> Unit = {
        CruxTopAppBar(
            title = {},
            navigationIcon = {
                BackButton(onBack)
            },
            scrollBehavior = scrollBehavior,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = color,
                    scrolledContainerColor = color,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            actions = {
                val fallbackArticleTitle = stringResource(R.string.rss_detail_article_fallback_title)
                IconButton(
                    enabled = isSavedState || docState.isSuccess,
                    onClick = {
                        val doc = loadedDoc
                        val itemTitle =
                            descriptionTitle?.takeIf { it.isNotBlank() }
                                ?: doc?.title?.takeIf { it.isNotBlank() }
                                ?: fallbackArticleTitle
                        val itemContent =
                            doc?.textContent?.takeIf { it.isNotBlank() }
                                ?: doc?.contentHtml?.takeIf { it.isNotBlank() }
                                ?: itemTitle
                        val itemAuthor =
                            doc?.byline?.takeIf { it.isNotBlank() }
                                ?: doc?.siteName?.takeIf { it.isNotBlank() }
                                ?: runCatching { URI(url).host }.getOrNull().orEmpty()
                        pendingBookmarkPost =
                            ExtractedSocialPost(
                                id = url,
                                title = itemTitle,
                                authorName = itemAuthor,
                                authorHandle = "rss",
                                authorAvatarUrl = null,
                                contentText = itemContent,
                                sourceUrl = url,
                                platformId = "rss",
                                mediaUrls = emptyList(),
                            )
                    },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Bookmark,
                        contentDescription = if (isSavedState) "Edit Bookmark Folder" else "Save to Bookmark Folder",
                        tint = if (isSavedState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = { showReaderSettings = true }) {
                    FAIcon(
                        FontAwesomeIcons.Solid.Font,
                        contentDescription = stringResource(R.string.reader_settings),
                        modifier = Modifier.size(18.dp),
                    )
                }
                AnimatedVisibility(docState.isSuccess && enableTldr) {
                    TextButton(
                        onClick = { showTldr = true },
                    ) {
                        Text(stringResource(R.string.rss_detail_tldr))
                    }
                }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        FAIcon(
                            FontAwesomeIcons.Solid.EllipsisVertical,
                            contentDescription = stringResource(R.string.reader_more),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        if (loadedDoc != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.live_share_post_article)) },
                                onClick = {
                                    showMoreMenu = false
                                    uriHandler.openUri(
                                        DeeplinkRoute.Status
                                            .OmniScribe(
                                                statusKey = MicroBlogKey(url.hashCode().toString(), "rss"),
                                                accountType = AccountType.Guest,
                                                initialText = loadedDoc.textContent.ifBlank { loadedDoc.contentHtml },
                                                authorName = loadedDoc.byline ?: loadedDoc.siteName.orEmpty(),
                                                authorHandle = "rss",
                                                articleTitle = loadedDoc.title,
                                                sourceUrl = url,
                                            ).toUri(),
                                    )
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rss_detail_share)) },
                            onClick = {
                                showMoreMenu = false
                                val title =
                                    descriptionTitle?.takeIf { it.isNotBlank() }
                                        ?: loadedDoc?.title?.takeIf { it.isNotBlank() }
                                val sendIntent =
                                    Intent().apply {
                                        action = Intent.ACTION_SEND
                                        title?.let {
                                            putExtra(Intent.EXTRA_TITLE, it)
                                            putExtra(Intent.EXTRA_SUBJECT, it)
                                        }
                                        putExtra(Intent.EXTRA_TEXT, url)
                                        type = "text/plain"
                                    }
                                context.startActivity(Intent.createChooser(sendIntent, title))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rss_detail_open_in_browser)) },
                            onClick = {
                                showMoreMenu = false
                                openInSystemBrowser(context, url)
                            },
                        )
                    }
                }
            },
        )
    }
    CruxScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = color,
        topBar = {
            if (!paged) readerBar()
        },
    ) { contentPadding ->
        CompositionLocalProvider(LocalContentColor provides textColor) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (paged) {
                                Modifier
                                    .pointerInput(lineHeightPx) {
                                        detectTapGestures { offset ->
                                            when {
                                                offset.x < size.width * PAGE_ZONE -> turnPage(-1)
                                                offset.x > size.width * (1 - PAGE_ZONE) -> turnPage(1)
                                                else -> showChrome = !showChrome
                                            }
                                        }
                                    }.pointerInput(lineHeightPx) {
                                        var dragged = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = { dragged = 0f },
                                            onDragEnd = {
                                                if (abs(dragged) > size.width * PAGE_SWIPE) turnPage(if (dragged < 0) 1 else -1)
                                            },
                                            onHorizontalDrag = { _, amount -> dragged += amount },
                                        )
                                    }
                            } else {
                                Modifier
                            },
                        ),
            ) {
                if (!paged && scrollState.maxValue > 0) {
                    LinearProgressIndicator(
                        progress = { scrollState.value / scrollState.maxValue.toFloat() },
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = contentPadding.calculateTopPadding())
                                .fillMaxWidth(),
                        drawStopIndicator = {},
                    )
                }
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (paged) {
                                    Modifier.padding(
                                        top = contentPadding.calculateTopPadding() + PAGE_HEADER_HEIGHT,
                                        bottom = contentPadding.calculateBottomPadding() + PAGE_FOOTER_HEIGHT,
                                    )
                                } else {
                                    Modifier
                                },
                            ).verticalScroll(scrollState, enabled = !paged),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .widthIn(max = 600.dp)
                                .padding(if (paged) PaddingValues() else contentPadding)
                                .padding(horizontal = screenHorizontalPadding + if (paged) 8.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        docState
                            .onSuccess { documentData ->
                                val displayTitle = descriptionTitle?.takeIf { it.isNotBlank() } ?: documentData.title
                                MaybeSelectable(enabled = !paged) {
                                    Text(
                                        text = displayTitle,
                                        style =
                                            MaterialTheme.typography.headlineMedium.copy(
                                                fontFamily = reader.font.family,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                    )
                                }
                                if (documentData.siteName != null || documentData.byline != null || documentData.publishedAt != null) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        documentData.siteName?.let { site ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                RssFeedFavicon(
                                                    iconUrl = remember(url) { RssNetworkClient.defaultFaviconFor(url) },
                                                    size = 16.dp,
                                                )
                                                Text(
                                                    text = site,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            documentData.byline?.let {
                                                Text(
                                                    text = it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }

                                            Text(
                                                text = stringResource(R.string.reader_minutes, readingMinutes(documentData.textContent)),
                                                style = MaterialTheme.typography.bodySmall,
                                            )

                                            Spacer(modifier = Modifier.weight(1f))

                                            documentData.publishedAt?.let { ts ->
                                                val formatted = remember(ts) { formatFullDateTime(ts) }
                                                if (formatted.isNotEmpty()) {
                                                    Text(
                                                        text = formatted,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (documentData.source == RssDocumentSource.FeedSummary) {
                                    FeedSummaryBanner(
                                        onTryFullArticle = { reloadKey++ },
                                        onOpenOriginal = { openInSystemBrowser(context, url) },
                                    )
                                }
                            }.onError { error ->
                                ArticleErrorState(
                                    message = articleErrorMessage(error),
                                    onRetry = { reloadKey++ },
                                    onOpenOriginal = { openInSystemBrowser(context, url) },
                                )
                            }.onLoading {
                                val loadingTitle = descriptionTitle?.takeIf { it.isNotBlank() }
                                if (loadingTitle != null) {
                                    Text(
                                        text = loadingTitle,
                                        style =
                                            MaterialTheme.typography.headlineMedium.copy(
                                                fontFamily = reader.font.family,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                    )
                                } else {
                                    Text(
                                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                                        modifier = Modifier.placeholder(true),
                                    )
                                }
                            }
                        HorizontalDivider()
                        docState
                            .onSuccess { data ->
                                AnimatedVisibility(showTldr) {
                                    tldrState?.let { currentTldr ->
                                        Column(
                                            modifier =
                                                Modifier
                                                    .animateContentSize()
                                                    .fillMaxWidth()
                                                    .listCard()
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .padding(8.dp)
                                                    .clickable {
                                                        currentTldr.onError {
                                                            tldrRefreshKey++
                                                        }
                                                    },
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            AnimatedContent(currentTldr) {
                                                it
                                                    .onSuccess { summary ->
                                                        Text(
                                                            text = summary,
                                                            modifier =
                                                                Modifier
                                                                    .padding(
                                                                        horizontal = screenHorizontalPadding,
                                                                        vertical = 8.dp,
                                                                    ),
                                                        )
                                                    }.onLoading {
                                                        LinearWavyProgressIndicator(
                                                            modifier =
                                                                Modifier
                                                                    .padding(
                                                                        horizontal = screenHorizontalPadding,
                                                                        vertical = 8.dp,
                                                                    ),
                                                        )
                                                    }.onError {
                                                        Text(
                                                            text = stringResource(R.string.rss_detail_tldr_error),
                                                            color = MaterialTheme.colorScheme.error,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            modifier =
                                                                Modifier
                                                                    .padding(
                                                                        horizontal = screenHorizontalPadding,
                                                                        vertical = 8.dp,
                                                                    ),
                                                        )
                                                    }
                                            }
                                        }
                                    }
                                }
                                MaybeSelectable(enabled = !paged) {
                                    RssRichText(
                                        element = remember(data.contentHtml, url) { parseHtml(data.contentHtml, baseUri = url) },
                                        textStyle = bodyStyle,
                                    )
                                }
                                if (data.textContent.isNotBlank()) {
                                    Surface(
                                        onClick = {
                                            uriHandler.openUri(
                                                DeeplinkRoute.Status
                                                    .OmniScribe(
                                                        statusKey = MicroBlogKey(url.hashCode().toString(), "rss"),
                                                        accountType = AccountType.Guest,
                                                        initialText = data.textContent,
                                                        authorName = data.byline ?: data.siteName.orEmpty(),
                                                        authorHandle = "rss",
                                                        articleTitle = data.title,
                                                        sourceUrl = url,
                                                    ).toUri(),
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    ) {
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .size(32.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    FAIcon(
                                                        FontAwesomeIcons.Solid.WandMagicSparkles,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = stringResource(R.string.rss_create_post_title),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.rss_create_post_description),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 11.sp,
                                                    )
                                                }
                                            }
                                            FAIcon(
                                                FontAwesomeIcons.Solid.ArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp),
                                            )
                                        }
                                    }
                                }
                            }.onLoading {
                                LoadingArticleBody(
                                    previewHtml = fallbackHtml,
                                    url = url,
                                    textStyle = bodyStyle,
                                )
                            }
                    }
                }
                if (paged) {
                    val edgeHeight = PAGE_EDGE_FADE
                    if (scrollState.value > 0) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = contentPadding.calculateTopPadding() + PAGE_HEADER_HEIGHT)
                                    .fillMaxWidth()
                                    .height(edgeHeight)
                                    .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0f)))),
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = contentPadding.calculateBottomPadding() + PAGE_FOOTER_HEIGHT)
                                .fillMaxWidth()
                                .height(edgeHeight)
                                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0f), color))),
                    )
                    val fraction = if (scrollState.maxValue > 0) scrollState.value / scrollState.maxValue.toFloat() else 0f
                    val totalMinutes = readingMinutes(loadedDoc?.textContent.orEmpty())
                    Text(
                        text =
                            stringResource(
                                R.string.reader_page_footer,
                                (fraction * 100).roundToInt(),
                                ceil(totalMinutes * (1 - fraction)).toInt(),
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.6f),
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp),
                    )
                    AnimatedVisibility(
                        visible = showChrome,
                        modifier = Modifier.align(Alignment.TopCenter),
                        enter = slideInVertically { -it },
                        exit = slideOutVertically { -it },
                    ) {
                        readerBar()
                    }
                }
            }
        }
    }
}

@Composable
private fun MaybeSelectable(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        SelectionContainer(content = content)
    } else {
        content()
    }
}

/** The string that explains [error] to the reader, in plain words. */
@Composable
private fun articleErrorMessage(error: Throwable): String {
    val reason = error.rssArticleFailureReason()
    val status = error.rssArticleHttpStatus()
    return if (reason == RssArticleFailureReason.HttpError && status != null) {
        stringResource(R.string.rss_detail_error_http, status)
    } else {
        stringResource(articleErrorMessageRes(reason))
    }
}

/** The message for [reason]; an HTTP error without a known status reads as the generic message. */
internal fun articleErrorMessageRes(reason: RssArticleFailureReason): Int =
    when (reason) {
        RssArticleFailureReason.Unreachable -> R.string.rss_detail_error_unreachable
        RssArticleFailureReason.TimedOut -> R.string.rss_detail_error_timeout
        RssArticleFailureReason.NoArticle -> R.string.rss_detail_error_no_article
        RssArticleFailureReason.HttpError,
        RssArticleFailureReason.Other,
        -> R.string.rss_detail_unavailable
    }

/** Says that the full article could not be read and the feed's summary is shown in its place. */
@Composable
private fun FeedSummaryBanner(
    onTryFullArticle: () -> Unit,
    onOpenOriginal: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp)) {
            Text(
                text = stringResource(R.string.rss_detail_feed_summary),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onTryFullArticle) {
                    Text(stringResource(R.string.rss_detail_try_full_article))
                }
                TextButton(onClick = onOpenOriginal) {
                    Text(stringResource(R.string.rss_detail_open_original))
                }
            }
        }
    }
}

/** Shown when the article and the feed's summary both failed: why, and what to do next. */
@Composable
private fun ArticleErrorState(
    message: String,
    onRetry: () -> Unit,
    onOpenOriginal: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onRetry) {
                Text(stringResource(R.string.rss_detail_retry))
            }
            OutlinedButton(onClick = onOpenOriginal) {
                Text(stringResource(R.string.rss_detail_open_original))
            }
        }
    }
}

/**
 * While the article loads, the feed's own text for it is shown at once, under a thin progress
 * bar, rather than a block of placeholder text. Without feed text the placeholder stays.
 */
@Composable
private fun LoadingArticleBody(
    previewHtml: String?,
    url: String,
    textStyle: TextStyle,
) {
    val preview =
        remember(previewHtml, url) {
            previewHtml
                ?.takeIf { it.isNotBlank() }
                ?.let { parseHtml(it, baseUri = url) }
                ?.takeIf { it.text().isNotBlank() }
        }
    if (preview != null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            RssRichText(
                element = preview,
                textStyle = textStyle,
            )
        }
    } else {
        Text(
            LOADING_PLACEHOLDER_TEXT,
            modifier = Modifier.placeholder(true),
        )
    }
}

private const val LOADING_PLACEHOLDER_TEXT =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nam elementum eget dui a bibendum. Fusce eget porttitor est, " +
        "et rhoncus massa. Etiam cursus urna at odio vulputate semper. Interdum et malesuada fames ac ante ipsum primis in " +
        "faucibus. Quisque tincidunt rhoncus massa sed volutpat. Nulla porta orci et finibus accumsan. Duis maximus diam quis " +
        "congue suscipit. Suspendisse velit enim, mollis non tellus eu, auctor vulputate diam. Sed ut purus eleifend, tempor " +
        "lectus ac, imperdiet tellus. Proin eleifend lorem ut risus gravida, id bibendum metus posuere."
