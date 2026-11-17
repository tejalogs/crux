package org.crux.common

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingMode
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.crux.data.ai.OnDeviceHardwareBackend
import org.crux.data.ai.OnDeviceModelSpec
import org.crux.data.datastore.model.AppSettings
import org.crux.data.repository.SettingsRepository
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File

internal sealed interface LiteRtRuntimeStatus {
    data object Unloaded : LiteRtRuntimeStatus

    data class Loading(
        val modelSpec: OnDeviceModelSpec,
        val backendLabel: String,
    ) : LiteRtRuntimeStatus

    data class Ready(
        val modelSpec: OnDeviceModelSpec,
        val activeBackendLabel: String,
        val initTimeMs: Long,
    ) : LiteRtRuntimeStatus

    data class Failed(
        val modelSpec: OnDeviceModelSpec,
        val message: String,
    ) : LiteRtRuntimeStatus
}

@Single
internal class LiteRtLmRuntime(
    @Provided private val context: Context,
    private val modelManager: OnDeviceModelManager,
    @Provided private val settingsRepository: SettingsRepository,
) {
    private val mutex = Mutex()

    private data class LoadedEngineKey(
        val modelId: String,
        val filePath: String,
        val backendId: String,
        val enableMtp: Boolean,
        val maxNumTokens: Int,
    )

    private var loadedKey: LoadedEngineKey? = null
    private var loadedEngine: Engine? = null
    private var loadedBackendLabel: String = ""

    private val _status = MutableStateFlow<LiteRtRuntimeStatus>(LiteRtRuntimeStatus.Unloaded)
    val status: StateFlow<LiteRtRuntimeStatus> = _status.asStateFlow()

    init {
        modelManager.registerBeforeDeleteHook { modelId ->
            unloadIfLoaded(modelId)
        }
    }

    fun hasInstalledModel(): Boolean = modelManager.hasAnyInstalledModel()

    suspend fun activeModelDisplayName(): String? =
        resolveTargetModel()?.first?.displayName

    suspend fun preloadActiveModel(): Result<String> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val resolved =
                    resolveTargetModel()
                        ?: return@withLock Result.failure(
                            IllegalStateException("No downloaded .litertlm model available."),
                        )
                val (spec, file, config) = resolved
                runCatching {
                    ensureEngineLocked(spec, file, config)
                    "$loadedBackendLabel (${spec.displayName})"
                }
            }
        }

    suspend fun unloadAll() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                closeLoadedEngineLocked()
            }
        }
    }

    suspend fun unloadIfLoaded(modelId: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (loadedKey?.modelId == modelId) {
                    closeLoadedEngineLocked()
                }
            }
        }
    }

    suspend fun generate(
        prompt: String,
        systemPrompt: String? = null,
        maxOutputTokens: Int? = null,
        temperature: Double = DEFAULT_TEMPERATURE,
    ): String? =
        withContext(Dispatchers.IO) {
            if (prompt.isBlank()) return@withContext null
            mutex.withLock {
                val resolved = resolveTargetModel() ?: return@withContext null
                val (spec, file, config) = resolved
                try {
                    val engine = ensureEngineLocked(spec, file, config)
                    val convConfig = buildConversationConfig(spec, systemPrompt, temperature)
                    val response =
                        engine.createConversation(convConfig).use { conversation ->
                            conversation.sendMessage(
                                message = Message.user(prompt),
                                extraContext = thinkingExtraContext(spec),
                                maxOutputToken = maxOutputTokens,
                                thinkingConfig = thinkingConfigFor(spec),
                            )
                        }
                    sanitizeModelOutput(response.toString()).takeIf { it.isNotBlank() }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "LiteRT-LM inference failed for ${spec.modelId}", t)
                    null
                }
            }
        }

    fun generateStreaming(
        prompt: String,
        systemPrompt: String? = null,
        maxOutputTokens: Int? = null,
        temperature: Double = DEFAULT_TEMPERATURE,
    ): Flow<String> =
        channelFlow {
            if (prompt.isBlank()) return@channelFlow
            mutex.withLock {
                val resolved = resolveTargetModel() ?: return@withLock
                val (spec, file, config) = resolved
                try {
                    val engine = ensureEngineLocked(spec, file, config)
                    val convConfig = buildConversationConfig(spec, systemPrompt, temperature)
                    val stripFilter = StreamingThinkTagFilter()
                    engine.createConversation(convConfig).use { conversation ->
                        conversation
                            .sendMessageAsync(
                                message = Message.user(prompt),
                                extraContext = thinkingExtraContext(spec),
                                maxOutputToken = maxOutputTokens,
                                thinkingConfig = thinkingConfigFor(spec),
                            ).collect { chunk ->
                                val filtered = stripFilter.processChunk(chunk.toString())
                                if (filtered.isNotEmpty()) {
                                    send(filtered)
                                }
                            }
                        val remaining = stripFilter.flush()
                        if (remaining.isNotEmpty()) {
                            send(remaining)
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "LiteRT-LM streaming failed for ${spec.modelId}", t)
                }
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun resolveTargetModel(): Triple<OnDeviceModelSpec, File, AppSettings.AiConfig.Type.OnDevice>? {
        val aiType =
            runCatching {
                settingsRepository.appSettings.first().aiConfig.type as? AppSettings.AiConfig.Type.OnDevice
            }.getOrNull() ?: AppSettings.AiConfig.Type.OnDevice()

        val requestedSpec = OnDeviceModelSpec.findById(aiType.model)
        val requestedFile = modelManager.installedModelFile(requestedSpec)
        if (requestedFile != null) {
            return Triple(requestedSpec, requestedFile, aiType)
        }
        val fallback = modelManager.firstInstalledModel() ?: return null
        return Triple(fallback.first, fallback.second, aiType)
    }

    private fun ensureEngineLocked(
        spec: OnDeviceModelSpec,
        file: File,
        config: AppSettings.AiConfig.Type.OnDevice,
    ): Engine {
        val maxNumTokens =
            minOf(
                config.contextLength.toInt().coerceAtLeast(512),
                spec.contextWindowTokens.toInt().coerceAtLeast(512),
            ).coerceIn(512, 4096)

        val desiredKey =
            LoadedEngineKey(
                modelId = spec.modelId,
                filePath = file.absolutePath,
                backendId = OnDeviceHardwareBackend.fromId(config.hardwareBackend).id,
                enableMtp = config.enableMtp && spec.multiTokenPrediction,
                maxNumTokens = maxNumTokens,
            )

        loadedEngine?.let { existing ->
            if (existing.isInitialized() && loadedKey == desiredKey) {
                return existing
            }
        }

        closeLoadedEngineLocked()

        val candidates = backendCandidates(OnDeviceHardwareBackend.fromId(config.hardwareBackend))
        val errors = mutableListOf<String>()
        val cacheDir = File(context.cacheDir, "litertlm_cache").apply { mkdirs() }.absolutePath

        for ((label, backend) in candidates) {
            _status.value = LiteRtRuntimeStatus.Loading(spec, label)
            val speculativeModes =
                if (desiredKey.enableMtp) {
                    listOf(SpeculativeDecodingMode.ADAPTIVE, SpeculativeDecodingMode.OFF)
                } else {
                    listOf(SpeculativeDecodingMode.OFF)
                }
            val tokenLimits = listOf<Int?>(maxNumTokens, null)

            for (specMode in speculativeModes) {
                for (tokenLimit in tokenLimits) {
                    val startMs = System.currentTimeMillis()
                    var engine: Engine? = null
                    try {
                        engine =
                            Engine(
                                EngineConfig(
                                    modelPath = file.absolutePath,
                                    backend = backend,
                                    maxNumTokens = tokenLimit,
                                    cacheDir = cacheDir,
                                    speculativeDecodingConfig = SpeculativeDecodingConfig(mode = specMode),
                                ),
                            )
                        engine.initialize()
                        val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                        loadedKey = desiredKey
                        loadedEngine = engine
                        loadedBackendLabel = label
                        _status.value =
                            LiteRtRuntimeStatus.Ready(
                                modelSpec = spec,
                                activeBackendLabel = label,
                                initTimeMs = elapsedMs,
                            )
                        return engine
                    } catch (t: Throwable) {
                        runCatching { if (engine?.isInitialized() == true) engine.close() }
                        val reason = t.localizedMessage ?: t.javaClass.simpleName
                        errors += "$label($specMode): $reason"
                        Log.w(TAG, "LiteRT-LM init attempt failed on $label ($specMode, maxTokens=$tokenLimit): $reason")
                    }
                }
            }
        }

        val combinedError = errors.firstOrNull() ?: "Failed to initialize LiteRT-LM engine."
        _status.value = LiteRtRuntimeStatus.Failed(spec, combinedError)
        throw IllegalStateException(combinedError)
    }

    private fun closeLoadedEngineLocked() {
        runCatching {
            loadedEngine?.let { engine ->
                if (engine.isInitialized()) {
                    engine.close()
                }
            }
        }
        loadedEngine = null
        loadedKey = null
        loadedBackendLabel = ""
        _status.value = LiteRtRuntimeStatus.Unloaded
    }

    private fun backendCandidates(requested: OnDeviceHardwareBackend): List<Pair<String, Backend>> {
        val cpuThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        val cpu = "CPU" to Backend.CPU(threadCount = cpuThreads)
        val gpu = "GPU" to Backend.GPU()
        val npuDir = context.applicationInfo.nativeLibraryDir.orEmpty()
        val npu = "NPU" to Backend.NPU(nativeLibraryDir = npuDir)

        return when (requested) {
            OnDeviceHardwareBackend.Auto -> listOf(gpu, cpu)
            OnDeviceHardwareBackend.Gpu -> listOf(gpu, cpu)
            OnDeviceHardwareBackend.Npu -> listOf(npu, gpu, cpu)
            OnDeviceHardwareBackend.Cpu -> listOf(cpu)
        }
    }

    private fun buildConversationConfig(
        spec: OnDeviceModelSpec,
        systemPrompt: String?,
        temperature: Double,
    ): ConversationConfig =
        ConversationConfig(
            systemInstruction = systemPrompt?.takeIf { it.isNotBlank() }?.let { Contents.of(it) },
            samplerConfig =
                SamplerConfig(
                    topK = DEFAULT_TOP_K,
                    topP = DEFAULT_TOP_P,
                    temperature = temperature.coerceIn(0.0, 2.0),
                ),
            extraContext = thinkingExtraContext(spec),
            thinkingConfig = thinkingConfigFor(spec),
        )

    private fun thinkingExtraContext(spec: OnDeviceModelSpec): Map<String, Any> =
        if (spec.disableThinkingTemplate) mapOf("enable_thinking" to false) else emptyMap()

    private fun thinkingConfigFor(spec: OnDeviceModelSpec): ThinkingConfig? =
        if (spec.disableThinkingTemplate) ThinkingConfig(enableThinking = false) else null

    companion object {
        private const val TAG = "LiteRtLmRuntime"
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_TOP_P = 0.95
        private const val DEFAULT_TEMPERATURE = 0.7

        internal fun sanitizeModelOutput(raw: String): String =
            raw
                .replace(THINK_BLOCK_REGEX, "")
                .replace("<think>", "")
                .replace("</think>", "")
                .trim()

        private val THINK_BLOCK_REGEX = Regex("""<think>[\s\S]*?</think>""")
    }
}

private class StreamingThinkTagFilter {
    private val buffer = StringBuilder()
    private var insideThink = false
    private var prefixResolved = false

    fun processChunk(chunk: String): String {
        if (chunk.isEmpty()) return ""
        if (prefixResolved && !insideThink) return chunk

        buffer.append(chunk)
        while (true) {
            val text = buffer.toString()
            if (!insideThink) {
                val trimmedStart = text.trimStart()
                if (trimmedStart.startsWith("<think>")) {
                    insideThink = true
                    prefixResolved = true
                    val startIdx = text.indexOf("<think>")
                    buffer.delete(0, startIdx + "<think>".length)
                    continue
                }
                if (trimmedStart.length < "<think>".length && "<think>".startsWith(trimmedStart)) {
                    // Wait for a few more characters before deciding whether response starts with <think>.
                    return ""
                }
                prefixResolved = true
                buffer.clear()
                return text
            } else {
                val endIdx = text.indexOf("</think>")
                if (endIdx < 0) {
                    return ""
                }
                insideThink = false
                buffer.delete(0, endIdx + "</think>".length)
                val remainder = buffer.toString().trimStart('\n', '\r')
                buffer.clear()
                return remainder
            }
        }
    }

    fun flush(): String {
        if (insideThink) {
            buffer.clear()
            return ""
        }
        val leftover = buffer.toString()
        buffer.clear()
        return leftover
    }
}
