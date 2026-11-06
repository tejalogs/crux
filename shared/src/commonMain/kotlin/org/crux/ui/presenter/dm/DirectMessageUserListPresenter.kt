package org.crux.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import org.crux.common.combineLatestFlowLists
import org.crux.data.datasource.microblog.DirectMessageDataSource
import org.crux.data.datasource.microblog.datasource.UserDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.allAccountServicesFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.model.takeSuccess
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase

public class DirectMessageUserListPresenter : PresenterBase<DirectMessageUserListState>() {
    private val accountRepository: AccountRepository by koinInject()
    private val queryFlow = MutableStateFlow("")

    private val itemsFlow by lazy {
        combine(
            allAccountServicesFlow(accountRepository)
                .map { services ->
                    services.mapNotNull { service ->
                        if (
                            service is DirectMessageDataSource &&
                            service is UserDataSource
                        ) {
                            combine(
                                service.userHandler
                                    .userById(service.accountKey.id)
                                    .toUi()
                                    .distinctUntilChangedBy(::profileIdentity),
                                service.directMessageBadgeCount
                                    .toUi(),
                            ) { profile, unreadCount ->
                                DirectMessageUserListItem(
                                    accountKey = service.accountKey,
                                    accountType = AccountType.Specific(service.accountKey),
                                    profile = profile,
                                    unreadCount = unreadCount,
                                )
                            }
                        } else {
                            null
                        }
                    }
                }.combineLatestFlowLists(),
            queryFlow,
        ) { items, query ->
            val normalizedQuery = query.trim().lowercase()
            items
                .filter { item ->
                    normalizedQuery.isEmpty() || item.matches(normalizedQuery)
                }.toImmutableList()
        }.distinctUntilChanged()
    }

    @Composable
    override fun body(): DirectMessageUserListState {
        val items by itemsFlow.collectAsUiState()
        val query by queryFlow.collectAsUiState(UiState.Success(""))

        return object : DirectMessageUserListState {
            override val items: UiState<ImmutableList<DirectMessageUserListItem>> = items
            override val query: String = query.takeSuccess().orEmpty()

            override fun setQuery(query: String) {
                queryFlow.value = query
            }
        }
    }

    private fun DirectMessageUserListItem.matches(query: String): Boolean {
        val profile = profile.takeSuccess()
        return listOfNotNull(
            accountKey.id,
            accountKey.host,
            profile?.name?.raw,
            profile?.handle?.raw,
            profile?.handle?.canonical,
            profile?.handleWithoutAt,
        ).any { value ->
            value.lowercase().contains(query)
        }
    }

    private fun profileIdentity(profile: UiState<UiProfile>): String? =
        profile.takeSuccess()?.let { user ->
            buildString {
                append(user.key)
                append("-")
                append(user.name.raw)
                append("-")
                append(user.avatar)
                append("-")
                append(user.handle.raw)
            }
        }
}

@Immutable
public interface DirectMessageUserListState {
    public val query: String
    public val items: UiState<ImmutableList<DirectMessageUserListItem>>

    public fun setQuery(query: String)
}

@Immutable
public data class DirectMessageUserListItem(
    val accountKey: MicroBlogKey,
    val accountType: AccountType,
    val profile: UiState<UiProfile>,
    val unreadCount: UiState<Int>,
)
