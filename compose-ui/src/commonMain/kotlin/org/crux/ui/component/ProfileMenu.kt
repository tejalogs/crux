package org.crux.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import org.crux.data.datasource.microblog.PostActionLayoutConfig
import org.crux.data.model.PostActionStyle
import org.crux.ui.component.platform.PlatformTextStyle
import org.crux.ui.component.status.StatusActions
import org.crux.ui.presenter.profile.ProfileState
import org.crux.ui.theme.PlatformTheme

@Composable
public fun ProfileMenu(
    profileState: ProfileState,
    modifier: Modifier = Modifier,
) {
    if (!profileState.actions.isEmpty()) {
        CompositionLocalProvider(
            LocalTimelineAppearance provides
                LocalTimelineAppearance.current.copy(
                    postActionStyle = PostActionStyle.RightAligned,
                    postActionLayout = PostActionLayoutConfig.Default,
                ),
            PlatformTextStyle provides PlatformTheme.typography.title,
        ) {
            StatusActions(profileState.actions, modifier = modifier)
        }
    }
}
