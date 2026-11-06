package org.crux.ui.screen.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.model.BottomBarBehavior
import org.crux.data.model.TimelineDisplayMode
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.component.RefreshContainer
import org.crux.ui.component.platform.isBigScreen
import org.crux.ui.component.platform.isCompatScreen
import org.crux.ui.component.status.LazyStatusVerticalStaggeredGrid
import org.crux.ui.component.status.status
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.takeSuccess
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.status.StatusContextPresenter
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
import org.crux.ui.theme.isLightTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StatusScreen(
    statusKey: MicroBlogKey,
    onBack: () -> Unit,
    accountType: AccountType,
) {
    val isBigScreen = isBigScreen()
    val state by producePresenter(statusKey.toString()) {
        statusPresenter(accountType = accountType, statusKey = statusKey)
    }
    val topAppBarScrollBehavior =
        if (LocalGlobalAppearance.current.bottomBarBehavior == BottomBarBehavior.AlwaysShow) {
            TopAppBarDefaults.pinnedScrollBehavior()
        } else {
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        }
    CruxScaffold(
        topBar = {
            CruxTopAppBar(
                colors =
                    when (LocalTimelineAppearance.current.timelineDisplayMode) {
                        TimelineDisplayMode.Plain if isLightTheme() && isCompatScreen() -> {
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                                actionIconContentColor = MaterialTheme.colorScheme.primary,
                            )
                        }

                        else -> {
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                scrolledContainerColor = MaterialTheme.colorScheme.background,
                                actionIconContentColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                scrollBehavior = topAppBarScrollBehavior,
                title = {
                    Text(
                        text = stringResource(id = R.string.status_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
        bottomBar = {
            val uriHandler = LocalUriHandler.current
            val currentPost = (state.state.current.takeSuccess() as? UiTimelineV2.TimelinePostItem)?.post
            val authorHandle = currentPost?.user?.handle?.canonical
            val replyPlaceholder =
                if (!authorHandle.isNullOrBlank()) {
                    stringResource(
                        id = R.string.status_reply_placeholder_user,
                        authorHandle.removePrefix("@"),
                    )
                } else {
                    stringResource(id = R.string.status_reply_placeholder_generic)
                }
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable {
                                    val replyUri =
                                        DeeplinkRoute.Compose
                                            .Reply(
                                                statusKey = statusKey,
                                                accountKey =
                                                    (accountType as? AccountType.Specific)?.accountKey
                                                        ?: MicroBlogKey(id = "", host = statusKey.host),
                                            ).toUri()
                                    uriHandler.openUri(replyUri)
                                }.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = replyPlaceholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = {
                            val rawText =
                                currentPost
                                    ?.content
                                    ?.original
                                    ?.raw
                                    .orEmpty()
                            val altText =
                                currentPost
                                    ?.images
                                    ?.mapNotNull {
                                        it.description?.takeIf { d ->
                                            d.isNotBlank()
                                        }
                                    }?.joinToString("\n")
                                    .orEmpty()
                            val smartReplyUri =
                                DeeplinkRoute.Status
                                    .SmartReply(
                                        statusKey = statusKey,
                                        accountType = accountType,
                                        initialText = rawText,
                                        authorName =
                                            currentPost
                                                ?.user
                                                ?.name
                                                ?.raw
                                                .orEmpty(),
                                        authorHandle =
                                            currentPost
                                                ?.user
                                                ?.handle
                                                ?.canonical
                                                .orEmpty(),
                                        mediaDescription = altText,
                                        parentContext = currentPost?.replyToHandle?.let { "Replying to @$it" }.orEmpty(),
                                    ).toUri()
                            uriHandler.openUri(smartReplyUri)
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                            contentDescription = "Gemma Smart Reply",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        RefreshContainer(
            onRefresh = state::refresh,
            isRefreshing = state.isRefreshing,
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
            indicatorPadding = it,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(1),
                    modifier =
                        Modifier
                            .let {
                                if (isBigScreen) {
                                    it
                                        .fillMaxHeight()
                                        .widthIn(max = 480.dp)
                                } else {
                                    it.fillMaxSize()
                                }
                            },
                    contentPadding = it,
                ) {
                    status(
                        state.state.listState,
                        detailStatusKey = statusKey,
                    )
                }
            },
        )
    }
}

@Composable
private fun statusPresenter(
    statusKey: MicroBlogKey,
    accountType: AccountType,
) = run {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val state =
        remember(statusKey) {
            StatusContextPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()

    object {
        val state = state
        val isRefreshing = isRefreshing

        fun refresh() {
            scope.launch {
                isRefreshing = true
                state.refresh()
                isRefreshing = false
            }
        }
    }
}
