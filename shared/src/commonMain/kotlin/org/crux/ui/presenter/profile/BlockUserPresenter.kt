package org.crux.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.crux.data.datasource.microblog.datasource.RelationDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.presenter.PresenterBase

public class BlockUserPresenter(
    private val accountType: AccountType?,
    private val userKey: MicroBlogKey,
) : PresenterBase<BlockUserPresenter.State>() {
    private val accountRepository by koinInject<AccountRepository>()
    private val scope by koinInject<CoroutineScope>()

    @Immutable
    public interface State {
        public fun confirm()
    }

    @Composable
    override fun body(): State =
        object : State {
            override fun confirm() {
                if (accountType != null) {
                    scope.launch {
                        accountServiceFlow(accountType, repository = accountRepository)
                            .firstOrNull()
                            ?.let {
                                if (it is RelationDataSource) {
                                    it.relationHandler.block(userKey)
                                }
                            }
                    }
                }
            }
        }
}
