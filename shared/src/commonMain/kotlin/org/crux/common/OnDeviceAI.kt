package org.crux.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface OnDeviceAI {
    public suspend fun isAvailable(): Boolean

    public suspend fun hasDownloadedModel(): Boolean = false

    public suspend fun activeModelDisplayName(): String? = null

    public suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String?

    public suspend fun generate(prompt: String): String? = null

    public suspend fun generate(
        prompt: String,
        systemPrompt: String?,
    ): String? =
        generate(
            if (systemPrompt.isNullOrBlank()) prompt else "$systemPrompt\n\n$prompt",
        )

    public fun generateStreaming(
        prompt: String,
        systemPrompt: String? = null,
    ): Flow<String> =
        flow {
            generate(prompt = prompt, systemPrompt = systemPrompt)?.let { emit(it) }
        }
}
