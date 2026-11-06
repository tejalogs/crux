package org.crux.ui.component.status

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import org.crux.data.model.PostActionStyle
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.model.ClickContext
import org.crux.ui.model.UiTimelineV2

/** The compact media-viewer header, without post content or attachments. */
@Composable
public fun StatusSummaryComponent(
    item: UiTimelineV2.Post,
    modifier: Modifier = Modifier,
    nameModifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
    avatarModifier: Modifier = Modifier,
    actionsModifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier) {
        item.user?.let { user ->
            UserCompat(
                user = user,
                nameModifier = nameModifier,
                handleModifier = handleModifier,
                avatarModifier = avatarModifier,
                onUserClick = {
                    user.onClicked.invoke(ClickContext(launcher = uriHandler::openUri))
                },
            ) {}
        }
        if (LocalTimelineAppearance.current.postActionStyle != PostActionStyle.Hidden) {
            StatusActions(items = item.actions, modifier = Modifier.fillMaxWidth().then(actionsModifier))
        }
    }
}
