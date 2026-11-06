package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.crux.common.combineLatestFlowLists
import org.crux.data.datasource.microblog.AuthenticatedMicroblogDataSource
import org.crux.data.datasource.microblog.datasource.UserDataSource
import org.crux.data.platform.INSTAGRAM_PLATFORM_ID
import org.crux.data.platform.LINKEDIN_PLATFORM_ID
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.model.UnsupportedPlatformException
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiAccount
import org.crux.ui.model.UiHandle
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.model.flattenUiState
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.render.toUiPlainText
import org.crux.web.shared.WebIgnore
import org.crux.web.shared.WebPresenter

@WebPresenter("accounts")
public class AccountsPresenter : PresenterBase<AccountsState>() {
    private val accountRepository: AccountRepository by koinInject()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val accountsFlow by lazy {
        accountRepository.allAccounts
            .map { accounts ->
                accounts.map { account ->
                    if (!account.platformAvailable) {
                        return@map flowOf(
                            AccountsState.AccountItem(
                                account = account,
                                profile =
                                    UiState.Error(
                                        UnsupportedPlatformException(account.platformId, account.accountKey),
                                    ),
                            ),
                        )
                    }
                    accountServiceFlow(
                        AccountType.Specific(account.accountKey),
                        accountRepository,
                    ).flatMapLatest { service ->
                        if (service is UserDataSource && service is AuthenticatedMicroblogDataSource) {
                            service.userHandler.userById(account.accountKey.id).toUi().map { user ->
                                AccountsState.AccountItem(
                                    account = account,
                                    profile =
                                        if (user is UiState.Error && account.isWebSessionAccount()) {
                                            UiState.Success(account.webSessionProfile())
                                        } else {
                                            user
                                        },
                                )
                            }
                        } else if (account.isWebSessionAccount()) {
                            flowOf(
                                AccountsState.AccountItem(
                                    account = account,
                                    profile = UiState.Success(account.webSessionProfile()),
                                ),
                            )
                        } else {
                            flowOf(
                                AccountsState.AccountItem(
                                    account = account,
                                    profile = UiState.Error(IllegalStateException("Account service is not authenticated user data source")),
                                ),
                            )
                        }
                    }
                }
            }.combineLatestFlowLists()
            .map {
                it.toImmutableList()
            }
    }

    @Composable
    override fun body(): AccountsState {
        val accounts by accountsFlow.collectAsUiState()
        val activeAccount by accountRepository.activeAccount.flattenUiState()
        val scope = rememberCoroutineScope()
        return object : AccountsState {
            override val accounts = accounts
            override val activeAccount = activeAccount

            override fun setActiveAccount(accountKey: MicroBlogKey) {
                scope.launch {
                    runCatching { accountRepository.setActiveAccount(accountKey) }
                }
            }

            override fun removeAccount(accountKey: MicroBlogKey) {
                accountRepository.delete(accountKey)
            }

            override fun updateOrder(newOrder: List<MicroBlogKey>) {
                accountRepository.updateAccountOrder(newOrder)
            }
        }
    }
}

@Immutable
public interface AccountsState {
    public val accounts: UiState<ImmutableList<AccountItem>>
    public val activeAccount: UiState<UiAccount>

    public fun setActiveAccount(accountKey: MicroBlogKey)

    public fun removeAccount(accountKey: MicroBlogKey)

    @WebIgnore
    public fun updateOrder(newOrder: List<MicroBlogKey>)

    @Immutable
    public data class AccountItem(
        val account: UiAccount,
        val profile: UiState<UiProfile>,
    )
}

// Instagram and LinkedIn accounts are browser sessions: Crux never calls their APIs, so the user's
// own profile is usually unknown. Show the platform instead of a "Failed to load account" row.
private fun UiAccount.isWebSessionAccount(): Boolean = platformId == INSTAGRAM_PLATFORM_ID || platformId == LINKEDIN_PLATFORM_ID

private fun UiAccount.webSessionProfile(): UiProfile =
    UiProfile(
        key = accountKey,
        handle = UiHandle(raw = accountKey.id, host = accountKey.host),
        avatar = null,
        nameInternal = platformDisplayName.toUiPlainText(),
        platformId = platformId,
        platformIcon = platformIcon,
        clickEvent = ClickEvent.Noop,
        banner = null,
        description = null,
        matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0),
        mark = persistentListOf(),
        bottomContent = null,
    )
