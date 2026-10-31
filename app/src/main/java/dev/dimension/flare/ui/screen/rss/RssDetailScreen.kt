package dev.dimension.flare.ui.screen.rss

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
import dev.dimension.flare.R
import dev.dimension.flare.common.OfflineMediaPrefetcher
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.network.rss.DocumentData
import dev.dimension.flare.data.readinglist.ReadingListItem
import dev.dimension.flare.data.readinglist.ReadingListRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FavIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.RssRichText
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccessOr
import dev.dimension.flare.ui.presenter.home.rss.RssDetailPresenter
import dev.dimension.flare.ui.presenter.home.rss.RssDetailTranslatePresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.server.AiTLDRPresenter
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import dev.dimension.flare.ui.theme.isLightTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.ktor.http.Url
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val PAGE_TURN_MILLIS = 160
private const val PAGE_ZONE = 0.3f
private const val PAGE_SWIPE = 0.12f
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
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val state by producePresenter(url) { presenter(url, descriptionHtml, descriptionTitle, fallbackHtml) }
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
    LaunchedEffect(url, hasContent, scrollState.maxValue) {
        if (hasContent && !restored && scrollState.maxValue > 0) {
            scrollState.scrollTo(positions.get(url).coerceAtMost(scrollState.maxValue))
            restored = true
        }
    }
    DisposableEffect(url) {
        onDispose {
            if (restored) positions.save(url, scrollState.value)
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
    var showChrome by remember { mutableStateOf(false) }
    val bodyStyle = reader.bodyStyle(LocalTextStyle.current).copy(color = textColor)
    val lineHeightPx = with(LocalDensity.current) { bodyStyle.lineHeight.toPx() }
    val view = LocalView.current
    DisposableEffect(view, paged) {
        // Like an e-reader, the screen stays on while you read.
        view.keepScreenOn = paged
        onDispose { view.keepScreenOn = false }
    }
    val turnPage: (Int) -> Unit = { direction ->
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
        FlareTopAppBar(
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
                    onClick = {
                        scope.launch {
                            if (isSavedState) {
                                readingListRepo.remove(url)
                            } else {
                                val itemTitle =
                                    when (val data = state.data) {
                                        is UiState.Success -> data.data.title.takeIf { it.isNotBlank() }
                                        else -> null
                                    } ?: descriptionTitle.orEmpty().ifBlank { "RSS Article" }

                                val itemContent =
                                    when (val data = state.data) {
                                        is UiState.Success -> data.data.textContent.takeIf { it.isNotBlank() }
                                        else -> null
                                    } ?: "Saved from RSS"

                                val item =
                                    ReadingListItem(
                                        id = url,
                                        title = itemTitle,
                                        authorName = itemTitle,
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rss_detail_share)) },
                            onClick = {
                                showMoreMenu = false
                                val title =
                                    (state.data as? UiState.Success)?.data?.title?.takeIf { it.isNotBlank() }
                                        ?: descriptionTitle?.takeIf { it.isNotBlank() }
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
    FlareScaffold(
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
                                        top = contentPadding.calculateTopPadding() + 8.dp,
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
                                val displayTitle =
                                    state.translateState?.translatedTitle?.let { titleState ->
                                        when (titleState) {
                                            is UiState.Success -> titleState.data
                                            else -> documentData.title
                                        }
                                    } ?: documentData.title
                                SelectionContainer {
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
                                                        remember {
                                                            Url(url).host
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
                                SelectionContainer(
                                    modifier =
                                    Modifier,
                                ) {
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
                                Surface(
                                    onClick = {
                                        val body = data.textContent.takeIf { it.isNotBlank() } ?: data.content
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
                                                    text = "Synthesize Post & Carousel",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    text = "Distill key thesis, evidence & 4:5 slides with on-device Gemma",
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
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = contentPadding.calculateTopPadding() + 8.dp)
                                .fillMaxWidth()
                                .height(edgeHeight)
                                .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0f)))),
                    )
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
            persistentMapOf(
                "Referer" to "https://${Url(url).host}/",
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
    val shouldTranslate = isAutoTranslate || enableTranslate
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
