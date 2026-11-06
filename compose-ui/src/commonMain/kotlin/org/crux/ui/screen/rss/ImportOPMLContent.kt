package org.crux.ui.screen.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.crux.ui.component.NetworkImage
import org.crux.ui.component.listCard
import org.crux.ui.component.platform.PlatformLinearProgressIndicator
import org.crux.ui.component.platform.PlatformListItem
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.presenter.home.rss.ImportOPMLPresenter
import org.crux.ui.theme.PlatformTheme
import org.crux.ui.theme.screenHorizontalPadding

@Composable
public fun ImportOPMLContent(
    state: ImportOPMLPresenter.State,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (state.error != null) {
            Box(
                contentAlignment = Alignment.Center,
            ) {
                PlatformText(
                    text = state.error!!,
                    color = PlatformTheme.colorScheme.error,
                )
            }
        } else {
            if (state.importing) {
                PlatformLinearProgressIndicator(
                    progress = { state.progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = screenHorizontalPadding),
                )
            }
            LazyColumn(
                modifier =
                    Modifier
                        .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding =
                    PaddingValues(
                        vertical = 8.dp,
                        horizontal = screenHorizontalPadding,
                    ),
            ) {
                itemsIndexed(state.importedSources) { index, item ->
                    PlatformListItem(
                        modifier = Modifier.listCard(index, state.importedSources.size),
                        headlineContent = {
                            PlatformText(text = item.title ?: item.url)
                        },
                        supportingContent = {
                            PlatformText(text = item.url)
                        },
                        leadingContent = {
                            if (item.favIcon != null) {
                                NetworkImage(
                                    model = item.favIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
