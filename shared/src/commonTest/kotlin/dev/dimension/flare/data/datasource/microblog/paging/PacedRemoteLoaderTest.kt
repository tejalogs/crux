package dev.dimension.flare.data.datasource.microblog.paging

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class PacedRemoteLoaderTest {
    private fun gate(
        time: TestTimeSource,
        sleeps: MutableList<Long>,
    ) = RequestGate(
        minGapMillis = 2_000,
        timeSource = time,
        sleep = {
            sleeps += it
            time += it.milliseconds
        },
    )

    @Test
    fun firstRequestDoesNotWait() =
        runTest {
            val sleeps = mutableListOf<Long>()
            gate(TestTimeSource(), sleeps).await()
            assertEquals(emptyList(), sleeps)
        }

    @Test
    fun laterRequestsWaitOutTheGap() =
        runTest {
            val time = TestTimeSource()
            val sleeps = mutableListOf<Long>()
            val gate = gate(time, sleeps)
            gate.await()
            time += 500.milliseconds
            gate.await()
            gate.await()
            assertEquals(listOf(1_500L, 2_000L), sleeps)
        }

    @Test
    fun noWaitOnceTheGapHasPassed() =
        runTest {
            val time = TestTimeSource()
            val sleeps = mutableListOf<Long>()
            val gate = gate(time, sleeps)
            gate.await()
            time += 3_000.milliseconds
            gate.await()
            assertEquals(emptyList(), sleeps)
        }

    @Test
    fun loaderPassesThroughBehindTheGate() =
        runTest {
            val sleeps = mutableListOf<Long>()
            val loader =
                PacedRemoteLoader(
                    delegate =
                        object : CacheableRemoteLoader<String> {
                            override val pagingKey = "key"

                            override suspend fun load(
                                pageSize: Int,
                                request: PagingRequest,
                            ) = PagingResult(data = listOf("post"))
                        },
                    gate = gate(TestTimeSource(), sleeps),
                )
            assertEquals("key", loader.pagingKey)
            assertEquals(listOf("post"), loader.load(20, PagingRequest.Refresh).data)
        }
}
