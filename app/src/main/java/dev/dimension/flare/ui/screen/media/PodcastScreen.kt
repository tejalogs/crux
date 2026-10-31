package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.PodcastManager
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.UiPodcast
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@Composable
internal fun ColumnScope.PodcastContent(
    data: UiPodcast,
    toUser: (MicroBlogKey) -> Unit,
    isPlaying: UiState<Boolean>,
    onLeavePodcast: () -> Unit,
    onJoinPodcast: () -> Unit,
) {
    Text(data.title, style = MaterialTheme.typography.titleSmall)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(72.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(data.hosts) { item ->
            UserItem(
                item = item,
                modifier =
                    Modifier.clickable {
                        toUser(item.key)
                    },
            ) {
                Text(stringResource(R.string.podcast_host))
            }
        }
        items(data.speakers) { item ->
            UserItem(
                item = item,
                modifier =
                    Modifier.clickable {
                        toUser(item.key)
                    },
            ) {
                Text(stringResource(R.string.podcast_speaker))
            }
        }
    }
    if (data.ended) {
        Button(
            onClick = {
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
        ) {
            Text(stringResource(R.string.podcast_ended))
        }
    } else {
        isPlaying.onSuccess {
            if (it) {
                Button(
                    onClick = {
                        onLeavePodcast()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.podcast_leave))
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        onJoinPodcast()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.podcast_listen))
                }
            }
        }
    }
}

@Composable
private fun UserItem(
    item: UiProfile,
    modifier: Modifier = Modifier,
    bottom: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        AvatarComponent(
            data = item.avatar,
        )
        RichText(
            text = item.name,
            maxLines = 1,
        )
        bottom.invoke()
    }
}
