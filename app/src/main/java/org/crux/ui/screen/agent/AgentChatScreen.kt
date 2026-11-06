package org.crux.ui.screen.agent

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.feature.agent.presenter.chat.GenericChatPresenter
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.agent.AgentChatScaffold
import org.crux.ui.component.agent.label
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiProfile
import org.crux.ui.presenter.invoke
import org.crux.ui.route.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentChatScreen(
    conversationId: String,
    initialMessage: String?,
    onBack: () -> Unit,
    navigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedInitialMessage = initialMessage?.trim()?.takeIf { it.isNotEmpty() }
    val state by producePresenter("agent_chat_${conversationId}_${normalizedInitialMessage.orEmpty()}") {
        GenericChatPresenter(
            conversationId = conversationId,
            initialMessage = normalizedInitialMessage,
        ).invoke()
    }
    val fallbackTitle = stringResource(id = R.string.agent_chat_title)
    val title = state.room.title.takeIf { it.isNotBlank() } ?: fallbackTitle
    val currentTrace = state.room.currentTrace
    val runningTrace =
        if (currentTrace != null) {
            currentTrace.label()
        } else {
            stringResource(id = R.string.agent_chat_thinking)
        }

    AgentChatScaffold(
        messages = state.messages,
        input = state.input,
        isRunning = state.room.isRunning,
        canSend = state.canSend,
        errorMessage = state.room.errorMessage,
        runningTrace = runningTrace,
        inputPlaceholder = stringResource(id = R.string.agent_chat_input_placeholder),
        sendContentDescription = stringResource(id = R.string.agent_chat_send),
        onInputChange = state::setInput,
        onSend = state::sendMessage,
        onInputRequestOptionSelected = state::selectInputRequestOption,
        onPostClick = { post ->
            navigate(Route.Status.Detail(statusKey = post.statusKey, accountType = post.accountType))
        },
        onUserClick = { user ->
            user.toRoute()?.let(navigate)
        },
        modifier = modifier,
        topBar = {
            CruxTopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
    )
}

private fun UiProfile.toRoute(): Route? =
    when (val event = clickEvent) {
        is ClickEvent.Deeplink -> Route.parse(event.url)
        ClickEvent.Noop -> null
    }
