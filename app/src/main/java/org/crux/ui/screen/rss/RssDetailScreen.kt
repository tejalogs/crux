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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Chrome
import compose.icons.fontawesomeicons.solid.ArrowRight
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Font
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.ShareNodes
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import io.ktor.http.Url
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.common.OfflineMediaPrefetcher
import org.crux.common.encodeJson
import org.crux.data.network.rss.DocumentData
import org.crux.data.readinglist.ReadingListItem
import org.crux.data.readinglist.ReadingListRepository
import org.crux.data.repository.SettingsRepository
import org.crux.data.translation.TranslationDisclosure
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.DateTimeText
import org.crux.ui.component.FAIcon
import org.crux.ui.component.FavIcon
import org.crux.ui.component.RssRichText
import org.crux.ui.component.listCard
import org.crux.ui.component.placeholder
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.model.flatMap
import org.crux.ui.model.isSuccess
import org.crux.ui.model.onError
import org.crux.ui.model.onLoading
import org.crux.ui.model.onSuccess
import org.crux.ui.model.takeSuccessOr
import org.crux.ui.presenter.home.rss.RssDetailPresenter
import org.crux.ui.presenter.home.rss.RssDetailTranslatePresenter
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.server.AiTLDRPresenter
import org.crux.ui.render.parseHtml
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
import org.crux.ui.theme.isLightTheme
import org.crux.ui.theme.screenHorizontalPadding
import org.koin.compose.koinInject
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
    descriptionHtml: String? = null,
    descriptionTitle: String? = null,
    fallbackHtml: String? = null,
    onBack: () -> Unit,
    toTranslationSettings: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val state by producePresenter(url) { presenter(url, descriptionHtml, descriptionTitle, fallbackHtml) }
    val settingsRepository = koinInject<SettingsRepository>()
    val disclosureScope = rememberCoroutineScope()
    var disclosureDismissed by remember(url) { mutableStateOf(false) }
    state.disclosureDestination?.let { destination ->
        if (state.enableTranslate || (state.isAutoTranslate && !disclosureDismissed)) {
            TranslationDisclosureDialog(
                destination = destination,
                onContinue = {
                    disclosureScope.launch {
                        settingsRepository.updateAppSettings {
                            copy(translateConfig = TranslationDisclosure.acknowledge(translateConfig, destination))
                        }
                    }
                },
                onCancel = {
                    state.setEnableTranslate(false)
                    disclosureDismissed = true
                },
                onChangeProvider = {
                    state.setEnableTranslate(false)
                    disclosureDismissed = true
                    toTranslationSettings()
                },
            )
        }
    }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val readingListRepo = koinInject<ReadingListRepository>()
    val isSavedState by remember(url) { readingListRepo.isSaved(url) }.collectAsState(false)
    val prefetcher = remember { OfflineMediaPrefetcher(context) }
    val scope = rememberCoroutineScope()
    val readerSettings = rememberReaderSettings()
    val reader = readerSettings.value
    var showReaderSettings by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val positions = remember(context) { ReaderPositions(context.applicationContext) }
    val hasContent = state.data.isSuccess
    var restored by remember(url) { mutableStateOf(false) }
    // Images load after the text, so the scroll range keeps growing. Re-apply the saved place at each
    // size until the range holds still, unless the reader has already moved on their own.
    LaunchedEffect(url, hasContent) {
        if (!hasContent || restored) return@LaunchedEffect
        val saved = positions.get(url)
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
        // Like an e-reader, the screen stays on while you read.
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
                IconButton(
                    // Saving before the article loads would store a placeholder, so wait for it.
                    enabled = isSavedState || state.data.isSuccess,
                    onClick = {
                        scope.launch {
                            if (isSavedState) {
                                readingListRepo.remove(url)
                            } else {
                                val doc = (state.data as? UiState.Success)?.data ?: return@launch
                                val itemTitle =
                                    descriptionTitle?.takeIf { it.isNotBlank() }
                                        ?: doc.title.takeIf { it.isNotBlank() }
                                        ?: context.getString(R.string.rss_detail_article_fallback_title)
                                val itemContent =
                                    doc.textContent.takeIf { it.isNotBlank() } ?: doc.content.takeIf { it.isNotBlank() } ?: itemTitle
                                val itemAuthor =
                                    doc.byline?.takeIf { it.isNotBlank() } ?: doc.siteName?.takeIf { it.isNotBlank() }.orEmpty()

                                val item =
                                    ReadingListItem(
                                        id = url,
                                        title = itemTitle,
                                        authorName = itemAuthor,
                                        authorHandle = "rss",
                                        contentText = itemContent,
                                        sourceUrl = url,
                                        platformId = "rss",
                                        savedAtTimestamp = System.currentTimeMillis(),
                                    )
                                readingListRepo.save(item)
                                prefetcher.prefetchItem(item)
                            }
                        }
                    },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Bookmark,
                        contentDescription = if (isSavedState) "Remove from Reading List" else "Save to Reading List",
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
                AnimatedVisibility(state.data.isSuccess && state.enableTldr.takeSuccessOr(false)) {
                    TextButton(
                        onClick = {
                            state.setShowTldr(true)
                        },
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
                        if (state.data.isSuccess) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.live_share_post_article)) },
                                onClick = {
                                    showMoreMenu = false
                                    val doc = (state.data as? UiState.Success)?.data
                                    uriHandler.openUri(
                                        DeeplinkRoute.Status
                                            .OmniScribe(
                                                statusKey = MicroBlogKey(url.hashCode().toString(), "rss"),
                                                accountType = AccountType.Guest,
                                                initialText = doc?.textContent?.takeIf { it.isNotBlank() } ?: doc?.content.orEmpty(),
                                                authorName = doc?.byline ?: doc?.siteName.orEmpty(),
                                                authorHandle = "rss",
                                                articleTitle = doc?.title.orEmpty(),
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
                                        ?: (state.data as? UiState.Success)?.data?.title?.takeIf { it.isNotBlank() }
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
                                uriHandler.openUri(url)
                            },
                        )
                        if (state.data.isSuccess && !state.isAutoTranslate && !state.enableTranslate) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rss_detail_translate)) },
                                onClick = {
                                    showMoreMenu = false
                                    state.setEnableTranslate(true)
                                },
                            )
                        }
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
                        state.data
                            .onSuccess { documentData ->
                                // Show translated title if available, otherwise original
                                val rawTitle = descriptionTitle?.takeIf { it.isNotBlank() } ?: documentData.title
                                val displayTitle =
                                    state.translateState?.translatedTitle?.let { titleState ->
                                        when (titleState) {
                                            is UiState.Success -> titleState.data
                                            else -> rawTitle
                                        }
                                    } ?: rawTitle
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
                                if (documentData.siteName != null || documentData.byline != null || documentData.publishDateTime != null) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        documentData.siteName?.let {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                FavIcon(
                                                    host =
                                                        remember(url) {
                                                            runCatching { Url(url).host }.getOrDefault("")
                                                        },
                                                    size = 16.dp,
                                                )
                                                Text(
                                                    text = it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }

                                        Row(
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

                                            documentData.publishDateTime?.let {
                                                DateTimeText(
                                                    data = it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fullTime = true,
                                                )
                                            }
                                        }
                                    }
                                }
                            }.onError {
                                Text(stringResource(R.string.rss_detail_unavailable))
                            }.onLoading {
                                Text(
                                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                                    modifier =
                                        Modifier
                                            .placeholder(true),
                                )
                            }
                        HorizontalDivider()
                        state.data
                            .onSuccess { data ->
                                AnimatedVisibility(state.showTldr) {
                                    state.tldrState?.let { tldrState ->
                                        Column(
                                            modifier =
                                                Modifier
                                                    .animateContentSize()
                                                    .fillMaxWidth()
                                                    .listCard()
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .padding(8.dp)
                                                    .clickable {
                                                        tldrState.onError {
                                                            state.refreshTldr()
                                                        }
                                                    },
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            AnimatedContent(tldrState) {
                                                it
                                                    .onSuccess {
                                                        Text(
                                                            text = it,
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
                                // Show translation loading indicator
                                state.translateState?.translatedHtml?.onLoading {
                                    LinearWavyProgressIndicator(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth(),
                                    )
                                }
                                MaybeSelectable(enabled = !paged) {
                                    val displayData =
                                        state.translateState?.translatedHtml?.let { htmlState ->
                                            when (htmlState) {
                                                is UiState.Success -> data.copy(content = htmlState.data)
                                                else -> data
                                            }
                                        } ?: data
                                    RssRichText(
                                        element = remember(displayData.content, url) { parseHtml(displayData.content, baseUri = url) },
                                        imageHeader = state.headers,
                                        textStyle = bodyStyle,
                                    )
                                }
                                // Show translation error if any
                                state.translateState?.translatedHtml?.onError {
                                    Text(
                                        text = stringResource(R.string.rss_detail_translate_error),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier =
                                            Modifier
                                                .clickable { state.refreshTranslate() },
                                    )
                                }
                                if (data.textContent.isNotBlank()) {
                                    Surface(
                                        onClick = {
                                            val body = data.textContent
                                            uriHandler.openUri(
                                                DeeplinkRoute.Status
                                                    .OmniScribe(
                                                        statusKey = MicroBlogKey(url.hashCode().toString(), "rss"),
                                                        accountType = AccountType.Guest,
                                                        initialText = body,
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
                                Text(
                                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nam elementum eget dui a bibendum. Fusce eget porttitor est, et rhoncus massa. Etiam cursus urna at odio vulputate semper. Interdum et malesuada fames ac ante ipsum primis in faucibus. Quisque tincidunt rhoncus massa sed volutpat. Nulla porta orci et finibus accumsan. Duis maximus diam quis congue suscipit. Suspendisse velit enim, mollis non tellus eu, auctor vulputate diam. Sed ut purus eleifend, tempor lectus ac, imperdiet tellus. Proin eleifend lorem ut risus gravida, id bibendum metus posuere. Cras pretium tortor mi. Quisque ac congue urna. Morbi posuere ac orci vestibulum euismod. Maecenas venenatis, justo at aliquet venenatis, arcu mauris sodales ligula, a iaculis nulla eros at turpis. Quisque varius lobortis porttitor.",
                                    modifier =
                                        Modifier
                                            .placeholder(true),
                                )
                            }
                    }
                }
                if (paged) {
                    // Page edges can fall mid-line, so fade them into the paper.
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
                    val totalMinutes = readingMinutes((state.data as? UiState.Success)?.data?.textContent.orEmpty())
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

@Composable
private fun presenter(
    url: String,
    descriptionHtml: String? = null,
    descriptionTitle: String? = null,
    fallbackHtml: String? = null,
    settingsRepository: SettingsRepository = koinInject(),
) = run {
    val state =
        remember(url, descriptionHtml, descriptionTitle, fallbackHtml) {
            RssDetailPresenter(url, descriptionHtml, descriptionTitle, fallbackHtml)
        }.invoke()
    val headers =
        remember(url) {
            val host = runCatching { Url(url).host }.getOrDefault("")
            persistentMapOf(
                "Referer" to "https://$host/",
                "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Ubuntu Chromium/70.0.3538.77 Chrome/70.0.3538.77 Safari/537.36",
            )
        }
    val enableTldr by remember {
        settingsRepository.appSettings.map { it.aiConfig.tldr }
    }.collectAsUiState()
    val preTranslate by remember {
        settingsRepository.appSettings.map { it.translateConfig.preTranslate }
    }.collectAsUiState()
    // Null until the settings are read; nothing is translated before the one-time disclosure is accepted.
    val gate by remember {
        settingsRepository.appSettings.map { settings ->
            TranslationGate(
                destination = TranslationDisclosure.destination(settings)?.takeIf { TranslationDisclosure.isRequired(settings) },
            )
        }
    }.collectAsState(initial = null)
    var showTldr by remember { mutableStateOf(false) }
    var tldrRefreshKey by remember { mutableIntStateOf(0) }
    var enableTranslate by remember { mutableStateOf(false) }
    var translateRefreshKey by remember { mutableIntStateOf(0) }
    val tldrState =
        if (showTldr) {
            key(tldrRefreshKey, state.data) {
                state.data.flatMap {
                    AiTLDRPresenter(
                        it.encodeJson(DocumentData.serializer()),
                        Locale.current.toLanguageTag(),
                    ).invoke()
                }
            }
        } else {
            null
        }
    // Determine if translation should be active:
    // auto-translate (preTranslate=true) or manually triggered
    val isAutoTranslate = preTranslate is UiState.Success && (preTranslate as UiState.Success<Boolean>).data
    val shouldTranslate = (isAutoTranslate || enableTranslate) && gate?.destination == null && gate != null
    val translateState: RssDetailTranslatePresenter.State? =
        if (shouldTranslate) {
            when (val dataState = state.data) {
                is UiState.Success -> {
                    key(translateRefreshKey) {
                        remember(dataState.data.content, dataState.data.title) {
                            RssDetailTranslatePresenter(
                                htmlContent = dataState.data.content,
                                title = dataState.data.title,
                                targetLanguage = Locale.current.toLanguageTag(),
                            )
                        }.invoke()
                    }
                }

                else -> {
                    null
                }
            }
        } else {
            null
        }
    object : RssDetailPresenter.State by state {
        val headers = headers
        val enableTldr = enableTldr
        val preTranslate = preTranslate
        val isAutoTranslate = isAutoTranslate
        val tldrState = tldrState
        val showTldr = showTldr
        val translateState = translateState
        val enableTranslate = enableTranslate
        val disclosureDestination: TranslationDisclosure.Destination? = gate?.destination

        fun setShowTldr(value: Boolean) {
            showTldr = value
        }

        fun refreshTldr() {
            tldrRefreshKey++
        }

        fun setEnableTranslate(value: Boolean) {
            enableTranslate = value
        }

        fun refreshTranslate() {
            translateRefreshKey++
        }
    }
}

private data class TranslationGate(
    val destination: TranslationDisclosure.Destination?,
)

@Composable
internal fun TranslationDisclosureDialog(
    destination: TranslationDisclosure.Destination,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    onChangeProvider: (() -> Unit)?,
) {
    val provider =
        stringResource(
            when (destination.kind) {
                TranslationDisclosure.Kind.GoogleWeb -> R.string.translation_provider_google
                TranslationDisclosure.Kind.DeepL -> R.string.translation_provider_deepl
                TranslationDisclosure.Kind.GoogleCloud -> R.string.translation_provider_google_cloud
                TranslationDisclosure.Kind.LibreTranslate -> R.string.translation_provider_libre
                TranslationDisclosure.Kind.AiServer -> R.string.translation_provider_ai_server
            },
        )
    val label =
        destination.host
            ?.takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.translation_destination_with_host, provider, it) }
            ?: provider
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(R.string.translation_disclosure_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.translation_disclosure_message, label))
                if (onChangeProvider != null) {
                    TextButton(onClick = onChangeProvider) {
                        Text(text = stringResource(R.string.translation_disclosure_change_provider))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(text = stringResource(R.string.translation_disclosure_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}
