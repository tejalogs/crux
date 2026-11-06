package org.crux.ui.screen.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import org.crux.data.database.app.model.SubscriptionType
import org.crux.data.model.IconType
import org.crux.data.model.tab.RssTimelineData
import org.crux.data.model.tab.SubscriptionTimelineData
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.model.tab.toUiTimelineTabItem
import org.crux.data.platform.RssTimelineSpecs
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiRssSource
import org.crux.ui.model.UiText
import org.crux.ui.presenter.PinTabsPresenter
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.presenter.home.rss.RssSourcesPresenter
import org.crux.ui.presenter.invoke

public class RssListWithTabsPresenter : PresenterBase<RssListWithTabsPresenter.State>() {
    private val pinTabsPresenter by lazy {
        object : PinTabsPresenter<UiRssSource>() {
            override fun getTimelineTabItem(item: UiRssSource): UiTimelineTabItem =
                if (item.type == SubscriptionType.RSS) {
                    RssTimelineSpecs.rss
                        .candidate(
                            data = RssTimelineData(item.url),
                            title = UiText.Raw(item.title ?: item.url),
                            icon = item.favIcon?.let { IconType.Url(it) } ?: IconType.Material(UiIcon.Rss),
                        ).toUiTimelineTabItem()
                } else {
                    RssTimelineSpecs.subscription
                        .candidate(
                            data =
                                SubscriptionTimelineData(
                                    subscriptionUrl = item.url,
                                    subscriptionType = item.type,
                                ),
                            title = UiText.Raw(item.title ?: item.url),
                            icon =
                                item.favIcon?.let { IconType.Url(it) }
                                    ?: if (item.type == SubscriptionType.RSS) {
                                        IconType.Material(UiIcon.Rss)
                                    } else {
                                        IconType.FavIcon(item.host)
                                    },
                        ).toUiTimelineTabItem()
                }
        }
    }

    @Composable
    override fun body(): State {
        val state = remember { RssSourcesPresenter() }.invoke()
        val pinState = pinTabsPresenter.invoke()
        return object :
            State,
            RssSourcesPresenter.State by state,
            PinTabsPresenter.State<UiRssSource> by pinState {
        }
    }

    @Immutable
    public interface State :
        RssSourcesPresenter.State,
        PinTabsPresenter.State<UiRssSource>
}
