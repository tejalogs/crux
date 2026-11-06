package org.crux.ui.presenter.status.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.crux.data.datasource.microblog.datasource.PostDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.presenter.PresenterBase

public class DeleteStatusPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<DeleteStatusState>() {
    // using io scope because it's a long-running operation
    private val scope by koinInject<CoroutineScope>()
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): DeleteStatusState =
        object : DeleteStatusState {
            override fun delete() {
                scope.launch {
                    accountServiceFlow(
                        accountType = accountType,
                        repository = accountRepository,
                    ).map {
                        require(it is PostDataSource)
                        it
                    }.first()
                        .let {
                            it.postHandler.delete(statusKey)
                        }
                }
            }
        }
}

@Immutable
public interface DeleteStatusState {
    public fun delete()
}
