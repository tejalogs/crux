package org.crux.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import org.crux.data.database.app.model.SubscriptionType
import org.crux.data.model.IconType
import org.crux.data.model.tab.AllRssTimelineData
import org.crux.data.model.tab.RssTimelineData
import org.crux.data.model.tab.SubscriptionTimelineData
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.model.tab.toUiTimelineTabItem
import org.crux.data.platform.RssTimelineSpecs
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiRssSource
import org.crux.ui.model.UiState
import org.crux.ui.model.UiText
import org.crux.ui.model.asText
import org.crux.ui.model.map
import org.crux.ui.model.takeSuccess
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.presenter.home.rss.RssSourcesPresenter
import org.crux.ui.presenter.list.PinnableTimelineTabPresenter
import org.crux.ui.presenter.settings.AccountsPresenter
import org.crux.web.shared.WebPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@WebPresenter("allTabs")
public class AllTabsPresenter : PresenterBase<AllTabsPresenter.State>() {
    @Composable
    override fun body(): State {
        val accountState = remember { AccountsPresenter() }.body()
        val accountTabs =
            accountState.accounts.map {
                it
                    .toImmutableList()
                    .mapNotNull { it.profile.takeSuccess() }
                    .map { user ->
                        val tabs = listTabPresenter(accountKey = user.key).tabs.takeSuccess()
                        State.AccountTabs(
                            profile = user,
                            tabs = tabs?.toImmutableList() ?: persistentListOf(),
                        )
                    }.toImmutableList()
            }
        val flattenedAccountTabs =
            accountTabs.map { groups ->
                groups
                    .map { group ->
                        State.FlattenedAccountTabs(
                            profile = group.profile,
                            sections =
                                group.tabs
                                    .map { section ->
                                        State.FlattenedTabSection(
                                            title = section.title.asText(),
                                            data =
                                                when (val data = section.data) {
                                                    is org.crux.common.PagingState.Success -> {
                                                        (0 until data.itemCount)
                                                            .mapNotNull { data.peek(it) }
                                                            .toImmutableList()
                                                    }

                                                    else -> {
                                                        persistentListOf()
                                                    }
                                                },
                                        )
                                    }.filter { section -> section.data.isNotEmpty() }
                                    .toImmutableList(),
                        )
                    }.filter { group -> group.sections.isNotEmpty() }
                    .toImmutableList()
            }

        val rssSources =
            remember {
                RssSourcesPresenter()
            }.body()
        val rssTabs =
            remember(rssSources.sources) {
                (
                    listOfNotNull(
                        if (rssSources.sources.isNotEmpty()) {
                            RssTimelineSpecs.allRss
                                .candidate(
                                    data = AllRssTimelineData,
                                ).toUiTimelineTabItem()
                        } else {
                            null
                        },
                    ) +
                        rssSources.sources.map { source -> source.toUiTimelineTabItem() }
                ).toImmutableList()
            }

        return object : State {
            override val accountTabs = accountTabs
            override val flattenedAccountTabs = flattenedAccountTabs
            override val rssTabs = rssTabs
        }
    }

    @Composable
    private fun listTabPresenter(accountKey: MicroBlogKey) =
        run {
            remember(accountKey) {
                PinnableTimelineTabPresenter(accountType = AccountType.Specific(accountKey))
            }.body()
        }

    @Immutable
    public interface State {
        public val rssTabs: ImmutableList<UiTimelineTabItem>
        public val accountTabs: UiState<ImmutableList<AccountTabs>>
        public val flattenedAccountTabs: UiState<ImmutableList<FlattenedAccountTabs>>

        @Immutable
        public data class AccountTabs(
            val profile: UiProfile,
            val tabs: ImmutableList<PinnableTimelineTabPresenter.PinnableTimelineTab>,
        )

        @Immutable
        public data class FlattenedAccountTabs(
            val profile: UiProfile,
            val sections: ImmutableList<FlattenedTabSection>,
        )

        @Immutable
        public data class FlattenedTabSection(
            val title: UiText,
            val data: ImmutableList<UiTimelineTabItem>,
        )
    }
}

private fun UiRssSource.toUiTimelineTabItem(): UiTimelineTabItem {
    val title = UiText.Raw(title ?: url)
    val icon =
        favIcon?.let { IconType.Url(it) }
            ?: if (type == SubscriptionType.RSS) {
                IconType.Material(UiIcon.Rss)
            } else {
                IconType.FavIcon(host)
            }
    return if (type == SubscriptionType.RSS) {
        RssTimelineSpecs.rss
            .candidate(
                data = RssTimelineData(url),
                title = title,
                icon = icon,
            ).toUiTimelineTabItem()
    } else {
        RssTimelineSpecs.subscription
            .candidate(
                data =
                    SubscriptionTimelineData(
                        subscriptionUrl = url,
                        subscriptionType = type,
                    ),
                title = title,
                icon = icon,
            ).toUiTimelineTabItem()
    }
}
