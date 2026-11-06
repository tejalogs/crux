package org.crux.data.datasource.microblog.handler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import org.crux.common.Cacheable
import org.crux.common.Locale
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.mapper.toDbUser
import org.crux.data.database.cache.mapper.upsertUser
import org.crux.data.database.cache.model.TranslationEntityType
import org.crux.data.database.cache.model.applyTranslation
import org.crux.data.database.cache.model.translationEntityKey
import org.crux.data.datasource.microblog.loader.UserLoader
import org.crux.data.datastore.AppDataStore
import org.crux.data.translation.PreTranslationService
import org.crux.data.translation.TranslationSettingsSupport
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
    private val appDataStore: AppDataStore by koinInject()
    private val preTranslationService: PreTranslationService by koinInject()

    private val translationDisplayFlow by lazy {
        TranslationSettingsSupport.displayOptionsFlow(appDataStore)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    public fun userByHandleAndHost(uiHandle: UiHandle): Cacheable<UiProfile> {
        val resolvedKey = MutableStateFlow<MicroBlogKey?>(null)
        return Cacheable(
            fetchSource = {
                val user = loader.userByHandleAndHost(uiHandle)
                val dbUser = user.toDbUser()
                database.upsertUser(
                    dbUser,
                )
                resolvedKey.value = user.key
                preTranslationService.enqueueProfile(dbUser)
            },
            cacheSource = {
                translatedUserFlow(
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
                        }.distinctUntilChanged(),
                )
            },
        )
    }

    public fun userById(id: String): Cacheable<org.crux.ui.model.UiProfile> =
        Cacheable(
            fetchSource = {
                val user = loader.userById(id)
                val dbUser = user.toDbUser()
                database.upsertUser(
                    dbUser,
                )
                preTranslationService.enqueueProfile(dbUser)
            },
            cacheSource = {
                translatedUserFlow(
                    database
                        .userDao()
                        .findByKey(
                            MicroBlogKey(
                                id = id,
                                host = host,
                            ),
                        ).distinctUntilChanged(),
                )
            },
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun translatedUserFlow(userFlow: kotlinx.coroutines.flow.Flow<org.crux.data.database.cache.model.DbUser?>) =
        combine(userFlow, translationDisplayFlow) { user, translationDisplayOptions ->
            user to translationDisplayOptions
        }.flatMapLatest { (user, translationDisplayOptions) ->
            if (user == null || !translationDisplayOptions.autoDisplayEnabled) {
                flowOf(user?.content)
            } else {
                combine(
                    flowOf(user),
                    database
                        .translationDao()
                        .find(
                            entityType = TranslationEntityType.Profile,
                            entityKey = user.translationEntityKey(),
                            targetLanguage = Locale.language,
                        ),
                ) { dbUser, translation ->
                    dbUser.content.applyTranslation(
                        options = translationDisplayOptions,
                        translation = translation,
                    )
                }
            }
        }.mapNotNull { it }
}
