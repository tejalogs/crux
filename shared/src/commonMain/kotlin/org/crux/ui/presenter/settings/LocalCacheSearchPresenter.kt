package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import androidx.paging.map
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import org.crux.common.PagingState
import org.crux.common.toPagingState
import org.crux.data.ai.SemanticEmbeddingEngine
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.model.TranslationDisplayOptions
import org.crux.data.datasource.microblog.paging.TimelinePagingMapper
import org.crux.data.datasource.microblog.pagingConfig
import org.crux.data.repository.AccountRepository
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiState
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.collectAsUiState
import org.crux.ui.model.flatMap
import org.crux.ui.model.map
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.presenter.status.LogStatusHistoryPresenter
import org.crux.web.shared.WebPresenter

@WebPresenter("localCacheSearch")
public class LocalCacheSearchPresenter : PresenterBase<LocalCacheSearchPresenter.State>() {
    private val database: CacheDatabase by koinInject()
    private val accountRepository: AccountRepository by koinInject()

    @androidx.compose.runtime.Immutable
    public interface State {
        public val data: PagingState<UiTimelineV2>
        public val history: PagingState<UiTimelineV2>
        public val userHistory: PagingState<UiProfile>
        public val searchUser: PagingState<UiProfile>
        public val isSemanticMode: Boolean
        public val semanticScores: Map<String, Float>

        public fun setQuery(value: String)

        public fun setSemanticMode(value: Boolean)
    }

    @Composable
    override fun body(): State {
        var query by remember { mutableStateOf("") }
        var isSemanticMode by remember { mutableStateOf(false) }
        var semanticResults by remember { mutableStateOf<List<UiTimelineV2>>(emptyList()) }
        var semanticScores by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
        var isSemanticLoading by remember { mutableStateOf(false) }

        LaunchedEffect(query, isSemanticMode) {
            if (isSemanticMode && query.isNotBlank()) {
                isSemanticLoading = true
                val cached = database.pagingTimelineDao().getRecentCachedStatuses(limit = 200)
                val posts =
                    cached.mapNotNull { dbItem ->
                        try {
                            TimelinePagingMapper.toUi(
                                item = dbItem,
                                pagingKey = LOCAL_CACHE_SEARCH_PAGING_KEY,
                                translationDisplayOptions = localCacheTranslationDisplayOptions,
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                val ranked =
                    SemanticEmbeddingEngine.search(
                        query = query,
                        items = posts,
                        textSelector = { it.searchText ?: "" },
                        topK = 50,
                        threshold = 0.15f,
                    )
                semanticResults = ranked.map { it.item }
                semanticScores = ranked.associate { it.item.statusKey.toString() to it.score }
                isSemanticLoading = false
            } else if (!isSemanticMode) {
                semanticResults = emptyList()
                semanticScores = emptyMap()
            }
        }
        val allAccounts by accountRepository.allAccounts.collectAsUiState()
        val paging =
            remember(query) {
                if (query.isEmpty()) {
                    UiState.Error(Throwable("Query is empty"))
                } else {
                    Pager(
                        config = pagingConfig,
                    ) {
                        database.pagingTimelineDao().searchHistoryPagingSource(query = "%$query%")
                    }.flow.let {
                        UiState.Success(it)
                    }
                }
            }
        val history =
            remember(allAccounts) {
                allAccounts.map { accounts ->
                    Pager(
                        config = pagingConfig,
                    ) {
                        database
                            .pagingTimelineDao()
                            .getStatusHistoryPagingSource(pagingKey = LogStatusHistoryPresenter.PAGING_KEY)
                    }.flow.map {
                        it.map {
                            TimelinePagingMapper.toUi(
                                item = it,
                                pagingKey = it.timeline.pagingKey,
                                translationDisplayOptions = localCacheTranslationDisplayOptions,
                            )
                        }
                    }
                }
            }.map {
                it.collectAsLazyPagingItems()
            }.toPagingState()
        val data =
            remember(paging, allAccounts) {
                allAccounts.flatMap { accounts ->
                    paging.map { pagingData ->
                        pagingData.map {
                            it.map {
                                TimelinePagingMapper.toUi(
                                    item = it,
                                    pagingKey = LOCAL_CACHE_SEARCH_PAGING_KEY,
                                    translationDisplayOptions = localCacheTranslationDisplayOptions,
                                )
                            }
                        }
                    }
                }
            }.map {
                it.collectAsLazyPagingItems()
            }.toPagingState()

        val userHistory =
            remember {
                Pager(
                    config = pagingConfig,
                ) {
                    database.userDao().getUserHistory()
                }.flow.map {
                    it
                        .filter {
                            it.data.accountType is AccountType.Specific
                        }.map {
                            it.user.content
                        }
                }
            }.collectAsLazyPagingItems().toPagingState()

        val searchUser =
            remember(query) {
                if (query.isEmpty()) {
                    UiState.Error(Throwable("Query is empty"))
                } else {
                    UiState.Success(
                        Pager(
                            config = pagingConfig,
                        ) {
                            database.userDao().searchUser(query)
                        }.flow.map {
                            it.map { user ->
                                user.content
                            }
                        },
                    )
                }
            }.map {
                it.collectAsLazyPagingItems()
            }.toPagingState()

        val semanticDataState: PagingState<UiTimelineV2> =
            remember(isSemanticMode, isSemanticLoading, semanticResults) {
                if (!isSemanticMode) {
                    data
                } else if (isSemanticLoading) {
                    PagingState.Loading()
                } else if (semanticResults.isEmpty()) {
                    PagingState.Empty { }
                } else {
                    PagingState.Success.ImmutableSuccess(semanticResults.toImmutableList())
                }
            }

        return object : State {
            override fun setQuery(value: String) {
                query = value
            }

            override val isSemanticMode: Boolean = isSemanticMode
            override val semanticScores: Map<String, Float> = semanticScores

            override fun setSemanticMode(value: Boolean) {
                isSemanticMode = value
            }

            override val data: PagingState<UiTimelineV2> = if (isSemanticMode) semanticDataState else data
            override val history: PagingState<UiTimelineV2> = history
            override val userHistory: PagingState<UiProfile> = userHistory
            override val searchUser: PagingState<UiProfile> = searchUser
        }
    }
}

private val localCacheTranslationDisplayOptions =
    TranslationDisplayOptions(
        translationEnabled = false,
        autoDisplayEnabled = false,
        providerCacheKey = "",
    )

private const val LOCAL_CACHE_SEARCH_PAGING_KEY = "local_cache_search"
