package org.crux.ui.screen.status

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.crux.ui.component.BottomSheetSceneStrategy
import androidx.navigation3.scene.DialogSceneStrategy
import org.crux.ui.route.Route
import org.crux.ui.screen.status.action.AltTextSheet
import org.crux.ui.screen.status.action.DeleteStatusConfirmDialog
import org.crux.ui.screen.status.action.OmniScribeBottomSheet
import org.crux.ui.screen.status.action.SmartReplyBottomSheet
import org.crux.ui.screen.status.action.StatusInsightSheet
import org.crux.ui.screen.status.action.StatusShareSheet

@OptIn(ExperimentalMaterial3Api::class)
internal fun EntryProviderScope<NavKey>.statusEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.Status.Detail> { args ->
        StatusScreen(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.AltText>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) { args ->
        AltTextSheet(
            text = args.text,
            onBack = onBack
        )
    }

    entry<Route.Status.DeleteConfirm>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        DeleteStatusConfirmDialog(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack
        )
    }

    entry<Route.Status.ShareSheet>(
        metadata = BottomSheetSceneStrategy.bottomSheet(
            expandFully = true,
        )
    ) { args ->
        StatusShareSheet(
            statusKey = args.statusKey,
            accountType = args.accountType,
            shareUrl = args.shareUrl,
            fxShareUrl = args.fxShareUrl,
            fixvxShareUrl = args.fixvxShareUrl,
            onBack = onBack
        )
    }

    entry<Route.Status.Insight>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) { args ->
        StatusInsightSheet(
            accountType = args.accountType,
            statusKey = args.statusKey,
            navigate = navigate,
        )
    }

    entry<Route.Status.OmniScribe>(
        metadata = BottomSheetSceneStrategy.bottomSheet(
            expandFully = true,
        )
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
        metadata = BottomSheetSceneStrategy.bottomSheet(
            expandFully = true,
        )
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
