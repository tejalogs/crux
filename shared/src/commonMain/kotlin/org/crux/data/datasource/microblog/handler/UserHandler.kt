package org.crux.data.datasource.microblog.handler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import org.crux.common.Cacheable
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.mapper.toDbUser
import org.crux.data.database.cache.mapper.upsertUser
import org.crux.data.datasource.microblog.loader.UserLoader
import org.crux.di.koinInject
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiHandle
import org.crux.ui.model.UiProfile
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class UserHandler(
    private val host: String,
    private val loader: UserLoader,
) {
    private val database: CacheDatabase by koinInject()

    @OptIn(ExperimentalCoroutinesApi::class)
    public fun userByHandleAndHost(uiHandle: UiHandle): Cacheable<UiProfile> {
        val resolvedKey = MutableStateFlow<MicroBlogKey?>(null)
        return Cacheable(
            fetchSource = {
                val user = loader.userByHandleAndHost(uiHandle)
                database.upsertUser(
                    user.toDbUser(),
                )
                resolvedKey.value = user.key
            },
            cacheSource = {
                resolvedKey
                    .flatMapLatest { key ->
                        if (key != null) {
                            database.userDao().findByKey(key)
                        } else {
                            database.userDao().findByCanonicalHandleAndHost(
                                canonicalHandle = uiHandle.canonical,
                                host = uiHandle.normalizedHost,
                            )
                        }
                    }.distinctUntilChanged()
                    .mapNotNull { it?.content }
            },
        )
    }

    public fun userById(id: String): Cacheable<org.crux.ui.model.UiProfile> =
        Cacheable(
            fetchSource = {
                val user = loader.userById(id)
                database.upsertUser(
                    user.toDbUser(),
                )
            },
            cacheSource = {
                database
                    .userDao()
                    .findByKey(
                        MicroBlogKey(
                            id = id,
                            host = host,
                        ),
                    ).distinctUntilChanged()
                    .mapNotNull { it?.content }
            },
        )
}
