package dev.dimension.flare.data.datasource.linkedin

public const val LINKEDIN_FEED_TIMELINE_URL: String = "https://www.linkedin.com/voyager/api/feed/updatesV2"
public const val LINKEDIN_FEED_DASH_URL: String = "https://www.linkedin.com/voyager/api/feed/dash/updates"
public const val LINKEDIN_USER_AGENT: String =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

public class LinkedInFeedService {
    public fun normalizeThreadUrn(rawUrn: String): String {
        val clean = rawUrn.substringBefore("#").trim()
        val directMatch = Regex("""urn:li:(?:activity|ugcPost|share):[0-9]+""").find(clean)
        if (directMatch != null) return directMatch.value

        val activityNum = Regex("""activity:([0-9]+)""").find(clean)?.groupValues?.get(1)
        if (activityNum != null) return "urn:li:activity:$activityNum"

        if (clean.startsWith("urn:li:fs_updateV2:(") && clean.contains(",")) {
            val inner = clean.substringAfter("(").substringBefore(",")
            if (inner.startsWith("urn:li:")) return normalizeThreadUrn(inner)
        }
        if (clean.contains("(") && clean.contains(")")) {
            val inner = clean.substringAfter("(").substringBefore(")")
            val innerMatch = Regex("""urn:li:(?:activity|ugcPost|share):[0-9]+""").find(inner)
            if (innerMatch != null) return innerMatch.value
        }
        return if (clean.startsWith("urn:li:")) clean else "urn:li:activity:$clean"
    }

    public fun normalizeParentCommentUrn(rawUrn: String): String? {
        if (!rawUrn.contains("#") && !rawUrn.contains("comment")) return null
        val target = if (rawUrn.contains("#")) rawUrn.substringAfter("#").trim() else rawUrn.trim()
        if (target.isBlank()) return null
        if (target.startsWith("urn:li:fsd_comment:")) return target
        if (target.startsWith("urn:li:fs_comment:")) {
            val inner = target.substringAfter("(").substringBefore(",")
            if (inner.startsWith("urn:li:comment:")) return inner
            return target
        }
        val directCommentMatch = Regex("""urn:li:(?:fsd_|fs_)?comment:\([^)]+\)""").find(target)
        if (directCommentMatch != null) return directCommentMatch.value
        val directSimpleMatch = Regex("""urn:li:(?:fsd_|fs_)?comment:[0-9]+""").find(target)
        if (directSimpleMatch != null) return directSimpleMatch.value
        if (target.startsWith("urn:li:comment:")) return target
        return null
    }

    public companion object {
        public fun extractCsrfToken(cookies: String): String? =
            cookies
                .split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("JSESSIONID=") }
                ?.substringAfter("JSESSIONID=")
                ?.removeSurrounding("\"")

        public fun extractUserId(cookies: String): String? {
            // 1. Try to find explicit member id / li_at token fingerprint
            val liAt =
                cookies
                    .split(";")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("li_at=") }
                    ?.substringAfter("li_at=")

            if (!liAt.isNullOrBlank()) {
                // Return consistent pseudo-id derived from session token
                val prefix = liAt.take(16)
                return "li_user_${prefix.hashCode()}"
            }
            return null
        }

        public fun hasActiveSession(cookies: String): Boolean = cookies.contains("li_at=")

        public fun isValidLinkedInHost(host: String?): Boolean = LinkedInFeedParser.isValidLinkedInHost(host)
    }
}
