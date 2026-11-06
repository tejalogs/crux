package org.crux.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import org.crux.model.MicroBlogKey
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.settings.AccountsPresenter
import org.crux.ui.presenter.settings.AccountsState

public class AccountManagementPresenter : PresenterBase<AccountManagementPresenter.State>() {
    public interface State : AccountsState {
        public fun logout(accountKey: MicroBlogKey)

        public fun setOrder(value: List<MicroBlogKey>)
    }

    @Composable
    override fun body(): State {
        val state =
            remember {
                AccountsPresenter()
            }.invoke()

        val order = remember { mutableStateListOf<MicroBlogKey>() }
        state.accounts.onSuccess {
            LaunchedEffect(Unit) {
                order.clear()
                order.addAll(it.map { it.account.accountKey })
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                state.updateOrder(order.toList())
            }
        }

        return object : State, AccountsState by state {
            override fun logout(accountKey: MicroBlogKey) {
                removeAccount(accountKey)
            }

            override fun setOrder(value: List<MicroBlogKey>) {
                order.clear()
                order.addAll(value)
            }
        }
    }
}
