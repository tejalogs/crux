package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.decodeFromStringWithNullableFallback
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

public const val INSTAGRAM_BASE_API_URL: String = "https://www.instagram.com/api/v1"
public const val INSTAGRAM_FEED_TIMELINE_URL: String = "$INSTAGRAM_BASE_API_URL/feed/timeline/"
public const val INSTAGRAM_APP_ID: String = "936619743392459"
public const val INSTAGRAM_ASBD_ID: String = "129477"
public const val INSTAGRAM_USER_AGENT: String =
    "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.107 Mobile Safari/537.36"

private const val SHORTCODE_ALPHABET: String =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

public class InstagramFeedService {
    public companion object {
        public fun isValidFeedPayload(
            json: String,
            maxSizeBytes: Int = 2 * 1024 * 1024,
        ): Boolean {
            if (json.isBlank()) return false
            if (json.length > maxSizeBytes) return false
            val trimmed = json.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
            return extractMediaItemsFromPayload(json).isNotEmpty()
        }

        public const val MAX_DOM_PAYLOAD_BYTES: Int = 2 * 1024 * 1024

        public fun isValidDomPayload(
            json: String,
            maxSizeBytes: Int = MAX_DOM_PAYLOAD_BYTES,
        ): Boolean {
            if (json.isBlank() || json.length > maxSizeBytes) return false
            val trimmed = json.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return false
            if (trimmed == "[]") return false
            return runCatching {
                val element = JSON.parseToJsonElement(trimmed)
                element is JsonArray && element.isNotEmpty() && element[0] is JsonObject
            }.getOrDefault(false)
        }

        public fun parseDomPosts(json: String): List<InstagramMediaOrAd> {
            if (!isValidDomPayload(json)) return emptyList()
            return runCatching {
                val domPosts = JSON.decodeFromString<List<InstagramDomPost>>(json)
                domPosts.mapNotNull { dom ->
                    if (dom.code.isBlank() && dom.imageUrl.isBlank() && dom.caption.isBlank()) return@mapNotNull null
                    val stableId = dom.code.ifBlank { "dom_${dom.username}_${dom.imageUrl.hashCode()}" }
                    InstagramMediaOrAd(
                        id = stableId,
                        pk = stableId,
                        code = dom.code,
                        taken_at = Clock.System.now().epochSeconds,
                        caption = if (dom.caption.isNotBlank()) InstagramCaption(text = dom.caption) else null,
                        user =
                            InstagramUser(
                                pk = dom.username.ifBlank { "instagram_user" },
                                username = dom.username,
                                full_name = dom.username,
                                profile_pic_url = dom.avatar,
                            ),
                        image_versions2 =
                            if (dom.imageUrl.isNotBlank()) {
                                InstagramImageVersions(
                                    candidates =
                                        listOf(
                                            InstagramImageCandidate(
                                                url = dom.imageUrl,
                                                width = 1080,
                                                height = 1080,
                                            ),
                                        ),
                                )
                            } else {
                                null
                            },
                        like_count = dom.likes,
                    )
                }
            }.getOrDefault(emptyList())
        }

        public fun extractMediaItemsFromPayload(jsonText: String): List<InstagramMediaOrAd> {
            if (jsonText.isBlank()) return emptyList()
            val items = mutableListOf<InstagramMediaOrAd>()

            val timeline =
                runCatching {
                    JSON.decodeFromStringWithNullableFallback(InstagramTimelineResponse.serializer(), jsonText)
                }.getOrNull()

            if (timeline != null) {
                timeline.feed_items.forEach { fi ->
                    fi.media_or_ad?.let { items.add(it) }
                    fi.end_of_feed_demarcator?.group_set?.groups?.forEach { g ->
                        g.feed_items.forEach { gi -> gi.media_or_ad?.let { items.add(it) } }
                    }
                }
                timeline.items.forEach { items.add(it) }
            }

            if (items.isEmpty()) {
                val mediaInfo =
                    runCatching {
                        JSON.decodeFromStringWithNullableFallback(InstagramMediaInfoResponse.serializer(), jsonText)
                    }.getOrNull()
                mediaInfo?.items?.let { items.addAll(it) }
            }

            return items.filter { it.is_paid_partnership != true && it.ad_action == null }
        }

        public fun isValidInstagramHost(host: String?): Boolean {
            if (host.isNullOrBlank()) return false
            val lower = host.lowercase()
            return lower == "instagram.com" || lower.endsWith(".instagram.com") ||
                lower == "threads.net" || lower.endsWith(".threads.net") ||
                lower == "facebook.com" || lower.endsWith(".facebook.com")
        }

        public fun extractCsrfToken(cookies: String): String? =
            cookies
                .split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("csrftoken=") }
                ?.substringAfter("csrftoken=")

        public fun extractUserId(cookies: String): String? =
            cookies
                .split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("ds_user_id=") }
                ?.substringAfter("ds_user_id=")

        public fun hasActiveSession(cookies: String): Boolean = cookies.contains("sessionid=") && cookies.contains("ds_user_id=")

        /**
         * Resolves an Instagram media identifier (`pk_userId`, numeric `pk`, or URL shortcode)
         * into a clean numeric media ID (`pk`).
         */
        public fun resolveNumericMediaId(mediaIdOrCode: String): String {
            val trimmed = mediaIdOrCode.trim()
            val withoutComment =
                if (trimmed.contains("#comment_")) {
                    trimmed.substringBefore("#comment_")
                } else if (trimmed.startsWith("comment_")) {
                    trimmed.removePrefix("comment_")
                } else {
                    trimmed
                }
            val prefix = withoutComment.substringBefore("_")
            if (prefix.isNotEmpty() && prefix.all { it.isDigit() }) {
                return prefix
            }
            val decoded = shortcodeToMediaId(withoutComment)
            return decoded?.toString() ?: withoutComment
        }

        /**
         * Converts an Instagram post/reel URL shortcode (e.g. "C8abc123XYZ") to its 64-bit numeric media PK.
         * Ported from Barinsta's `TextUtils.shortcodeToId`.
         */
        public fun shortcodeToMediaId(shortcode: String): Long? {
            if (shortcode.isBlank()) return null
            var result = 0L
            var i = 0
            while (i < shortcode.length && i < 11) {
                val c = shortcode[i]
                val index = SHORTCODE_ALPHABET.indexOf(c)
                if (index < 0) return null
                result = result * 64 + index
                i++
            }
            return result.takeIf { it > 0L }
        }
    }
}
