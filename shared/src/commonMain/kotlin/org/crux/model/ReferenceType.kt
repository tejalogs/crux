package org.crux.model

import kotlinx.serialization.Serializable

@Serializable
public enum class ReferenceType {
    Retweet,
    Reply,
    Quote,
    Notification,
}
