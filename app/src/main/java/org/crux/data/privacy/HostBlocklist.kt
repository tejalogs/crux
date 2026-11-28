package org.crux.data.privacy

/**
 * Normalises user input into a bare hostname for the blocklist.
 *
 * Strips scheme, path, query, fragment, credentials and port so that
 * `https://tracker.example/x?y=1`, `user@tracker.example:443` and `tracker.example` all resolve
 * to the same rule. Without this, a user pasting a URL from their browser would silently get a
 * rule that never matches.
 *
 * IPv6 literals are preserved intact including their brackets and port, since `stripping the port`
 * by splitting on `:` would corrupt them.
 */
public fun normaliseBlockedHost(input: String): String {
    var value = input.trim().lowercase()
    val schemeEnd = value.indexOf("://")
    if (schemeEnd >= 0) value = value.substring(schemeEnd + 3)
    value = value.substringBefore('/')
    value = value.substringBefore('?')
    value = value.substringBefore('#')
    value = value.substringAfterLast('@')
    if (value.startsWith("[")) {
        // IPv6 literal: keep the bracketed host, drop only a trailing :port outside the bracket.
        val close = value.indexOf(']')
        if (close >= 0) {
            value = value.substring(0, close + 1) + value.substring(close + 1).substringBefore(':')
        }
    } else {
        value = value.substringBefore(':')
    }
    return value.trim().trimEnd('.').removePrefix(".")
}

/** Whether [host] would be refused by [blocked]. */
public fun wouldBlock(
    host: String,
    blocked: Set<String>,
): Boolean = PrivacyClassifier.hostMatches(host, blocked)
