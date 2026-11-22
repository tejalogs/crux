package org.crux.data.repository

import androidx.room3.Room
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.crux.RobolectricTest
import org.crux.createTestFileSystem
import org.crux.createTestRootPath
import org.crux.data.database.app.AppDatabase
import org.crux.data.database.app.model.DbAccount
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.createDatabaseDriver
import org.crux.data.datasource.microblog.MicroblogDataSource
import org.crux.data.datasource.microblog.ProfileTab
import org.crux.data.datasource.microblog.paging.RemoteLoader
import org.crux.data.datastore.AppDataStore
import org.crux.data.io.OkioFileStorage
import org.crux.data.model.tab.TimelineSpec
import org.crux.deleteTestRootPath
import org.crux.memoryDatabaseBuilder
import org.crux.model.MicroBlogKey
import org.crux.model.PlatformDataSourceContext
import org.crux.model.PlatformDeepLink
import org.crux.model.PlatformMetadata
import org.crux.model.PlatformRegistry
import org.crux.model.PlatformRuntimeData
import org.crux.model.PlatformSpec
import org.crux.ui.model.UiHashtag
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiTimelineV2
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * A credential that cannot be decrypted (an `enc:v1:` value from another install's vault key, or a
 * truncated payload in a crafted backup) must not crash every collector of the account list.
 */
class AccountRepositoryUndecryptableCredentialTest : RobolectricTest() {
    private val root = createTestRootPath()
    private val fileStorage = OkioFileStorage(createTestFileSystem(), root)

    private lateinit var appDatabase: AppDatabase
    private lateinit var cacheDatabase: CacheDatabase
    private lateinit var appDataStore: AppDataStore

    private val brokenKey = MicroBlogKey(id = "broken", host = "fake.example")
    private val healthyKey = MicroBlogKey(id = "healthy", host = "fake.example")

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
    fun undecryptableCredentialDoesNotCrashDataSourceCreation() =
        runTest {
            val spec = CapturingPlatformSpec()
            val repository = createRepository(this, spec)
            insert(brokenKey, credentialJson = "enc:v1:AAAA", platformId = spec.platformId)

            val account = repository.allAccounts.first().single()
            val dataSource = repository.getOrCreateDataSource(account)

            assertSame(StubDataSource, dataSource)
            val context = assertNotNull(spec.lastContext)
            val error =
                assertFailsWith<RequireReLoginException> {
                    context.credential(FakeCredential.serializer())
                }
            assertEquals(brokenKey, error.accountKey)
            assertFailsWith<RequireReLoginException> {
                context.credentialFlow(FakeCredential.serializer()).first()
            }
        }

    @Test
    fun allAccountServicesFlowSkipsAccountsWhoseDataSourceFails() =
        runTest {
            val spec = CapturingPlatformSpec(failFor = setOf(brokenKey))
            val repository = createRepository(this, spec)
            insert(brokenKey, credentialJson = "{}", platformId = spec.platformId, sortId = 0L)
            insert(healthyKey, credentialJson = "{}", platformId = spec.platformId, sortId = 1L)

            val services = allAccountServicesFlow(repository).first()

            assertEquals(listOf<MicroblogDataSource>(StubDataSource), services.toList())
        }

    private fun createRepository(
        scope: CoroutineScope,
        spec: PlatformSpec,
    ): AccountRepository =
        AccountRepository(
            appDatabase = appDatabase,
            coroutineScope = scope,
            appDataStore = appDataStore,
            cacheDatabase = cacheDatabase,
            platformRegistry =
                PlatformRegistry(
                    PlatformRuntimeData(
                        platformSpecs = listOf(spec),
                        extraTimelineSpecs = emptyList(),
                    ),
                ),
        )

    private suspend fun insert(
        key: MicroBlogKey,
        credentialJson: String,
        platformId: String,
        sortId: Long = 0L,
    ) {
        appDatabase.accountDao().insert(
            DbAccount(
                account_key = key,
                credential_json = credentialJson,
                platformId = platformId,
                last_active = 1L,
                sort_id = sortId,
            ),
        )
    }

    @Serializable
    private data class FakeCredential(
        val token: String = "",
    )

    private class CapturingPlatformSpec(
        private val failFor: Set<MicroBlogKey> = emptySet(),
    ) : PlatformSpec {
        var lastContext: PlatformDataSourceContext? = null

        override val platformId: String = "FakeNet"
        override val metadata: PlatformMetadata = PlatformMetadata(platformId, UiIcon.World)
        override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = persistentListOf()

        override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

        override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource {
            if (context.accountKey in failFor) {
                error("data source creation failed for ${context.accountKey}")
            }
            lastContext = context
            return StubDataSource
        }

        override fun guestDataSource(
            host: String,
            locale: String,
        ): MicroblogDataSource = error("Not used")
    }

    private object StubDataSource : MicroblogDataSource {
        override fun homeTimeline(): RemoteLoader<UiTimelineV2> = error("Not used")

        override fun userTimeline(
            userKey: MicroBlogKey,
            mediaOnly: Boolean,
        ): RemoteLoader<UiTimelineV2> = error("Not used")

        override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = error("Not used")

        override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = error("Not used")

        override fun searchUser(query: String): RemoteLoader<UiProfile> = error("Not used")

        override fun discoverUsers(): RemoteLoader<UiProfile> = error("Not used")

        override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = error("Not used")

        override fun discoverHashtags(): RemoteLoader<UiHashtag> = error("Not used")

        override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = error("Not used")

        override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = error("Not used")

        override suspend fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = error("Not used")
    }
}
