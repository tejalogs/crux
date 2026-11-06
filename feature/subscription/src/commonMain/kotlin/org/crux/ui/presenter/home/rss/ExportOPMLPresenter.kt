package org.crux.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import nl.adaptivity.xmlutil.serialization.XML
import org.crux.data.database.app.model.SubscriptionType
import org.crux.data.network.rss.model.Opml
import org.crux.data.network.rss.model.OpmlBody
import org.crux.data.network.rss.model.OpmlHead
import org.crux.data.network.rss.model.OpmlOutline
import org.crux.data.repository.SubscriptionRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.presenter.PresenterBase
import kotlin.time.Clock

public class ExportOPMLPresenter : PresenterBase<UiState<String>>() {
    private val subscriptionRepository: SubscriptionRepository by koinInject()

    @Composable
    override fun body(): UiState<String> {
        var state by remember { mutableStateOf<UiState<String>>(UiState.Loading()) }

        LaunchedEffect(Unit) {
            try {
                state = UiState.Success(export())
            } catch (e: Exception) {
                state = UiState.Error(e)
            }
        }

        return state
    }

    public suspend fun export(): String {
        val sources =
            subscriptionRepository
                .observeAll()
                .first()
                .filter { it.type == SubscriptionType.RSS }

        val outlines =
            sources.map { source ->
                OpmlOutline(
                    text = source.title ?: source.url,
                    title = source.title,
                    type = "rss",
                    xmlUrl = source.url,
                    htmlUrl = null,
                )
            }

        val opml =
            Opml(
                version = "2.0",
                head =
                    OpmlHead(
                        title = "Crux Export",
                        dateCreated = Clock.System.now().toString(),
                    ),
                body =
                    OpmlBody(
                        outlines = outlines,
                    ),
            )

        return XML
            .v1 {
                indentString = "  "
            }.encodeToString(
                Opml.serializer(),
                opml,
            )
    }
}
