package org.crux.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.crux.data.model.tab.TimelineResolver
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.repository.SettingsRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.model.map

public class PinTabs<T> internal constructor(
    private val pinnedIds: Set<String>,
    private val itemId: (T) -> String,
) {
    public fun contains(item: T): Boolean = itemId(item) in pinnedIds
}

public abstract class PinTabsPresenter<T> : PresenterBase<PinTabsPresenter.State<T>>() {
    private val settingsRepository by koinInject<SettingsRepository>()
    private val timelineResolver by koinInject<TimelineResolver>()
    private val appScope: CoroutineScope by koinInject()

    public interface State<T> {
        public val pins: UiState<PinTabs<T>>

        public fun pinTab(item: T)

        public fun unpinTab(item: T)

        public fun timelineTabItem(item: T): UiTimelineTabItem
    }

    @Composable
    override fun body(): State<T> {
        val tabSettings by settingsRepository.tabSettingsV2.collectAsUiState()
        val pins =
            tabSettings.map {
                PinTabs(
                    pinnedIds = it.homeSlots.mapTo(mutableSetOf()) { slot -> slot.id },
                    itemId = ::getTimelineTabItemId,
                )
            }

        return object : State<T> {
            override val pins = pins

            override fun pinTab(item: T) {
                appScope.launch {
                    settingsRepository.updateTabSettingsV2 {
                        val tabItem = getTimelineTabItem(item)
                        if (homeSlots.any { it.id == tabItem.id }) {
                            return@updateTabSettingsV2 this
                        }
                        copy(
                            homeSlots =
                                homeSlots + timelineResolver.toSlot(tabItem),
                        )
                    }
                }
            }

            override fun unpinTab(item: T) {
                appScope.launch {
                    settingsRepository.updateTabSettingsV2 {
                        val tabItemId = getTimelineTabItemId(item)
                        copy(
                            homeSlots = homeSlots.filterNot { it.id == tabItemId },
                        )
                    }
                }
            }

            override fun timelineTabItem(item: T): UiTimelineTabItem = getTimelineTabItem(item)
        }
    }

    protected abstract fun getTimelineTabItem(item: T): UiTimelineTabItem

    protected open fun getTimelineTabItemId(item: T): String = getTimelineTabItem(item).id
}
