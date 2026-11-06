package org.crux.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.crux.data.model.tab.RssTimelineData
import org.crux.data.platform.RssTimelineSpecs
import org.crux.data.repository.SettingsRepository
import org.crux.data.repository.SubscriptionRepository
import org.crux.data.repository.SubscriptionSourceInput
import org.crux.di.koinInject
import org.crux.ui.model.UiRssSource
import org.crux.ui.presenter.PresenterBase
import org.crux.web.shared.WebPresenter

@WebPresenter("rssSources")
public class RssSourcesPresenter : PresenterBase<RssSourcesPresenter.State>() {
    private val subscriptionRepository by koinInject<SubscriptionRepository>()
    private val settingsRepository by koinInject<SettingsRepository>()

    @androidx.compose.runtime.Immutable
    public interface State {
        public val sources: ImmutableList<UiRssSource>

        public fun add(
            url: String,
            title: String,
            iconUrl: String?,
        )

        public fun delete(id: Int)

        public fun seedDefaults()
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            subscriptionRepository.ensureDefaultFeedsSeeded()
        }
        val sources by remember {
            subscriptionRepository.observeAll().map {
                it
                    .toImmutableList()
            }
        }.collectAsState(persistentListOf())
        return object : State {
            override val sources = sources

            override fun add(
                url: String,
                title: String,
                iconUrl: String?,
            ) {
                scope.launch {
                    subscriptionRepository.upsert(
                        SubscriptionSourceInput(
                            url = url,
                            title = title,
                            icon = iconUrl,
                            lastUpdateMillis = 0,
                        ),
                    )
                }
            }

            override fun delete(id: Int) {
                scope.launch {
                    val source = subscriptionRepository.delete(id)
                    source?.let {
                        settingsRepository.removeHomeTimelineTabBySourceId(it.timelineSourceId())
                    }
                }
            }

            override fun seedDefaults() {
                scope.launch {
                    subscriptionRepository.restoreDefaultFeeds()
                }
            }
        }
    }
}

private fun UiRssSource.timelineSourceId(): String = RssTimelineSpecs.rss.itemId(RssTimelineData(url))
