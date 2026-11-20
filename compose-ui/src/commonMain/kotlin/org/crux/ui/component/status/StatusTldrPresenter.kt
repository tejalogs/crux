package org.crux.ui.component.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.crux.ui.model.UiState
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.server.AiTLDRPresenter
import org.crux.ui.render.UiRichText
import org.crux.ui.render.toFlatText

@Composable
internal fun statusTldrPresenter(
    contentWarning: UiRichText?,
    content: UiRichText,
    targetLanguage: String,
): UiState<String> =
    remember(contentWarning, content, targetLanguage) {
        val text =
            buildString {
                contentWarning?.takeIf { !it.isEmpty }?.let {
                    append("Content warning:\n")
                    append(it.toFlatText())
                    append("\n\n")
                }
                append("Content:\n")
                append(content.toFlatText())
            }
        AiTLDRPresenter(text, targetLanguage)
    }.invoke()
