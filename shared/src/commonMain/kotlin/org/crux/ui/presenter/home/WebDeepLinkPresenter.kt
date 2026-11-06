package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.crux.ui.model.ClickContext
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UriLauncher
import org.crux.ui.model.onClicked
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.route.toUri
import org.crux.ui.route.toWebPath
import org.crux.web.shared.WebPresenter

@WebPresenter("deepLink")
public class WebDeepLinkPresenter(
    private val onRoute: (String) -> Unit,
    private val onLink: (String) -> Unit,
) : PresenterBase<WebDeepLinkPresenter.State>() {
    public interface State {
        public fun handle(url: String)

        public fun performClickEvent(clickEvent: ClickEvent)
    }

    @Composable
    override fun body(): State {
        val deepLinkState =
            remember {
                DeepLinkPresenter(
                    onRoute = { route ->
                        onRoute(route.toWebPath() ?: route.toUri())
                    },
                    onLink = onLink,
                )
            }.body()

        return object : State {
            override fun handle(url: String) {
                deepLinkState.handle(url)
            }

            override fun performClickEvent(clickEvent: ClickEvent) {
                clickEvent.onClicked.invoke(clickContext(deepLinkState))
            }
        }
    }

    private fun clickContext(deepLinkState: DeepLinkPresenter.State): ClickContext =
        ClickContext(
            launcher =
                UriLauncher { url ->
                    deepLinkState.handle(url)
                },
        )
}
