package org.crux.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.crux.data.network.rss.RssService
import org.crux.data.network.rss.model.OpmlOutline
import org.crux.data.network.rss.model.decodeOpml
import org.crux.data.repository.SubscriptionRepository
import org.crux.data.repository.SubscriptionSourceInput
import org.crux.di.koinInject
import org.crux.ui.model.UiRssSource
import org.crux.ui.presenter.PresenterBase
import kotlin.time.Clock

private const val MAX_CONCURRENT_IMPORTS = 4

public class ImportOPMLPresenter(
    private val opmlContent: String,
    private val fetchIcon: suspend (String) -> String? = { RssService.fetchIcon(it) },
) : PresenterBase<ImportOPMLPresenter.State>() {
    private val subscriptionRepository: SubscriptionRepository by koinInject()

    @Immutable
    public interface State {
        public val importing: Boolean
        public val progress: Float
        public val importedCount: Int
        public val totalCount: Int
        public val error: String?
        public val importedSources: ImmutableList<UiRssSource>
    }

    @Composable
    override fun body(): State {
        var importing by remember { mutableStateOf(true) }
        val importedSources = remember { mutableStateListOf<UiRssSource>() }
        var totalCount by remember { mutableStateOf(0) }
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            try {
                val opml = decodeOpml(opmlContent)
                val outlines =
                    opml.body.outlines
                        .flatMap { flattenOutlines(it) }
                        .filter { !it.xmlUrl.isNullOrBlank() }

                totalCount = outlines.size

                val limiter = Semaphore(MAX_CONCURRENT_IMPORTS)
                channelFlow {
                    outlines.forEach { outline ->
                        launch {
                            val url = outline.xmlUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return@launch
                            // One feed that cannot be read must not cancel the rest of the import.
                            val source =
                                try {
                                    limiter.withPermit { importOutline(url, outline) }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    null
                                }
                            source?.let { send(it) }
                        }
                    }
                }.collect { source ->
                    importedSources.add(source)
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                importing = false
            }
        }

        return object : State {
            override val importing = importing
            override val progress = if (totalCount > 0) importedSources.size.toFloat() / totalCount else 0f
            override val importedCount = importedSources.size
            override val totalCount = totalCount
            override val error = error
            override val importedSources = importedSources.toImmutableList()
        }
    }

    private suspend fun importOutline(
        url: String,
        outline: OpmlOutline,
    ): UiRssSource {
        subscriptionRepository.findByUrl(url).firstOrNull()?.let { return it }
        val icon =
            try {
                fetchIcon(url)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        return subscriptionRepository.upsert(
            SubscriptionSourceInput(
                url = url,
                title = outline.title?.takeIf { it.isNotBlank() } ?: outline.text?.takeIf { it.isNotBlank() } ?: url,
                icon = icon,
                lastUpdateMillis = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }

    private fun flattenOutlines(outline: OpmlOutline): List<OpmlOutline> {
        val result = mutableListOf<OpmlOutline>()
        result.add(outline)
        outline.outlines.forEach { result.addAll(flattenOutlines(it)) }
        return result
    }
}
