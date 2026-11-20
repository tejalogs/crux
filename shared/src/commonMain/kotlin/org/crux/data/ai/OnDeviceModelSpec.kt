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
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/2d74ac41a33d7da82f39f7d5720a04c68514a103/gemma-4-E2B-it.litertlm?download=true",
    val downloadSizeBytes: Long = 2_588_147_712L,
    val sha256: String = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
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
                    "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/f254b5ba6e36f7d89cc7892e268e9877bc20f0a0/gemma-4-E4B-it.litertlm?download=true",
                downloadSizeBytes = 3_659_530_240L,
                sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
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
                    "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/8d42ab5f1db14cad987fac57aeb17d070f9e180d/Qwen3-0.6B.litertlm?download=true",
                downloadSizeBytes = 613_722_264L,
                sha256 = "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108",
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
                    "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/60257f9f1d00ca1f24cc5ce3b829b4139be1ca2b/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
                downloadSizeBytes = 1_597_922_856L,
                sha256 = "c4a9b7289365ab80fe599b844b77ad253eb3c53fb0e5d963d699146a6f01255e",
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
