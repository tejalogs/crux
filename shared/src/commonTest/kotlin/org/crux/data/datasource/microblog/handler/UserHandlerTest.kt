package org.crux.data.datasource.microblog.handler

import androidx.paging.LoadState
import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import org.crux.RobolectricTest
import org.crux.common.CacheState
import org.crux.common.OnDeviceAI
import org.crux.common.TestFormatter
import org.crux.common.collectAsState
import org.crux.createTestFileSystem
import org.crux.createTestRootPath
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.mapper.toDbUser
import org.crux.data.database.cache.mapper.upsertUser
import org.crux.data.database.cache.model.DbUser
import org.crux.data.database.createDatabaseDriver
import org.crux.data.datasource.microblog.loader.UserLoader
import org.crux.data.datastore.AppDataStore
import org.crux.data.io.OkioFileStorage
import org.crux.data.network.ai.AiCompletionService
import org.crux.data.network.ai.OpenAIService
import org.crux.deleteTestRootPath
import org.crux.di.startKoin
import org.crux.di.testSingle
import org.crux.memoryDatabaseBuilder
import org.crux.model.MicroBlogKey
import org.crux.ui.humanizer.PlatformFormatter
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiHandle
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiState
import org.crux.ui.model.toUi
import org.crux.ui.render.toUiPlainText
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserHandlerTest : RobolectricTest() {
    private val root = createTestRootPath()
    private val fileStorage = OkioFileStorage(createTestFileSystem(), root)

    private lateinit var db: CacheDatabase
    private lateinit var appDataStore: AppDataStore
    private lateinit var loader: FakeUserLoader
    private lateinit var handler: UserHandler
    private lateinit var onDeviceAI: FakeOnDeviceAI

    private val accountKey = MicroBlogKey(id = "account-1", host = "test.social")

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        appDataStore = AppDataStore(fileStorage)

        loader = FakeUserLoader()
        onDeviceAI = FakeOnDeviceAI()

        val scope = CoroutineScope(Dispatchers.Unconfined)
        val openAIService = OpenAIService()
        val aiCompletionService = AiCompletionService(openAIService, onDeviceAI)
        startKoin {
            modules(
                module {
                    testSingle { db }
                    testSingle { appDataStore }
                    testSingle<CoroutineScope> { scope }
                    testSingle<OnDeviceAI> { onDeviceAI }
                    testSingle { openAIService }
                    testSingle { aiCompletionService }
                    testSingle<PlatformFormatter> { TestFormatter() }
                },
            )
        }

        handler = UserHandler(host = accountKey.host, loader = loader)
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
        deleteTestRootPath(root)
    }

    @Test
    fun userByHandleAndHostRefreshStoresAndEmitsUser() =
        runTest {
            val expected = createProfile(id = "alice", host = "test.social", handle = "alice")
            loader.nextByHandleAndHost = expected

            val cacheable =
                handler.userByHandleAndHost(
                    UiHandle(
                        raw = "alice",
                        host = "test.social",
                    ),
                )
            val valueDeferred =
                async {
                    cacheable.data
                        .filterIsInstance<CacheState.Success<UiProfile>>()
                        .first()
                        .data
                }

            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is LoadState.NotLoading)
            assertEquals(expected.key, valueDeferred.await().key)
            assertEquals(1, loader.byHandleCallCount)

            val saved = db.userDao().findByCanonicalHandleAndHost("@alice@test.social", "test.social").first()
            assertNotNull(saved)
            assertEquals(expected.key, saved.userKey)
        }

    @Test
    fun userByHandleResolvesCanonicalNameAndKeepsObservingUser() =
        runTest {
            val expected = createProfile(id = "2100560390937833472", host = "x.com", handle = "NSJunXiu")
            loader.nextByHandleAndHost = expected
            val cacheable = handler.userByHandleAndHost(UiHandle("nsjunxiu", "x.com"))
            val states = Channel<UiState<UiProfile>>(Channel.UNLIMITED)
            backgroundScope.launch { cacheable.toUi().collect { states.send(it) } }

            val resolved = states.receiveAsFlow().first { it !is UiState.Loading }
            assertEquals(expected, assertIs<UiState.Success<UiProfile>>(resolved).data)
            assertEquals(1, loader.byHandleCallCount)
            assertEquals(0, loader.byIdCallCount)

            val updated = expected.copy(handle = UiHandle("JunRenamed", "x.com"))
            db.upsertUser(updated.toDbUser())

            val changed = states.receiveAsFlow().first { it !is UiState.Loading && it != resolved }
            assertEquals(updated, assertIs<UiState.Success<UiProfile>>(changed).data)
        }

    @Test
    fun userByHandleCacheSwitchPublishesNoErrorThroughCompose() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val expected = createProfile(id = "2100560390937833472", host = "x.com", handle = "NSJunXiu")
                loader.nextByHandleAndHost = expected
                val cacheable = handler.userByHandleAndHost(UiHandle("nsjunxiu", "x.com"))
                val resolved =
                    moleculeFlow(RecompositionMode.Immediate) {
                        cacheable.collectAsState().toUi()
                    }.first { it !is UiState.Loading }

                assertEquals(expected, assertIs<UiState.Success<UiProfile>>(resolved).data)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun userByHandleRefreshFailureKeepsResolvedUser() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val expected = createProfile(id = "2100560390937833472", host = "x.com", handle = "NSJunXiu")
            loader.nextByHandleAndHost = expected
            val cacheable = handler.userByHandleAndHost(UiHandle("nsjunxiu", "x.com"))
            val states = Channel<Pair<LoadState, UiState<UiProfile>>>(Channel.UNLIMITED)
            val job =
                launch {
                    moleculeFlow(RecompositionMode.Immediate) {
                        val state = cacheable.collectAsState()
                        state.refreshState to state.toUi()
                    }.collect { states.send(it) }
                }
            try {
                val resolved = states.receiveAsFlow().first { it.first is LoadState.NotLoading && it.second !is UiState.Loading }
                assertEquals(expected, assertIs<UiState.Success<UiProfile>>(resolved.second).data)

                loader.loadByHandle = { error("user lookup failed") }
                cacheable.refresh()
                val failed = states.receiveAsFlow().first { it.first is LoadState.Error }

                assertEquals(expected, assertIs<UiState.Success<UiProfile>>(failed.second).data)
                assertEquals(2, loader.byHandleCallCount)
            } finally {
                job.cancelAndJoin()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun userByHandleQueriesResolveIndependently() =
        runTest {
            val alice = createProfile(id = "alice-id", host = "test.social", handle = "Alice")
            val bob = createProfile(id = "bob-id", host = "test.social", handle = "Bob")
            val aliceResponse = CompletableDeferred<UiProfile>()
            val bobResponse = CompletableDeferred<UiProfile>()
            loader.loadByHandle = {
                when (it.normalizedRaw) {
                    "alice" -> aliceResponse.await()
                    "bob" -> bobResponse.await()
                    else -> error("Unexpected handle: $it")
                }
            }
            val aliceCache = handler.userByHandleAndHost(UiHandle("alice", "test.social"))
            val bobCache = handler.userByHandleAndHost(UiHandle("bob", "test.social"))
            val aliceStates = Channel<UiState<UiProfile>>(Channel.UNLIMITED)
            val bobStates = Channel<UiState<UiProfile>>(Channel.UNLIMITED)
            backgroundScope.launch { aliceCache.toUi().collect { aliceStates.send(it) } }
            backgroundScope.launch { bobCache.toUi().collect { bobStates.send(it) } }
            assertIs<UiState.Loading<UiProfile>>(aliceStates.receive())
            assertIs<UiState.Loading<UiProfile>>(bobStates.receive())

            bobResponse.complete(bob)
            val resolvedBob = bobStates.receiveAsFlow().first { it !is UiState.Loading }
            assertEquals(bob, assertIs<UiState.Success<UiProfile>>(resolvedBob).data)
            aliceResponse.complete(alice)
            val resolvedAlice = aliceStates.receiveAsFlow().first { it !is UiState.Loading }
            assertEquals(alice, assertIs<UiState.Success<UiProfile>>(resolvedAlice).data)

            val cachedBob = bobCache.data.filterIsInstance<CacheState.Success<UiProfile>>().first()
            assertEquals(bob, cachedBob.data)
        }

    @Test
    fun userByIdRefreshStoresAndEmitsUser() =
        runTest {
            val expected = createProfile(id = "bob", host = "test.social", handle = "@bob@test.social")
            loader.nextById = expected

            val cacheable = handler.userById("bob")
            val valueDeferred =
                async {
                    cacheable.data
                        .filterIsInstance<CacheState.Success<UiProfile>>()
                        .first()
                        .data
                }

            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is LoadState.NotLoading)
            assertEquals(expected.key, valueDeferred.await().key)
            assertEquals(1, loader.byIdCallCount)

            val saved = db.userDao().findByKey(MicroBlogKey("bob", "test.social")).first()
            assertNotNull(saved)
            assertEquals(expected.key, saved.userKey)
        }

    @Test
    fun refreshFailureKeepsExistingCachedUser() =
        runTest {
            val existing = createProfile(id = "charlie", host = "test.social", handle = "@charlie@test.social")
            db.userDao().insert(
                DbUser(
                    userKey = existing.key,
                    name = existing.name.raw,
                    canonicalHandle = existing.handle.canonical,
                    host = "test.social",
                    content = existing,
                ),
            )
            loader.failById = true

            val cacheable = handler.userById("charlie")
            val refreshState = cacheable.refreshState.drop(1).first()
            assertTrue(refreshState is LoadState.Error)

            val latest =
                cacheable.data
                    .filterIsInstance<CacheState.Success<UiProfile>>()
                    .first()
                    .data
            assertEquals(existing.key, latest.key)
        }

    @Test
    fun uiHandleQueryMatchesAtHandleInCache() =
        runTest {
            val atHandleProfile = createProfile(id = "david", host = "test.social", handle = "@david@test.social")
            db.userDao().insert(
                DbUser(
                    userKey = atHandleProfile.key,
                    name = atHandleProfile.name.raw,
                    canonicalHandle = atHandleProfile.handle.canonical,
                    host = "test.social",
                    content = atHandleProfile,
                ),
            )

            // UserHandler now normalizes raw+host to canonical before querying cache.
            val uiHandleHit = db.userDao().findByCanonicalHandleAndHost("@david@test.social", "test.social").first()
            assertNotNull(uiHandleHit)
            assertEquals(atHandleProfile.key, uiHandleHit.userKey)

            // Stored shape in many loaders/renderers: "@david@test.social"
            val atHandleHit = db.userDao().findByCanonicalHandleAndHost("@david@test.social", "test.social").first()
            assertNotNull(atHandleHit)
            assertEquals(atHandleProfile.key, atHandleHit.userKey)
        }

    private fun createProfile(
        id: String,
        host: String,
        handle: String,
    ): UiProfile =
        UiProfile(
            key = MicroBlogKey(id = id, host = host),
            handle =
                UiHandle(
                    raw = handle.removePrefix("@").substringBefore("@"),
                    host = host,
                ),
            avatar = "https://$host/$id.png",
            nameInternal = id.toUiPlainText(),
            platformId = "Mastodon",
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0, platformFansCount = "0"),
            mark = persistentListOf(),
            bottomContent = null,
        )

    private class FakeUserLoader : UserLoader {
        var nextByHandleAndHost: UiProfile? = null
        var loadByHandle: suspend (UiHandle) -> UiProfile = { requireNotNull(nextByHandleAndHost) }
        var nextById: UiProfile? = null
        var failById: Boolean = false
        var byHandleCallCount: Int = 0
        var byIdCallCount: Int = 0

        override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
            byHandleCallCount++
            return loadByHandle(uiHandle)
        }

        override suspend fun userById(id: String): UiProfile {
            byIdCallCount++
            if (failById) {
                error("userById failed")
            }
            return requireNotNull(nextById)
        }
    }
}

private class FakeOnDeviceAI : OnDeviceAI {
    override suspend fun isAvailable(): Boolean = true

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null
}
