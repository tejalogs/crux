package org.crux.data.model.tab

import kotlinx.serialization.Serializable
import org.crux.data.database.app.model.SubscriptionType

@Serializable
public data class RssTimelineData(
    val feedUrl: String,
) : TimelineSpec.Data

@Serializable
public data object AllRssTimelineData : TimelineSpec.Data

@Serializable
public data class SubscriptionTimelineData(
    val subscriptionUrl: String,
    val subscriptionType: SubscriptionType,
) : TimelineSpec.Data
