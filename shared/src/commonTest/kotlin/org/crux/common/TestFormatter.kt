package org.crux.common

import org.crux.ui.humanizer.PlatformFormatter
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

@HiddenFromObjC
class TestFormatter : PlatformFormatter {
    override fun formatNumber(number: Long): String = number.toString()

    override fun formatRelativeInstant(instant: Instant): String = instant.toString()

    override fun formatFullInstant(instant: Instant): String = instant.toString()

    override fun formatAbsoluteInstant(instant: Instant): String = instant.toString()
}
