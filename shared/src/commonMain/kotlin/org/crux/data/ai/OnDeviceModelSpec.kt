package org.crux.data.ai

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
 * Model specification for open-weights `.litertlm` on-device inference models.
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
    val fileName: String = "gemma-4-E2B-it.litertlm",
    val downloadUrl: String =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
    val downloadSizeBytes: Long = 2_588_147_712L,
    val licenseName: String = "Apache-2.0",
    val summaryDescription: String =
        "Balanced Gemma 4 open-weights model for smart replies, summaries, and local chat",
    val disableThinkingTemplate: Boolean = false,
) {
    public fun formattedDownloadSize(): String = formatModelBytes(downloadSizeBytes)

    public companion object {
        public val Gemma4E2B: OnDeviceModelSpec = OnDeviceModelSpec()
        public val Gemma4E4B: OnDeviceModelSpec =
            OnDeviceModelSpec(
                modelId = "gemma-4-e4b",
                displayName = "Gemma 4 Edge (E4B)",
                parameterCount = "4.1B",
                memoryFootprintMb = 2300,
                mtpSpeedupMultiplier = 2.0f,
                fileName = "gemma-4-E4B-it.litertlm",
                downloadUrl =
                    "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true",
                downloadSizeBytes = 3_659_530_240L,
                summaryDescription =
                    "Higher-capacity Gemma 4 model for deeper reasoning and synthesis (8 GB+ RAM recommended)",
            )
        public val Qwen3Compact: OnDeviceModelSpec =
            OnDeviceModelSpec(
                modelId = "qwen3-0.6b",
                displayName = "Qwen 3 Compact (0.6B)",
                parameterCount = "0.6B",
                memoryFootprintMb = 650,
                multiTokenPrediction = false,
                mtpSpeedupMultiplier = 1.0f,
                contextWindowTokens = 4096L,
                fileName = "Qwen3-0.6B.litertlm",
                downloadUrl =
                    "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm?download=true",
                downloadSizeBytes = 613_722_264L,
                summaryDescription =
                    "Fastest compact download (~585 MB) for low-latency replies and lighter memory use",
                disableThinkingTemplate = true,
            )
        public val Qwen25Instruct: OnDeviceModelSpec =
            OnDeviceModelSpec(
                modelId = "qwen2.5-1.5b",
                displayName = "Qwen 2.5 Instruct (1.5B)",
                parameterCount = "1.5B",
                memoryFootprintMb = 1600,
                multiTokenPrediction = false,
                mtpSpeedupMultiplier = 1.0f,
                contextWindowTokens = 4096L,
                fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                downloadUrl =
                    "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
                downloadSizeBytes = 1_597_922_856L,
                summaryDescription =
                    "Mid-size multilingual instruction model (~1.5 GB) with strong translation quality",
            )

        public val SupportedModels: List<OnDeviceModelSpec> =
            listOf(Gemma4E2B, Qwen3Compact, Qwen25Instruct, Gemma4E4B)

        public fun findById(id: String): OnDeviceModelSpec {
            val normalized = id.trim()
            return SupportedModels.firstOrNull {
                it.modelId.equals(normalized, ignoreCase = true) ||
                    it.displayName.equals(normalized, ignoreCase = true)
            } ?: Gemma4E2B
        }

        public fun formatModelBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 MB"
            val mib = bytes.toDouble() / (1024.0 * 1024.0)
            return if (mib >= 1024.0) {
                val gibTenths = ((mib / 1024.0) * 100.0 + 0.5).toLong()
                val whole = gibTenths / 100L
                val frac = (gibTenths % 100L).toString().padStart(2, '0')
                "$whole.$frac GB"
            } else {
                val wholeMb = (mib + 0.5).toLong().coerceAtLeast(1L)
                "$wholeMb MB"
            }
        }
    }
}
