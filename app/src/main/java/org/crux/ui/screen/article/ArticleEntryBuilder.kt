package org.crux.ui.screen.article

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.crux.ui.route.Route

internal fun EntryProviderScope<NavKey>.articleEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.Article> { args ->
        ArticleScreen(
            accountType = args.accountType,
            articleKey = args.articleKey,
            navigate = navigate,
            onBack = onBack,
        )
    }
}
