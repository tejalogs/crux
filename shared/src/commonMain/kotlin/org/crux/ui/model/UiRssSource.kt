package org.crux.ui.model

import androidx.compose.runtime.Immutable
import io.ktor.http.Url
import org.crux.data.database.app.model.RssDisplayMode
import org.crux.data.database.app.model.SubscriptionType
import org.crux.ui.render.UiDateTime

@Immutable
public data class UiRssSource public constructor(
    val id: Int,
    val url: String,
    val title: String?,
    val lastUpdate: UiDateTime,
    val favIcon: String?,
    val displayMode: RssDisplayMode = RssDisplayMode.FULL_CONTENT,
    val type: SubscriptionType = SubscriptionType.RSS,
) {
    val host: String by lazy {
        when (type) {
            SubscriptionType.RSS -> Url(url).host
            else -> url // Non-RSS subscription types store the host directly
        }
    }
}
