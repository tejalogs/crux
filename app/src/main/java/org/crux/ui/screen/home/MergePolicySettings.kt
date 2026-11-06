package org.crux.ui.screen.home

import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.persistentMapOf
import org.crux.R
import org.crux.data.model.tab.TimelineMergePolicy
import org.crux.ui.screen.settings.SingleChoiceSettingsItem

@Composable
internal fun MergePolicySettingsItem(
    selected: TimelineMergePolicy,
    onSelected: (TimelineMergePolicy) -> Unit,
    shapes: ListItemShapes,
) {
    SingleChoiceSettingsItem(
        headline = { Text(text = stringResource(id = R.string.tab_settings_merge_policy)) },
        supporting = { Text(text = stringResource(id = R.string.tab_settings_merge_policy_desc)) },
        items =
            persistentMapOf(
                TimelineMergePolicy.Time to stringResource(id = R.string.tab_settings_merge_policy_time),
                TimelineMergePolicy.TimePerPage to stringResource(id = R.string.tab_settings_merge_policy_time_per_page),
                TimelineMergePolicy.Staggered to stringResource(id = R.string.tab_settings_merge_policy_staggered),
            ),
        selected = selected,
        onSelected = onSelected,
        shapes = shapes,
    )
}
