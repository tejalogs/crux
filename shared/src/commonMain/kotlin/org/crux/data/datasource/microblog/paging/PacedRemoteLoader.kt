package org.crux.data.datasource.microblog.paging

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Spaces out the start of requests that share this gate, however many callers run at once. */
internal class RequestGate(
    private val minGapMillis: Long,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var lastStart: TimeMark? = null

    suspend fun await() {
        mutex.withLock {
            val elapsed = lastStart?.elapsedNow()?.inWholeMilliseconds
            if (elapsed != null && elapsed < minGapMillis) {
                sleep(minGapMillis - elapsed)
            }
            lastStart = timeSource.markNow()
        }
    }
}

/** Runs [delegate] behind [gate]. Everything else about the loader stays the same. */
internal class PacedRemoteLoader<T : Any>(
    private val delegate: CacheableRemoteLoader<T>,
    private val gate: RequestGate,
) : CacheableRemoteLoader<T> by delegate {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<T> {
        gate.await()
        return delegate.load(pageSize, request)
    }
}
