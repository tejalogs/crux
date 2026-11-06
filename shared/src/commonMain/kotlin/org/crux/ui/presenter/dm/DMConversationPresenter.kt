package org.crux.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import org.crux.common.PagingState
import org.crux.common.collectAsState
import org.crux.common.toPagingState
import org.crux.data.datasource.microblog.DirectMessageDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceProvider
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiDMItem
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiState
import org.crux.ui.model.flatMap
import org.crux.ui.model.map
import org.crux.ui.model.onSuccess
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase
import kotlin.time.Duration.Companion.seconds

public class DMConversationPresenter(
    private val accountType: AccountType,
    private val roomKey: MicroBlogKey,
) : PresenterBase<DMConversationState>() {
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): DMConversationState {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val scope = rememberCoroutineScope()
        val items =
            serviceState
                .map { service ->
                    require(service is DirectMessageDataSource)
                    remember(service, roomKey) {
                        service.directMessageConversation(roomKey, scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        val users =
            serviceState
                .flatMap { service ->
                    require(service is DirectMessageDataSource)
                    remember(service, roomKey) {
                        service.getDirectMessageConversationInfo(roomKey)
                    }.collectAsState().toUi()
                }.map {
                    it.users
                }
        serviceState.onSuccess {
            require(it is DirectMessageDataSource)
            LaunchedEffect(Unit) {
                while (true) {
                    delay(10.seconds)
                    runCatching {
                        it.fetchNewDirectMessageForConversation(roomKey)
                    }
                }
            }
        }
        return object : DMConversationState {
            override val items = items

            override val users = users

            override fun send(message: String) {
                serviceState.onSuccess {
                    require(it is DirectMessageDataSource)
                    it.sendDirectMessage(roomKey, message)
                }
            }

            override fun retry(key: MicroBlogKey) {
                serviceState.onSuccess {
                    require(it is DirectMessageDataSource)
                    it.retrySendDirectMessage(key)
                }
            }

            override fun leave() {
                serviceState.onSuccess {
                    require(it is DirectMessageDataSource)
                    it.leaveDirectMessage(roomKey)
                }
            }
        }
    }
}

@Immutable
public interface DMConversationState {
    public val items: PagingState<UiDMItem>
    public val users: UiState<ImmutableList<UiProfile>>

    public fun send(message: String)

    public fun retry(key: MicroBlogKey)

    public fun leave()
}
