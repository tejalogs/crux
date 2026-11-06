package org.crux.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Robot
import compose.icons.fontawesomeicons.solid.Xmark
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.feature.agent.localhistory.LocalHistoryAgentTarget
import org.crux.ui.common.itemsIndexed
import org.crux.ui.component.BackArrow
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.component.status.AdaptiveCard
import org.crux.ui.component.status.LazyStatusVerticalStaggeredGrid
import org.crux.ui.component.status.status
import org.crux.ui.model.ClickContext
import org.crux.ui.model.UiState
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.settings.LocalCacheSearchPresenter
import org.crux.ui.theme.screenHorizontalPadding

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LocalCacheSearchScreen(
    onBack: () -> Unit,
    onAskAiClick: (String?, LocalHistoryAgentTarget) -> Unit = { _, _ -> },
) {
    val state by producePresenter {
        presenter()
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyStaggeredGridState()
    val uriHandler = LocalUriHandler.current
    val showAskAi = LocalTimelineAppearance.current.aiConfig.agent
    CruxScaffold(
        topBar = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val inputField =
                    @Composable {
                        SearchBarDefaults.InputField(
                            textFieldState = textFieldState,
                            searchBarState = searchBarState,
                            onSearch = {
                                state.setQuery(it)
                                scope.launch { searchBarState.animateToCollapsed() }
                                keyboardController?.hide()
                            },
                            placeholder = {
                                Text(stringResource(R.string.local_history_search_placeholder))
                            },
                            leadingIcon = {
                                IconButton(
                                    onClick = {
                                        if (searchBarState.currentValue == SearchBarValue.Expanded) {
                                            scope.launch { searchBarState.animateToCollapsed() }
                                        } else {
                                            onBack()
                                        }
                                    },
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.BackArrow,
                                        contentDescription = stringResource(id = R.string.navigate_back),
                                    )
                                }
                            },
                            trailingIcon = {
                                if (textFieldState.text.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            textFieldState.clearText()
                                        },
                                    ) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.Xmark,
                                            contentDescription = stringResource(R.string.clear_search),
                                        )
                                    }
                                }
                            },
                        )
                    }
                androidx.compose.material3.SearchBar(
                    state = searchBarState,
                    inputField = inputField,
                    modifier =
                        Modifier
                            .windowInsetsPadding(SearchBarDefaults.windowInsets)
                            .padding(vertical = 8.dp),
                )
                ExpandedFullScreenSearchBar(
                    state = searchBarState,
                    inputField = inputField,
                    content = {
                        if (showAskAi) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .imePadding()
                                        .padding(16.dp),
                            ) {
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        scope.launch { searchBarState.animateToCollapsed() }
                                        keyboardController?.hide()
                                        onAskAiClick(
                                            textFieldState.text
                                                .toString()
                                                .trim()
                                                .takeIf { it.isNotEmpty() },
                                            state.selectedSearchType.toLocalHistoryAgentTarget(),
                                        )
                                    },
                                    icon = {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Robot,
                                            contentDescription = null,
                                        )
                                    },
                                    text = {
                                        Text(text = stringResource(id = R.string.ask_ai))
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { contentPadding ->
        LazyStatusVerticalStaggeredGrid(
            state = lazyListState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                val items =
                    state.allSearchTypes.associate { searchType ->
                        searchType to stringResource(id = searchType.title)
                    }
                Row(
                    modifier =
                        Modifier
                            .padding(horizontal = screenHorizontalPadding, vertical = 8.dp)
                            .fillMaxWidth(),
                    horizontalArrangement =
                        androidx.compose.foundation.layout.Arrangement
                            .spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { (searchType, title) ->
                        val isSelected = state.selectedSearchType == searchType
                        ToggleButton(
                            checked = isSelected,
                            onCheckedChange = {
                                state.setSearchType(searchType)
                            },
                            colors =
                                ToggleButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                title,
                                style =
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                            )
                        }
                    }
                    if (state.selectedSearchType == SearchType.Status) {
                        val isSemSelected = state.isSemanticMode
                        ToggleButton(
                            checked = isSemSelected,
                            onCheckedChange = {
                                state.setSemanticMode(!state.isSemanticMode)
                            },
                            colors =
                                ToggleButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "✦ Semantic (Gemma AI)",
                                style =
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSemSelected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                            )
                        }
                    }
                }
            }
            if (state.isSemanticMode && textFieldState.text.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    androidx.compose.material3.Surface(
                        shape =
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = screenHorizontalPadding, vertical = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement =
                                androidx.compose.foundation.layout.Arrangement
                                    .spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "✦ On-Device Gemma 4 Semantic Ranking",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "• Zero Cloud Egress",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            when (state.selectedSearchType) {
                SearchType.Status -> {
                    status(
                        if (textFieldState.text.isEmpty() && !state.isSemanticMode) {
                            state.history
                        } else {
                            state.data
                        },
                    )
                }

                SearchType.User -> {
                    itemsIndexed(
                        if (textFieldState.text.isEmpty()) {
                            state.userHistory
                        } else {
                            state.searchUser
                        },
                    ) { index, itemsCount, user ->
                        AdaptiveCard(
                            index = index,
                            totalCount = itemsCount,
                            content = {
                                AccountItem(
                                    userState = UiState.Success(user),
                                    onClick = { user.onClicked.invoke(ClickContext(uriHandler::openUri)) },
                                    toLogin = {},
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun presenter() =
    run {
        val state = remember { LocalCacheSearchPresenter() }.invoke()
        var selectedSearchType by remember { mutableStateOf(SearchType.Status) }
        object : LocalCacheSearchPresenter.State by state {
            val selectedSearchType = selectedSearchType
            val allSearchTypes = SearchType.entries.toImmutableList()

            fun setSearchType(value: SearchType) {
                selectedSearchType = value
            }
        }
    }

private enum class SearchType(
    val title: Int,
) {
    Status(R.string.local_history_search_status_title),
    User(R.string.local_history_search_user_title),
}

private fun SearchType.toLocalHistoryAgentTarget(): LocalHistoryAgentTarget =
    when (this) {
        SearchType.Status -> LocalHistoryAgentTarget.Posts
        SearchType.User -> LocalHistoryAgentTarget.Users
    }
