package org.crux.data.privacy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.crux.data.datastore.AppDataStore
import org.crux.data.datastore.model.PrivacySettingsData
import org.koin.core.annotation.Single

/**
 * Durable owner of [PrivacySettings].
 *
 * The Ktor request pipeline reads policy synchronously on every request, so the hot path is a
 * cached [StateFlow] rather than a datastore read. Writes go to the datastore first and then
 * update that cache, which means a failed write leaves the cached policy matching what is
 * actually on disk instead of silently diverging.
 */
@Single
public class PrivacySettingsRepository internal constructor(
    private val appDataStore: AppDataStore,
) {
    private val scope = CoroutineScope(org.crux.common.PlatformDispatchers.IO)
    private val store get() = appDataStore.privacySettingsStore

    /**
     * Cached policy, seeded from disk.
     *
     * Starts at defaults so a request made before the first disk read cannot observe a
     * half-initialised state; [observe] replaces it as soon as the real value is available.
     */
    private val cached: MutablePrivacyState = MutablePrivacyState()

    public fun settings(): StateFlow<PrivacySettings> = cached.flow

    /** Loads the persisted value and keeps the cache in step with later external writes. */
    public suspend fun observe() {
        runCatching {
            store.data.collect { data ->
                val next = data.toPrivacySettings()
                cached.update(next)
                // Publish to the synchronous read path the request pipeline uses.
                PrivacyPolicyCache.install { next }
            }
        }
    }

    /** Loads once, for callers that need the value before the first flow emission. */
    public suspend fun loadOnce(): PrivacySettings =
        runCatching { store.data.first().toPrivacySettings() }
            .getOrElse { PrivacySettings.Default }
            .also {
                cached.update(it)
                PrivacyPolicyCache.install { it }
            }

    /** Persists [settings], then updates the cache so the next request observes it. */
    public suspend fun update(settings: PrivacySettings) {
        runCatching { store.updateData { PrivacySettingsData.from(settings) } }
        cached.update(settings)
        PrivacyPolicyCache.install { settings }
    }

    /** Convenience for the single-value edits the UI performs. */
    public suspend fun mutate(transform: (PrivacySettings) -> PrivacySettings) {
        update(transform(cached.current()))
    }
}

/**
 * Minimal observable holder.
 *
 * Hand-rolled so the repository owns the lifetime and the plugin keeps a lock-free read path.
 */
internal class MutablePrivacyState {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(PrivacySettings.Default)

    val flow: StateFlow<PrivacySettings> = state

    fun current(): PrivacySettings = state.value

    fun update(settings: PrivacySettings) {
        state.value = settings
    }
}

/**
 * Process-wide access to the cached policy.
 *
 * Assigned once during startup by [PrivacySettingsRepository.observe]; the plugin only reads it,
 * so it never needs a coroutine or a disk round-trip.
 */
public object PrivacyPolicyCache {
    private var provider: (() -> PrivacySettings)? = null

    internal fun install(provider: () -> PrivacySettings) {
        this.provider = provider
    }

    /** Current policy, or defaults when the repository has not been wired up yet. */
    public fun current(): PrivacySettings = provider?.invoke() ?: PrivacySettings.Default
}
