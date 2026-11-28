package org.crux.data.database.app.model

import kotlinx.serialization.Serializable

/** Kept for ProtoBuf deserialization of legacy TabSettings slots (`rss.subscription`). */
@Serializable
public enum class SubscriptionType {
    RSS,
    MASTODON_TRENDS,
    MASTODON_PUBLIC,
    MASTODON_LOCAL,
}
