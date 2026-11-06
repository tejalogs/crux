package org.crux.data.database.app.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import org.crux.model.MicroBlogKey

/** A user-made group of local follows. Stored on this device only. */
@Serializable
@Entity
internal data class DbLocalFollowGroup(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long,
)

/**
 * An account followed inside Crux only: the platform is never told. [accountKey] is the signed-in
 * account used to read [userKey]'s posts. A null [groupId] means ungrouped.
 */
@Serializable
@Entity(primaryKeys = ["accountKey", "userKey"])
internal data class DbLocalFollow(
    val accountKey: MicroBlogKey,
    val userKey: MicroBlogKey,
    val platformId: String,
    val handle: String,
    val displayName: String,
    val avatar: String?,
    val groupId: String?,
    val createdAt: Long,
)
