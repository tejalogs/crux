package org.crux.data.repository

import androidx.room3.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.crux.RobolectricTest
import org.crux.createTestFileSystem
import org.crux.createTestRootPath
import org.crux.data.database.app.AppDatabase
import org.crux.data.database.app.model.DbAccount
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.createDatabaseDriver
import org.crux.data.datastore.AppDataStore
import org.crux.data.io.OkioFileStorage
import org.crux.data.model.tab.TimelineResolver
import org.crux.data.platform.INSTAGRAM_PLATFORM_ID
import org.crux.data.platform.InstagramPlatformSpec
import org.crux.data.platform.LINKEDIN_PLATFORM_ID
import org.crux.data.platform.LinkedInPlatformSpec
import org.crux.deleteTestRootPath
import org.crux.memoryDatabaseBuilder
import org.crux.model.MicroBlogKey
import org.crux.model.PlatformRegistry
import org.crux.model.PlatformRuntimeData
import org.crux.testPlatformRuntimeData
import org.crux.unavailableAccountService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeXRemovalTest : RobolectricTest() {
    private val root = createTestRootPath()
    private val fileStorage = OkioFileStorage(createTestFileSystem(), root)

    private lateinit var appDatabase: AppDatabase
    private lateinit var cacheDatabase: CacheDatabase
    private lateinit var appDataStore: AppDataStore

    @BeforeTest
    fun setup() {
        appDatabase =
            Room
                .memoryDatabaseBuilder<AppDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        cacheDatabase =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        appDataStore = AppDataStore(fileStorage)
    }

    @AfterTest
    fun tearDown() {
        appDatabase.close()
        cacheDatabase.close()
        deleteTestRootPath(root)
    }

    @Test
    fun removesShadowWebAccountsOnlyAfterLiveWebTabsMigrated() =
        runTest {
            val baseRuntime = testPlatformRuntimeData()
            val runtimeData =
                PlatformRuntimeData(
                    platformSpecs = baseRuntime.platformSpecs + listOf(InstagramPlatformSpec, LinkedInPlatformSpec),
                    extraTimelineSpecs = baseRuntime.extraTimelineSpecs,
                )
            val accountRepository =
                AccountRepository(
                    appDatabase = appDatabase,
                    coroutineScope = this as CoroutineScope,
                    appDataStore = appDataStore,
                    cacheDatabase = cacheDatabase,
                    platformRegistry = PlatformRegistry(runtimeData),
                )
            val settingsRepository =
                SettingsRepository(
                    fileStorage = fileStorage,
                    appDataStore = appDataStore,
                    timelineResolver = TimelineResolver(runtimeData, unavailableAccountService()),
                )

            val mastodonKey = MicroBlogKey(id = "alice", host = "example.social")
            val xKey = MicroBlogKey(id = "bob", host = "x.com")
            val igKey = MicroBlogKey(id = "123", host = "instagram.com")
            val liKey = MicroBlogKey(id = "456", host = "linkedin.com")

            listOf(
                mastodonKey to "Mastodon",
                xKey to "xQt",
                igKey to INSTAGRAM_PLATFORM_ID,
                liKey to LINKEDIN_PLATFORM_ID,
            ).forEachIndexed { idx, (key, platformId) ->
                appDatabase.accountDao().insert(
                    DbAccount(
                        account_key = key,
                        credential_json = "{}",
                        platformId = platformId,
                        last_active = idx.toLong(),
                        sort_id = idx.toLong(),
                    ),
                )
            }

            val remover = NativeXRemoval(accountRepository, settingsRepository)
            remover.run()
            assertEquals(
                setOf(mastodonKey, igKey, liKey),
                accountRepository.allAccounts.first().map { it.accountKey }.toSet(),
            )

            settingsRepository.updateAppSettings { copy(liveWebTabsMigrated = true) }
            remover.run()
            assertEquals(
                listOf(mastodonKey),
                accountRepository.allAccounts.first().map { it.accountKey },
            )
        }
}
