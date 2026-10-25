package dev.dimension.flare.data.ai.benchmark

import dev.dimension.flare.data.ai.OnDeviceHardwareBackend
import dev.dimension.flare.data.ai.OnDeviceModelSpec
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnDeviceHardwareBenchmarkEngineTest {

    @Test
    fun runsBenchmarkAcrossAllStagesAndCompletes() = runTest {
        val progressEvents = OnDeviceHardwareBenchmarkEngine.runBenchmark(
            modelSpec = OnDeviceModelSpec.Gemma4E2B,
            hardwareBackend = OnDeviceHardwareBackend.Npu,
        ).toList()

        assertTrue(progressEvents.isNotEmpty(), "Benchmark should emit progress events")

        val stages = progressEvents.map { it.stage }.distinct()
        assertTrue(stages.contains(BenchmarkStage.INITIALIZING))
        assertTrue(stages.contains(BenchmarkStage.WEIGHT_MAPPING))
        assertTrue(stages.contains(BenchmarkStage.DENSE_EMBEDDING))
        assertTrue(stages.contains(BenchmarkStage.TTFT_PREFILL))
        assertTrue(stages.contains(BenchmarkStage.MTP_GENERATION))
        assertTrue(stages.contains(BenchmarkStage.COMPLETE))

        val finalEvent = progressEvents.last()
        assertEquals(BenchmarkStage.COMPLETE, finalEvent.stage)
        assertEquals(1.0f, finalEvent.progressPercent)
        val finalSpeed = finalEvent.instantaneousTokensPerSec
        assertTrue(finalSpeed != null && finalSpeed > 0f)
    }

    @Test
    fun computesAccurateBenchmarkResult() {
        val result = OnDeviceHardwareBenchmarkEngine.computeResult(
            modelSpec = OnDeviceModelSpec.Gemma4E2B,
            hardwareBackend = OnDeviceHardwareBackend.Npu,
            measuredTokensPerSec = 52.8f,
            measuredEmbeddingThroughput = 1150f,
        )

        assertEquals("Gemma 4 Edge (E2B)", result.modelSpec.displayName)
        assertEquals(OnDeviceHardwareBackend.Npu, result.hardwareBackend)
        assertEquals(52.8f, result.tokensPerSecond)
        assertEquals(1400, result.memoryFootprintMb)
        assertTrue(result.speculativeAcceptanceRate > 70f, "MTP speculative acceptance rate should be > 70%")
        assertTrue(result.zeroCloudLeakageVerified, "Must guarantee zero cloud leakage")
        assertTrue(result.hardwareTierLabel.contains("NPU"), "Should identify NPU Tier")
    }

    @Test
    fun scalesSpeedForLargerE4BModel() = runTest {
        val eventsE4B = OnDeviceHardwareBenchmarkEngine.runBenchmark(
            modelSpec = OnDeviceModelSpec.Gemma4E4B,
            hardwareBackend = OnDeviceHardwareBackend.Cpu,
        ).toList()

        val finalSpeed = eventsE4B.last().instantaneousTokensPerSec
        assertTrue(finalSpeed != null && finalSpeed > 0f)
    }
}
