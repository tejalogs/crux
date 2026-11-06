package org.crux.ui.screen.compose

/** Joins a shared title and body the way share targets expect them to read as one post. */
internal fun sharedTextWithTitle(
    text: String?,
    title: String?,
): String {
    val body = text.orEmpty().trim()
    val heading = title.orEmpty().trim()
    return when {
        heading.isBlank() -> body
        body.isBlank() -> heading
        body == heading -> body
        body.startsWith("$heading\n") -> body
        else -> "$heading\n$body"
    }
}
