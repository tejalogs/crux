package org.crux.ui.screen.status

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.crux.ui.component.BottomSheetSceneStrategy
import org.crux.ui.route.Route
import org.crux.ui.screen.status.action.OmniScribeBottomSheet
import org.crux.ui.screen.status.action.SmartReplyBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
internal fun EntryProviderScope<NavKey>.statusEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.Status.OmniScribe>(
        metadata =
            BottomSheetSceneStrategy.bottomSheet(
                expandFully = true,
            ),
    ) { args ->
        OmniScribeBottomSheet(
            statusKey = args.statusKey,
            accountType = args.accountType,
            initialText = args.initialText,
            authorName = args.authorName,
            authorHandle = args.authorHandle,
            articleTitle = args.articleTitle,
            sourceUrl = args.sourceUrl,
            navigate = navigate,
            onBack = onBack,
        )
    }

    entry<Route.Status.SmartReply>(
        metadata =
            BottomSheetSceneStrategy.bottomSheet(
                expandFully = true,
            ),
    ) { args ->
        SmartReplyBottomSheet(
            statusKey = args.statusKey,
            accountType = args.accountType,
            initialText = args.initialText,
            authorName = args.authorName,
            authorHandle = args.authorHandle,
            mediaDescription = args.mediaDescription,
            parentContext = args.parentContext,
            navigate = navigate,
            onBack = onBack,
        )
    }
}
