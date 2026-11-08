package org.crux.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.Pager
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import org.crux.common.BasePagingSource
import org.crux.common.PagingState
import org.crux.common.toPagingState
import org.crux.data.datasource.microblog.pagingConfig
import org.crux.data.repository.tryRun
import org.crux.di.koinInject
import org.crux.model.RecommendedInstance
import org.crux.ui.model.UiInstance
import org.crux.ui.model.UiState
import org.crux.ui.model.isSuccess
import org.crux.ui.presenter.PresenterBase
import org.crux.web.shared.WebPresenter

@WebPresenter("loginServiceSelect")
public class NodeInfoPresenter : PresenterBase<NodeInfoState>() {
    private val loginPlatformRegistry: LoginPlatformRegistry by koinInject()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): NodeInfoState {
        val scope = rememberCoroutineScope()
        var filter by remember { mutableStateOf("") }
        val filterFlow =
            remember {
                snapshotFlow { filter }
                    .debounce(666L)
            }
        val instances =
            remember {
                combine(
                    Pager(
                        config = pagingConfig,
                    ) {
                        LoginRecommendInstancePagingSource(loginPlatformRegistry)
                    }.flow.cachedIn(scope),
                    filterFlow,
                ) { pagingData, filter ->
                    pagingData.filter {
                        it.name.contains(filter, ignoreCase = true) ||
                            it.domain.contains(filter, ignoreCase = true)
                    }
                }
            }.collectAsLazyPagingItems()

        val detectedPlatformId by remember(filterFlow) {
            filterFlow.flatMapLatest {
                flow {
                    runCatching {
                        emit(UiState.Loading())
                        loginPlatformRegistry.detectPlatformId(it)
                    }.onSuccess {
                        emit(UiState.Success(it))
                    }.onFailure {
                        emit(UiState.Error(it))
                    }
                }
            }
        }.collectAsState(UiState.Loading())

        return object : NodeInfoState {
            override val instances = instances.toPagingState()
            override val detectedPlatformId = detectedPlatformId
            override val canNext = detectedPlatformId.isSuccess

            override fun setFilter(value: String) {
                if (filter != value) {
                    filter = value
                }
            }
        }
    }
}

@Immutable
public interface NodeInfoState {
    public val instances: PagingState<UiInstance>
    public val detectedPlatformId: UiState<NodeData>
    public val canNext: Boolean

    public fun setFilter(value: String)
}

private class LoginRecommendInstancePagingSource(
    private val loginPlatformRegistry: LoginPlatformRegistry,
) : BasePagingSource<Int, UiInstance>() {
    override fun getRefreshKey(state: androidx.paging.PagingState<Int, UiInstance>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiInstance> {
        val recommendations =
            coroutineScope {
                loginPlatformRegistry.all
                    .map { provider ->
                        async {
                            tryRun {
                                provider.recommendInstances().map { recommendation ->
                                    recommendation.copy(
                                        instance =
                                            recommendation.instance.copy(
                                                platformDisplayName = provider.metadata.displayName,
                                                platformIcon = provider.metadata.icon,
                                            ),
                                    )
                                }
                            }.getOrDefault(emptyList())
                        }
                    }.awaitAll()
                    .flatten()
            }
        val instances =
            recommendations
                .sortedWith(
                    compareByDescending<RecommendedInstance> { it.priority }
                        .thenByDescending { it.instance.usersCount },
                ).distinctBy { it.instance.platformId to it.instance.domain }
                .map { it.instance }
        return LoadResult.Page(
            data = instances,
            prevKey = null,
            nextKey = null,
        )
    }
}
