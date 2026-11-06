@file:OptIn(FormatStringsInDatetimeFormats::class)

package org.crux.ui.model

import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.crux.ui.render.UiRichText

public val UiRichText.direction: LayoutDirection
    get() =
        if (isRtl) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
