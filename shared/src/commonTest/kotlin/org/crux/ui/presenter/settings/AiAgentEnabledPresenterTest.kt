package org.crux.ui.presenter.settings

import org.crux.data.datastore.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiAgentEnabledPresenterTest {
    @Test
    fun isAiAgentEnabled_followsTheAgentToggleOnDevice() {
        assertTrue(
            AppSettings
                .AiConfig(
                    agent = true,
                    type = AppSettings.AiConfig.Type.OnDevice(),
                ).isAiAgentEnabled(),
        )

        assertFalse(
            AppSettings
                .AiConfig(
                    agent = false,
                    type = AppSettings.AiConfig.Type.OnDevice(),
                ).isAiAgentEnabled(),
        )
    }

    @Test
    fun isAiAgentEnabled_legacyCloudTypeIsTreatedAsOnDevice() {
        // A legacy cloud config runs on the device, so its model field no longer matters.
        assertTrue(
            AppSettings
                .AiConfig(
                    agent = true,
                    type =
                        AppSettings.AiConfig.Type.OpenAI(
                            serverUrl = "https://api.example.com/v1",
                            apiKey = "sk-legacy",
                            model = " ",
                        ),
                ).isAiAgentEnabled(),
        )

        assertFalse(
            AppSettings
                .AiConfig(
                    agent = false,
                    type =
                        AppSettings.AiConfig.Type.OpenAI(
                            serverUrl = "https://api.example.com/v1",
                            apiKey = "sk-legacy",
                            model = "gpt-4.1",
                        ),
                ).isAiAgentEnabled(),
        )
    }
}
