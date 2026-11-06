package org.crux.ui.component.status

import org.crux.ui.model.UiState
import org.crux.ui.render.UiRichText

internal data class TranslateResult(
    val contentWarning: UiState<UiRichText>?,
    val text: UiState<UiRichText>,
)
