package dev.dimension.flare.data.ai

/**
 * Hardware accelerator options for on-device Gemma 4 Edge execution.
 */
public enum class OnDeviceHardwareBackend(
    public val id: String,
    public val label: String,
) {
    Auto("auto", "Auto (GPU / NPU Accelerated)"),
    Gpu("gpu", "GPU (OpenCL / Vulkan)"),
    Npu("npu", "NPU / APU (Neural Processing Unit)"),
    Cpu("cpu", "CPU (Multi-Threaded Vector SIMD)"),
    ;

    public companion object {
        public fun fromId(id: String): OnDeviceHardwareBackend = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: Auto
    }
}

/**
 * Model specification for Google's Gemma 4 Edge (E2B) zero-egress intelligence runtime.
 */
public data class OnDeviceModelSpec(
    val modelId: String = "gemma-4-e2b",
    val displayName: String = "Gemma 4 Edge (E2B)",
    val parameterCount: String = "2.3B",
    val memoryFootprintMb: Int = 1400,
    val containerFormat: String = ".litertlm",
    val multiTokenPrediction: Boolean = true,
    val mtpSpeedupMultiplier: Float = 2.2f,
    val contextWindowTokens: Long = 8192L,
    val zeroEgressGuaranteed: Boolean = true,
    val embeddingDimension: Int = 768,
) {
    public companion object {
        public val Gemma4E2B: OnDeviceModelSpec = OnDeviceModelSpec()
        public val Gemma4E4B: OnDeviceModelSpec =
            OnDeviceModelSpec(
                modelId = "gemma-4-e4b",
                displayName = "Gemma 4 Edge (E4B)",
                parameterCount = "4.1B",
                memoryFootprintMb = 2300,
                mtpSpeedupMultiplier = 2.0f,
            )

        public val SupportedModels: List<OnDeviceModelSpec> =
            listOf(Gemma4E2B, Gemma4E4B)

        public fun findById(id: String): OnDeviceModelSpec =
            SupportedModels.firstOrNull { it.modelId.equals(id, ignoreCase = true) } ?: Gemma4E2B
    }
}
