package org.crux.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import org.crux.data.network.rss.DocumentData
import org.crux.data.network.rss.Readability
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiState
import org.crux.ui.model.mapper.fromRss
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.presenter.status.LogStatusHistoryPresenter

public class RssDetailPresenter(
    private val url: String,
    private val descriptionHtml: String? = null,
    private val descriptionTitle: String? = null,
    private val fallbackHtml: String? = null,
) : PresenterBase<RssDetailPresenter.State>() {
    private val readability = Readability()

    @Immutable
    public interface State {
        public val data: UiState<DocumentData>
    }

    @Composable
    override fun body(): State {
        remember {
            LogStatusHistoryPresenter(
                accountType = AccountType.Guest,
                statusKey = MicroBlogKey.fromRss(url),
            )
        }.body()
        val data by remember(url, descriptionHtml, descriptionTitle, fallbackHtml) {
            flow<UiState<DocumentData>> {
                val result =
                    try {
                        UiState.Success(readability.load(url, descriptionHtml, descriptionTitle, fallbackHtml))
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        UiState.Error(exception)
                    }
                emit(result)
            }.onStart { emit(UiState.Loading()) }
        }.collectAsState(UiState.Loading())
        return object : State {
            override val data = data
        }
    }
}
