package org.crux.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.crux.R
import org.crux.data.model.appearance.AppearanceKeys
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.theme.single

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceDisplayContent(state: AppearanceSettingsUpdater) {
    val timelineAppearance = LocalTimelineAppearance.current
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        SegmentedListItem(
            onClick = {
                state.update(AppearanceKeys.AbsoluteTimestamp, !timelineAppearance.absoluteTimestamp)
            },
            shapes = ListItemDefaults.single(),
            content = {
                Text(text = stringResource(id = R.string.settings_appearance_absolute_timestamp))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.settings_appearance_absolute_timestamp_description))
            },
            trailingContent = {
                Switch(
                    checked = timelineAppearance.absoluteTimestamp,
                    onCheckedChange = {
                        state.update(AppearanceKeys.AbsoluteTimestamp, it)
                    },
                )
            },
        )
    }
}
