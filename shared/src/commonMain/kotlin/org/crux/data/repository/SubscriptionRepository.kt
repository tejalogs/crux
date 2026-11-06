package org.crux.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.crux.data.database.app.AppDatabase
import org.crux.data.database.app.model.DbRssSources
import org.crux.data.database.app.model.RssDisplayMode
import org.crux.data.database.app.model.SubscriptionType
import org.crux.ui.model.UiRssSource
import org.crux.ui.model.mapper.render
import org.crux.ui.render.toUi
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

@HiddenFromObjC
public data class SubscriptionSourceInput(
    val id: Int = 0,
    val url: String,
    val title: String?,
    val icon: String?,
    val displayMode: RssDisplayMode = RssDisplayMode.FULL_CONTENT,
    val lastUpdateMillis: Long = 0,
    val type: SubscriptionType = SubscriptionType.RSS,
)

@HiddenFromObjC
public interface SubscriptionRepository {
    public fun observeAll(): Flow<List<UiRssSource>>

    public fun observe(id: Int): Flow<UiRssSource>

    public suspend fun findByUrl(url: String): List<UiRssSource>

    public suspend fun findByUrlAndType(
        url: String,
        type: SubscriptionType,
    ): List<UiRssSource>

    public suspend fun upsert(input: SubscriptionSourceInput): UiRssSource

    public suspend fun upsertAll(inputs: List<SubscriptionSourceInput>): List<UiRssSource>

    public suspend fun delete(id: Int): UiRssSource?

    /** Adds the starter feeds the first time only, and only when there are no feeds yet. */
    public suspend fun ensureDefaultFeedsSeeded(): List<UiRssSource>

    /** Adds the starter feeds on request, skipping any that are already subscribed. */
    public suspend fun restoreDefaultFeeds(): List<UiRssSource>
}

@Single(binds = [SubscriptionRepository::class])
internal class DatabaseSubscriptionRepository(
    private val appDatabase: AppDatabase,
    // Lazy: SettingsRepository reaches back here through the timeline resolver.
    private val lazySettingsRepository: Lazy<SettingsRepository>,
) : SubscriptionRepository {
    private val settingsRepository by lazySettingsRepository

    // Only RSS feeds can be loaded. Rows of the removed Mastodon types stay in the table but are never listed.
    override fun observeAll(): Flow<List<UiRssSource>> =
        appDatabase
            .rssSourceDao()
            .getAllByType(SubscriptionType.RSS)
            .map { sources -> sources.map { it.render() } }

    override fun observe(id: Int): Flow<UiRssSource> =
        appDatabase
            .rssSourceDao()
            .get(id)
            .map { it.render() }

    override suspend fun findByUrl(url: String): List<UiRssSource> =
        appDatabase
            .rssSourceDao()
            .getByUrl(url)
            .map { it.render() }

    override suspend fun findByUrlAndType(
        url: String,
        type: SubscriptionType,
    ): List<UiRssSource> =
        appDatabase
            .rssSourceDao()
            .getByUrlAndType(url, type)
            .map { it.render() }

    override suspend fun upsert(input: SubscriptionSourceInput): UiRssSource {
        val stored = appDatabase.rssSourceDao().insertIfNew(input.toDatabase())
        return if (stored.id != 0 && stored.id != input.id) stored.render() else input.toUiRssSource()
    }

    override suspend fun upsertAll(inputs: List<SubscriptionSourceInput>): List<UiRssSource> =
        appDatabase
            .rssSourceDao()
            .insertAllIfNew(inputs.map { it.toDatabase() })
            .mapIndexed { index, stored ->
                if (stored.id != 0 && stored.id != inputs[index].id) stored.render() else inputs[index].toUiRssSource()
            }

    override suspend fun delete(id: Int): UiRssSource? {
        val source =
            appDatabase
                .rssSourceDao()
                .getAll()
                .first()
                .firstOrNull { it.id == id }
                ?.render()
        appDatabase.rssSourceDao().delete(id)
        return source
    }

    override suspend fun ensureDefaultFeedsSeeded(): List<UiRssSource> {
        val dao = appDatabase.rssSourceDao()
        dao.deleteAllExceptType(SubscriptionType.RSS)
        if (!settingsRepository.appSettings.first().rssDefaultsSeeded) {
            if (dao.getAllOnce().isEmpty()) {
                upsertAll(DefaultRssFeeds.allFeeds.map { it.toSubscriptionSourceInput() })
            }
            settingsRepository.updateAppSettings { copy(rssDefaultsSeeded = true) }
        }
        return observeAll().first()
    }

    override suspend fun restoreDefaultFeeds(): List<UiRssSource> {
        upsertAll(DefaultRssFeeds.allFeeds.map { it.toSubscriptionSourceInput() })
        settingsRepository.updateAppSettings { copy(rssDefaultsSeeded = true) }
        return observeAll().first()
    }
}

private fun SubscriptionSourceInput.toDatabase(): DbRssSources =
    DbRssSources(
        id = id,
        url = url,
        title = title,
        icon = icon,
        displayMode = displayMode,
        lastUpdate = lastUpdateMillis,
        type = type,
    )

@HiddenFromObjC
public fun SubscriptionSourceInput.toUiRssSource(): UiRssSource =
    UiRssSource(
        id = id,
        url = url,
        title = title,
        favIcon = icon,
        displayMode = displayMode,
        lastUpdate = Instant.fromEpochMilliseconds(lastUpdateMillis).toUi(),
        type = type,
    )
