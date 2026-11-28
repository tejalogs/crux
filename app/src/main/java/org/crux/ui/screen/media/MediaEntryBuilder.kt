package org.crux.ui.screen.media

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import org.crux.ui.route.Route

@OptIn(ExperimentalMaterial3Api::class)
internal fun EntryProviderScope<NavKey>.mediaEntryBuilder(
    @Suppress("UNUSED_PARAMETER") navigate: (Route) -> Unit,
    onBack: () -> Unit,
    uriHandler: UriHandler,
) {
    entry<Route.Media.Image>(
        metadata =
            DialogSceneStrategy.dialog(
                dialogProperties =
                    DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                    ),
            ),
    ) { args ->
        MediaScreen(
            uri = args.uri,
            previewUrl = args.previewUrl,
            customHeaders = args.customHeaders,
            onDismiss = onBack,
            toAltText = {},
        )
    }

    entry<Route.Media.RawMedia>(
        metadata =
            DialogSceneStrategy.dialog(
                dialogProperties =
                    DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                    ),
            ),
    ) { args ->
        RawMediaScreen(
            medias = args.medias,
            index = args.index,
            preview = args.preview,
            onDismiss = onBack,
            toAltText = {},
            uriHandler = uriHandler,
        )
    }
}
