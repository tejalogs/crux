package org.crux.data.datasource.microblog.handler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.crux.common.Cacheable
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.connect
import org.crux.data.database.cache.mapper.saveToDatabase
import org.crux.data.database.cache.model.DbStatus
import org.crux.data.datasource.microblog.loader.PostLoader
import org.crux.data.datasource.microblog.paging.TimelinePagingMapper
import org.crux.data.repository.tryRun
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.DbAccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class PostHandler(
    public val accountType: AccountType,
    public val loader: PostLoader,
) {
    private val database: CacheDatabase by koinInject()
    private val coroutineScope: CoroutineScope by koinInject()

    public fun post(postKey: MicroBlogKey): Cacheable<UiTimelineV2> {
        val pagingKey = "post_only_$postKey"
        return Cacheable(
            fetchSource = {
                val result = loader.status(postKey)
                database.connect {
                    val item =
                        TimelinePagingMapper.toDb(
                            result,
                            pagingKey,
                        )
                    saveToDatabase(database, listOf(item))
                }
            },
            cacheSource = {
                val dbAccountType = accountType as DbAccountType
                combine(
                    database
                        .statusDao()
                        .getWithReferences(postKey, dbAccountType),
                    database
                        .pagingTimelineDao()
                        .get(pagingKey, accountType = dbAccountType),
                ) { status, timelineStatus ->
                    timelineStatus?.let {
                        TimelinePagingMapper.toUi(
                            item = it,
                            pagingKey = pagingKey,
                        )
                    } ?: status?.let {
                        TimelinePagingMapper.toUi(
                            item = it,
                            pagingKey = pagingKey,
                        )
                    }
                }.filterNotNull()
                    .distinctUntilChanged()
            },
        )
    }

    public fun delete(postKey: MicroBlogKey): Job =
        coroutineScope.launch {
            tryRun {
                loader.deleteStatus(postKey)
            }.onSuccess {
                database.connect {
                    val dbAccountType = accountType as DbAccountType
                    database.pagingTimelineDao().deleteStatus(
                        accountType = dbAccountType,
                        statusId = DbStatus.createId(dbAccountType, postKey),
                    )
                    database.statusDao().delete(
                        statusKey = postKey,
                        accountType = dbAccountType,
                    )
                    database.statusReferenceDao().delete(DbStatus.createId(dbAccountType, postKey))
                }
            }
        }
}
