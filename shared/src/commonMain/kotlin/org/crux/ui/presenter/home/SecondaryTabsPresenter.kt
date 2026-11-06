package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import org.crux.common.combineLatestFlowLists
import org.crux.data.datasource.microblog.AuthenticatedMicroblogDataSource
import org.crux.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import org.crux.data.datasource.microblog.datasource.UserDataSource
import org.crux.data.model.tab.ShortcutSpec
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.model.tab.toUiTimelineTabItem
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.allAccountServicesFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiState
import org.crux.ui.model.UiStrings
import org.crux.ui.model.collectAsUiState
import org.crux.ui.model.takeSuccess
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toWebPath
import org.crux.web.shared.WebIgnore
import org.crux.web.shared.WebPresenter

@WebPresenter("secondaryTabs")
public class SecondaryTabsPresenter : PresenterBase<SecondaryTabsPresenter.State>() {
    @Immutable
    public interface State {
        public val items: UiState<ImmutableList<Item>>
    }

    @Immutable
    public class Item(
        public val accountKey: MicroBlogKey,
        public val user: UiState<UiProfile>,
        public val tabs: ImmutableList<Tab>,
    )

    @Immutable
    public data class Tab(
        val title: UiStrings,
        val icon: UiIcon,
        @WebIgnore
        val destination: Destination,
    ) {
        public val href: String? = (destination as? Destination.Route)?.route?.toWebPath()
        public val timelineTabItem: UiTimelineTabItem? = (destination as? Destination.Timeline)?.tabItem
    }

    @Immutable
    public sealed interface Destination {
        public data class Route(
            val route: DeeplinkRoute,
        ) : Destination

        public data class Timeline(
            val tabItem: UiTimelineTabItem,
        ) : Destination
    }

    private val accountRepository: AccountRepository by koinInject()

    private val itemsFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map { services ->
                services
                    .mapNotNull { service ->
                        if (service is UserDataSource && service is AuthenticatedMicroblogDataSource) {
                            service.userHandler
                                .userById(service.accountKey.id)
                                .toUi()
                                .distinctUntilChangedBy {
                                    it.takeSuccess()?.let {
                                        buildString {
                                            append(it.key)
                                            append("-")
                                            append(it.name.raw)
                                            append("-")
                                            append(it.avatar)
                                            append("-")
                                            append(it.handle.raw)
                                        }
                                    }
                                }.map { userState ->
                                    userState.takeSuccess()?.let { user ->
                                        Item(
                                            accountKey = service.accountKey,
                                            user = userState,
                                            tabs =
                                                (
                                                    listOf(
                                                        ShortcutSpec(
                                                            title = UiStrings.Me,
                                                            icon = UiIcon.Profile,
                                                            target =
                                                                ShortcutSpec.Target.Route(
                                                                    DeeplinkRoute.Profile.User(
                                                                        accountType = AccountType.Specific(service.accountKey),
                                                                        userKey = user.key,
                                                                    ),
                                                                ),
                                                        ),
                                                    ) +
                                                        (service as? TimelineTabConfigurationDataSource)
                                                            ?.shortcuts
                                                            .orEmpty()
                                                ).mapNotNull(::toTab)
                                                    .toImmutableList(),
                                        )
                                    }
                                }
                        } else {
                            null
                        }
                    }
            }.combineLatestFlowLists()
            .map {
                it.filterNotNull().toImmutableList()
            }.distinctUntilChanged()
    }

    @Composable
    override fun body(): State {
        val items by itemsFlow.collectAsUiState()

        return object : State {
            override val items = items
        }
    }

    private fun toTab(shortcut: ShortcutSpec): Tab? =
        when (val target = shortcut.target) {
            is ShortcutSpec.Target.Route -> {
                Tab(
                    title = shortcut.title,
                    icon = shortcut.icon,
                    destination = Destination.Route(target.route),
                )
            }

            is ShortcutSpec.Target.Timeline -> {
                Tab(
                    title = shortcut.title,
                    icon = shortcut.icon,
                    destination = Destination.Timeline(target.candidate.toUiTimelineTabItem()),
                )
            }
        }
}
