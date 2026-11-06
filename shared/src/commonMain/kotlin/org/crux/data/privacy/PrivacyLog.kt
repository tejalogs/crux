package org.crux.data.privacy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.crux.common.PlatformDispatchers
import kotlin.native.HiddenFromObjC

/**
 * A single recorded request, reduced to what is safe to keep in memory.
 *
 * Only names are retained, never values: a stripped `twclid` is recorded as the literal
 * `"twclid"`, not the identifier. That keeps the log useful for auditing without turning the
 * privacy tool into a second place where tracking identifiers live.
 */
@HiddenFromObjC
public data class PrivacyLogEntry(
    val sequence: Long,
    val host: String,
    val path: String,
    val method: String,
    val category: PrivacyCategory,
    val blocked: Boolean,
    val strippedParameters: List<String>,
    /** Status code when the request completed; null when blocked or still in flight. */
    val status: Int? = null,
) {
    public val isIdentifying: Boolean get() = strippedParameters.isNotEmpty()
}

/**
 * Bounded, in-memory, on-device record of outbound traffic.
 *
 * Deliberately not persisted: a durable traffic history would itself become a sensitive dataset
 * and would need its own retention rules. The log exists to answer "what did this app just send,
 * and what did it remove", which only needs recent history.
 */
@HiddenFromObjC
public class PrivacyLog private constructor() {
    private val scope = CoroutineScope(PlatformDispatchers.IO)
    private val _entries = MutableStateFlow<List<PrivacyLogEntry>>(emptyList())
    private var nextSequence = 0L

    public val entries: StateFlow<List<PrivacyLogEntry>> = _entries.asStateFlow()

    public companion object {
        private const val MAX_ENTRIES = 500
        private var instance: PrivacyLog? = null

        public fun get(): PrivacyLog = instance ?: PrivacyLog().also { instance = it }
    }

    /**
     * Records a request attempt and returns its sequence.
     *
     * Returning the sequence lets the response hook attach a status code to the same row rather
     * than appending a second entry for one logical request.
     */
    public fun record(
        verdict: PrivacyVerdict,
        strippedParameters: List<String> = emptyList(),
        status: Int? = null,
    ): Long {
        val entry =
            PrivacyLogEntry(
                sequence = nextSequence++,
                host = verdict.host,
                path = verdict.path,
                method = verdict.method,
                category = verdict.category,
                blocked = verdict.blocked,
                strippedParameters = strippedParameters,
                status = status,
            )
        scope.launch {
            _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        }
        return entry.sequence
    }

    /**
     * Attaches a status code to an already-recorded attempt.
     *
     * No-op when the entry has aged out of the bounded window, which is the correct outcome: the
     * request still happened, the detail simply is no longer retained.
     */
    internal fun attachStatus(
        sequence: Long,
        status: Int,
    ) {
        scope.launch {
            _entries.value =
                _entries.value.map { entry ->
                    if (entry.sequence == sequence && entry.status == null) {
                        entry.copy(status = status)
                    } else {
                        entry
                    }
                }
        }
    }

    public fun clear() {
        scope.launch { _entries.value = emptyList() }
    }

    /**
     * Per-host aggregate, most recent first.
     *
     * This is the view that answers "which companies am I actually talking to".
     */
    public fun hostSummary(): List<HostSummary> {
        val current = _entries.value
        val grouped = current.groupBy { it.host }
        return grouped
            .map { (host, hostEntries) ->
                HostSummary(
                    host = host,
                    total = hostEntries.size,
                    telemetry = hostEntries.count { it.category == PrivacyCategory.Telemetry },
                    profiling = hostEntries.count { it.category == PrivacyCategory.Profiling },
                    blocked = hostEntries.count { it.blocked },
                    stripped = hostEntries.sumOf { it.strippedParameters.size },
                    lastSeenSequence = hostEntries.maxOf { it.sequence },
                )
            }.sortedByDescending { it.lastSeenSequence }
    }
}

@HiddenFromObjC
public data class HostSummary(
    val host: String,
    val total: Int,
    val telemetry: Int,
    val profiling: Int,
    val blocked: Int,
    val stripped: Int,
    val lastSeenSequence: Long,
)
