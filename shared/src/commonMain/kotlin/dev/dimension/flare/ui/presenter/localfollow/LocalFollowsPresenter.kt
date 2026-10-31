package dev.dimension.flare.ui.presenter.localfollow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.data.model.tab.LocalFollowTimelineSpecs
import dev.dimension.flare.data.model.tab.LocalFollowsTimelineData
import dev.dimension.flare.data.model.tab.toUiTimelineTabItem
import dev.dimension.flare.data.repository.LocalFollowRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Immutable
public data class UiLocalFollow(
    val accountKey: MicroBlogKey,
    val userKey: MicroBlogKey,
    val handle: String,
    val displayName: String,
    val avatar: String?,
    val groupId: String?,
)

@Immutable
public data class UiLocalFollowGroup(
    val id: String,
    val name: String,
    val count: Int,
)

public class LocalFollowsPresenter : PresenterBase<LocalFollowsPresenter.State>() {
    @Immutable
    public interface State {
        public val groups: UiState<ImmutableList<UiLocalFollowGroup>>
        public val follows: UiState<ImmutableList<UiLocalFollow>>

        public fun createGroup(name: String)

        public fun renameGroup(
            id: String,
            name: String,
        )

        public fun deleteGroup(id: String)

        public fun remove(follow: UiLocalFollow)

        /** Pins a group to the home tabs; a null [groupId] pins every local follow. */
        public fun pinToHome(
            groupId: String?,
            title: String,
        )
    }

    private val repository: LocalFollowRepository by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()

    private val contentFlow by lazy {
        combine(repository.groups(), repository.follows()) { groups, follows ->
            val counts = follows.groupingBy { it.groupId }.eachCount()
            groups.map { UiLocalFollowGroup(it.id, it.name, counts[it.id] ?: 0) }.toImmutableList() to
                follows
                    .map {
                        UiLocalFollow(
                            accountKey = it.accountKey,
                            userKey = it.userKey,
                            handle = it.handle,
                            displayName = it.displayName,
                            avatar = it.avatar,
                            groupId = it.groupId,
                        )
                    }.toImmutableList()
        }
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val content = contentFlow.collectAsUiState().value
        return object : State {
            override val groups = content.map { it.first }
            override val follows = content.map { it.second }

            override fun createGroup(name: String) {
                if (name.isBlank()) return
                scope.launch { repository.createGroup(name) }
            }

            override fun renameGroup(
                id: String,
                name: String,
            ) {
                if (name.isBlank()) return
                scope.launch { repository.renameGroup(id, name) }
            }

            override fun deleteGroup(id: String) {
                scope.launch { repository.deleteGroup(id) }
            }

            override fun remove(follow: UiLocalFollow) {
                scope.launch { repository.remove(follow.accountKey, follow.userKey) }
            }

            override fun pinToHome(
                groupId: String?,
                title: String,
            ) {
                scope.launch {
                    val tab =
                        LocalFollowTimelineSpecs.follows
                            .candidate(
                                data = LocalFollowsTimelineData(groupId),
                                title = UiText.Raw(title),
                            ).toUiTimelineTabItem()
                    val tabs = settingsRepository.homeTimelineTabs.first()
                    if (tabs.none { it.id == tab.id }) {
                        settingsRepository.replaceHomeTimelineTabs(tabs + tab)
                    }
                }
            }
        }
    }
}
