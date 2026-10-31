package dev.dimension.flare.ui.presenter.localfollow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.data.repository.LocalFollowRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Follow or unfollow [userKey] inside Crux. The platform is never told. */
public class EditLocalFollowPresenter(
    private val accountKey: MicroBlogKey,
    private val userKey: MicroBlogKey,
) : PresenterBase<EditLocalFollowPresenter.State>() {
    @Immutable
    public interface State {
        public val groups: UiState<ImmutableList<UiLocalFollowGroup>>
        public val isFollowing: Boolean
        public val groupId: String?

        public fun save(
            profile: UiProfile,
            groupId: String?,
        )

        public fun remove()
    }

    private data class Content(
        val groups: ImmutableList<UiLocalFollowGroup>,
        val isFollowing: Boolean,
        val groupId: String?,
    )

    private val repository: LocalFollowRepository by koinInject()

    private val contentFlow by lazy {
        combine(repository.groups(), repository.follow(accountKey, userKey)) { groups, follow ->
            Content(
                groups = groups.map { UiLocalFollowGroup(it.id, it.name, 0) }.toImmutableList(),
                isFollowing = follow != null,
                groupId = follow?.groupId,
            )
        }
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val content = contentFlow.collectAsUiState().value
        return object : State {
            override val groups = content.map { it.groups }
            override val isFollowing = content.takeSuccess()?.isFollowing == true
            override val groupId = content.takeSuccess()?.groupId

            override fun save(
                profile: UiProfile,
                groupId: String?,
            ) {
                scope.launch { repository.save(accountKey, profile, groupId) }
            }

            override fun remove() {
                scope.launch { repository.remove(accountKey, userKey) }
            }
        }
    }
}
