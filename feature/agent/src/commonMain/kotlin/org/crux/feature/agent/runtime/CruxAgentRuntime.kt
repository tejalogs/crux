package org.crux.feature.agent.runtime

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import org.crux.data.datastore.model.AppSettings

internal class CruxAgentRuntime(
    val promptExecutor: PromptExecutor,
    val model: LLModel,
    val aiConfig: AppSettings.AiConfig,
)
