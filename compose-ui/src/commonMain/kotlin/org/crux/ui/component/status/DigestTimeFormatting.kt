package org.crux.ui.component.status

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Instant

/**
 * Formats an [Instant] to a localized lowercase time string (e.g. "9:00 am").
 */
public fun formatDigestTime(instant: Instant): String {
    val date = Date(instant.toEpochMilliseconds())
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(date).lowercase(Locale.getDefault())
}
