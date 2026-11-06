package org.crux.ui.screen.settings

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.launch
import org.crux.data.model.appearance.AppearanceKey
import org.crux.data.model.appearance.AppearancePatch
import org.crux.data.repository.SettingsRepository
import org.crux.ui.component.CruxDropdownMenu
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.settings.AppearancePresenter
import org.crux.ui.presenter.settings.AppearanceState
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> SingleChoiceSettingsItem(
    headline: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
    items: ImmutableMap<T, String>,
    selected: T,
    onSelected: (T) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    SegmentedListItem(
        modifier = modifier,
        checked = showMenu,
        onCheckedChange = {
            showMenu = it
        },
        shapes = shapes,
        content = headline,
        supportingContent = supporting,
        trailingContent = {
            TextButton(onClick = { showMenu = true }) {
                Text(text = items[selected] ?: "")
            }
            CruxDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                items.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(text = label) },
                        onClick = {
                            onSelected(value)
                            showMenu = false
                        },
                    )
                }
            }
        },
    )
}

internal interface AppearanceSettingsUpdater : AppearanceState {
    val appearance: UiState<AppearancePatch>

    fun <T : Any> update(
        key: AppearanceKey<T>,
        value: T,
    )

    fun clear(key: AppearanceKey<*>)

    fun updateFontScale(fontSizeDiff: Float)
}

@Composable
internal fun appearancePresenter(): AppearanceSettingsUpdater =
    run {
        val scope = rememberCoroutineScope()
        val settingsRepository = koinInject<SettingsRepository>()
        val appearanceState = remember { AppearancePresenter() }.invoke()
        val appearance by settingsRepository.appearancePatch.collectAsUiState()

        object : AppearanceSettingsUpdater, AppearanceState by appearanceState {
            override val appearance: UiState<AppearancePatch> = appearance

            override fun <T : Any> update(
                key: AppearanceKey<T>,
                value: T,
            ) {
                scope.launch {
                    settingsRepository.updateAppearance(key, value)
                }
            }

            override fun clear(key: AppearanceKey<*>) {
                scope.launch {
                    settingsRepository.clearAppearance(key)
                }
            }

            override fun updateFontScale(fontSizeDiff: Float) {
                scope.launch {
                    settingsRepository.updateAppearance {
                        set(org.crux.data.model.appearance.AppearanceKeys.FontSizeDiff, fontSizeDiff)
                            .set(org.crux.data.model.appearance.AppearanceKeys.LineHeightDiff, fontSizeDiff * 2)
                    }
                }
            }
        }
    }
