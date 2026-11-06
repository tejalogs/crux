@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package org.crux.data.datasource.microblog.paging

import org.crux.data.database.cache.dao.DbTimelinePageIdentity
import org.crux.data.database.cache.model.DbStatus
import org.crux.data.database.cache.model.DbTranslation
import org.crux.data.database.cache.model.TranslationDisplayOptions
import org.crux.data.database.cache.model.applyTranslation
import org.crux.model.DbAccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiTimelineV2
import kotlin.concurrent.atomics.AtomicReference

/** The single retained representation shared by the page cache and Paging/UI. */
internal class TimelinePageItem(
    val identity: DbTimelinePageIdentity,
    val baseItem: UiTimelineV2,
    private val translationsByStatusId: Map<String, List<DbTranslation>>,
) {
    val statusId: String
        get() = identity.statusId

    val sortId: Long
        get() = identity.sortId

    private val cachedRender = AtomicReference<CachedRender?>(null)

    fun toUi(options: TranslationDisplayOptions): UiTimelineV2 {
        cachedRender.load()?.takeIf { it.options == options }?.let { return it.item }
        val rendered =
            baseItem.applyTranslation(options) { accountType, statusKey ->
                @Suppress("UNCHECKED_CAST")
                translationsByStatusId[
                    DbStatus.createId(
                        accountType = accountType as DbAccountType,
                        statusKey = statusKey,
                    ),
                ].orEmpty()
            }
        cachedRender.store(CachedRender(options, rendered))
        return rendered
    }

    private data class CachedRender(
        val options: TranslationDisplayOptions,
        val item: UiTimelineV2,
    )
}
