package org.crux.data.privacy

/**
 * Classifies outgoing requests so the client can drop the ones that exist only to measure the
 * user, and can show the user what remains.
 *
 * The rules are deliberately conservative: a request is only labelled telemetry when its path is
 * a known measurement endpoint. When in doubt the request is kept and reported as
 * [PrivacyCategory.Unknown], because silently dropping a request the app actually needs is a far
 * worse failure than leaving one telemetry call through.
 */
public object PrivacyClassifier {
    private val telemetryPaths: List<String> =
        listOf(
            "/impressions",
            "/Impression",
            "/seen",
            "/seen_mentions",
            "/conversations/read",
            "/notifications/read",
            "/events",
            "/client_event",
            "/1.1/jot/client_event",
            "/i/api/2/client_event",
            "/metrics",
            "/stats",
            "/1.1/attribution",
        )

    private val profilingPaths: List<String> =
        listOf(
            "/search/adaptive.json",
            "/i/api/2/search/adaptive.json",
            "/i/api/1.1/search/adaptive.json",
            "/i/api/2/guide.json",
            "/users/recommendations",
            "/i/api/1.1/users/recommendations",
            "/i/api/1.1/onboarding",
            "/suggest",
            "/trends",
            "/i/api/2/trends",
            "/i/api/1.1/trends",
            "/1.1/recommendations",
        )

    private val authPaths: List<String> =
        listOf(
            "/oauth2",
            "/oauth",
            "/1.1/guest/activate",
            "/i/api/1.1/guest/activate",
            "/2/oauth2",
            "/api/1.1/account/login",
            "/i/api/1.1/account/login",
            "/session",
            "/1.1/session",
        )

    private val mediaExtensions: List<String> =
        listOf(
            ".jpg",
            ".jpeg",
            ".png",
            ".gif",
            ".webp",
            ".mp4",
            ".m3u8",
            ".mp3",
            ".m4a",
            ".webm",
        )

    private val uploadPaths: List<String> =
        listOf(
            "/media/upload",
            "/upload",
            "/uploadBlob",
            "/uploadVideo",
            "/api/v1/media",
            "/api/v2/media",
            "/statuses/uploadPic",
        )

    /**
     * Decides how to treat a request.
     *
     * @param method HTTP method
     * @param host request host, lowercased by the caller
     * @param path request path, query string excluded
     * @param blockTelemetry when true, measurement endpoints are refused instead of sent
     * @param blockProfiling when true, discovery/profiling endpoints are refused
     */
    public fun classify(
        method: String,
        host: String,
        path: String,
        blockTelemetry: Boolean = false,
        blockProfiling: Boolean = false,
    ): PrivacyVerdict {
        val lowerPath = path.lowercase()
        val upperMethod = method.uppercase()

        if (authPaths.any { lowerPath.contains(it) }) {
            return PrivacyVerdict(PrivacyCategory.Auth, host, path, upperMethod)
        }

        if (uploadPaths.any { lowerPath.endsWith(it) || lowerPath.contains(it) }) {
            return PrivacyVerdict(PrivacyCategory.Media, host, path, upperMethod)
        }

        if (mediaExtensions.any { lowerPath.endsWith(it) }) {
            return PrivacyVerdict(PrivacyCategory.Media, host, path, upperMethod)
        }

        if (telemetryPaths.any { lowerPath.contains(it) }) {
            return PrivacyVerdict(
                category = PrivacyCategory.Telemetry,
                host = host,
                path = path,
                method = upperMethod,
                blocked = blockTelemetry,
            )
        }

        if (profilingPaths.any { lowerPath.contains(it) }) {
            return PrivacyVerdict(
                category = PrivacyCategory.Profiling,
                host = host,
                path = path,
                method = upperMethod,
                blocked = blockProfiling,
            )
        }

        return PrivacyVerdict(PrivacyCategory.Content, host, path, upperMethod)
    }

    /**
     * Host-suffix check for blocklists.
     *
     * Matches the host itself and any parent domain, so blocking `example.com` also blocks
     * `metrics.example.com`. Does not match a mere substring, so blocking `example.com` does not
     * block `notexample.com`.
     */
    public fun hostMatches(
        host: String,
        blocked: Set<String>,
    ): Boolean {
        if (blocked.isEmpty()) return false
        val lower = host.lowercase()
        return blocked.any { entry ->
            val rule = entry.lowercase().removePrefix(".")
            rule.isNotEmpty() && (lower == rule || lower.endsWith(".$rule"))
        }
    }
}
