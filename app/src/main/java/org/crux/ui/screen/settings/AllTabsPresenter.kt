package org.crux.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.crux.data.model.IconType
import org.crux.data.model.tab.AllRssTimelineData
import org.crux.data.model.tab.LiveWebTimelineSpecs
import org.crux.data.model.tab.RssTimelineData
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.model.tab.toUiTimelineTabItem
import org.crux.rss.NativeRssTimelineSpecs
import org.crux.rss.RssFeed
import org.crux.rss.RssRepository
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiText
import org.crux.ui.presenter.PresenterBase

/**
 * Pure-Android presenter surfacing Crux's native RSS feeds and live web platforms for pinning
 * onto the Home tab bar.
 */
internal class AllTabsPresenter : PresenterBase<AllTabsPresenter.State>() {
    @Composable
    override fun body(): State {
        val context = LocalContext.current
        val repo = remember(context) { RssRepository.get(context) }
        val feeds by repo.feeds.collectAsState()
        LaunchedEffect(repo) {
            repo.ensureLoaded()
        }
        val rssTabs =
            remember(feeds) {
                (
                    listOfNotNull(
                        if (feeds.isNotEmpty()) {
                            NativeRssTimelineSpecs.allRss
                                .candidate(data = AllRssTimelineData)
                                .toUiTimelineTabItem()
                        } else {
                            null
                        },
                    ) + feeds.map { it.toUiTimelineTabItem() }
                ).toImmutableList()
            }
        val webTabs =
            remember {
                listOf(
                    LiveWebTimelineSpecs.xCandidate.toUiTimelineTabItem(),
                    LiveWebTimelineSpecs.instagramCandidate.toUiTimelineTabItem(),
                    LiveWebTimelineSpecs.linkedinCandidate.toUiTimelineTabItem(),
                    LiveWebTimelineSpecs.redditCandidate.toUiTimelineTabItem(),
                    LiveWebTimelineSpecs.mastodonCandidate.toUiTimelineTabItem(),
                ).toImmutableList()
            }

        return object : State {
            override val rssTabs: ImmutableList<UiTimelineTabItem> = rssTabs
            override val webTabs: ImmutableList<UiTimelineTabItem> = webTabs
        }
    }

    @Immutable
    interface State {
        val rssTabs: ImmutableList<UiTimelineTabItem>
        val webTabs: ImmutableList<UiTimelineTabItem>
    }
}

private fun RssFeed.toUiTimelineTabItem(): UiTimelineTabItem =
    NativeRssTimelineSpecs.rss
        .candidate(
            data = RssTimelineData(url),
            title = UiText.Raw(title.ifBlank { url }),
            icon = iconUrl?.let { IconType.Url(it) } ?: IconType.Material(UiIcon.Rss),
        ).toUiTimelineTabItem()
