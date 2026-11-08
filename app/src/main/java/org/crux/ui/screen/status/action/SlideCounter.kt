package org.crux.ui.screen.status.action

import java.util.Locale

/** Slide numbers are shown with two digits (01, 02 … 12), so ten or more slides still line up. */
internal fun slideNumber(number: Int): String = String.format(Locale.ROOT, "%02d", number)

/** "01/08": the position of a slide within its deck. */
internal fun slideCounter(
    number: Int,
    total: Int,
): String = "${slideNumber(number)}/${slideNumber(total)}"
