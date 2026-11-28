package org.crux.rss

import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure-Android streaming feed parser for RSS 2.0, Atom 1.0, RDF / RSS 1.0, and JSON Feed 1.0/1.1.
 * Uses Android's built-in [XmlPullParser] (zero external XML serialization dependencies).
 */
internal object RssFeedParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        rawBody: String,
        feedUrl: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): ParsedFeed {
        val trimmed = rawBody.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (trimmed.isEmpty()) throw IllegalArgumentException("Empty feed response")
        return if (trimmed.startsWith("{")) {
            parseJsonFeed(trimmed, feedUrl, nowMillis)
        } else {
            parseXmlFeed(trimmed, feedUrl, nowMillis)
        }
    }

    private fun parseJsonFeed(
        body: String,
        feedUrl: String,
        nowMillis: Long,
    ): ParsedFeed {
        val root = json.parseToJsonElement(body).jsonObject
        val title = root["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifEmpty { hostOf(feedUrl) }
        val siteUrl = root["home_page_url"]?.jsonPrimitive?.contentOrNull?.trim()?.let { resolveUrl(feedUrl, it) }
        val iconUrl =
            (root["favicon"] ?: root["icon"])?.jsonPrimitive?.contentOrNull?.trim()?.let { resolveUrl(siteUrl ?: feedUrl, it) }
        val itemsArray: JsonArray = root["items"]?.jsonArray ?: JsonArray(emptyList())
        val articles =
            itemsArray.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val link =
                    (obj["url"] ?: obj["external_url"])?.jsonPrimitive?.contentOrNull?.trim()
                        ?.let { resolveUrl(siteUrl ?: feedUrl, it) }
                        ?: return@mapNotNull null
                val html = obj["content_html"]?.jsonPrimitive?.contentOrNull
                val text = obj["content_text"]?.jsonPrimitive?.contentOrNull
                val summary = obj["summary"]?.jsonPrimitive?.contentOrNull
                val rawTitle = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val resolvedTitle = rawTitle.ifEmpty { summarizeText(summary ?: text ?: html, 90) }.ifEmpty { link }
                val summaryHtml = html ?: summary ?: text?.let { "<p>${escapeHtml(it)}</p>" }
                val snippet = summarizeText(summary ?: text ?: html, 260)
                val author =
                    obj["authors"]?.jsonArray?.firstOrNull()?.let { it as? JsonObject }?.get("name")?.jsonPrimitive?.contentOrNull
                        ?: (obj["author"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
                val imageUrl =
                    (obj["image"] ?: obj["banner_image"])?.jsonPrimitive?.contentOrNull?.trim()
                        ?.let { resolveUrl(link, it) }
                        ?: extractFirstImage(summaryHtml, link)
                val dateRaw = (obj["date_published"] ?: obj["date_modified"])?.jsonPrimitive?.contentOrNull
                val publishedAt = parseFeedDate(dateRaw) ?: nowMillis
                val id = obj["id"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: link
                ParsedArticle(
                    externalId = stableHash("$feedUrl|$id"),
                    title = cleanTitle(resolvedTitle),
                    link = link,
                    author = author?.trim()?.takeIf { it.isNotEmpty() },
                    summaryHtml = summaryHtml,
                    snippet = snippet,
                    imageUrl = imageUrl,
                    publishedAt = publishedAt,
                )
            }
        return ParsedFeed(title = title, siteUrl = siteUrl, iconUrl = iconUrl, articles = articles)
    }

    private fun parseXmlFeed(
        xml: String,
        feedUrl: String,
        nowMillis: Long,
    ): ParsedFeed {
        // Strip invalid XML 1.0 control characters common in messy real-world RSS feeds.
        val sanitized = xml.replace(INVALID_XML_CHARS, "")
        val factory =
            XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
                // Defense-in-depth against XXE even though Android's Expat/KXml parser ignores external entities.
                runCatching { setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
            }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(sanitized))

        var rootTag = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                rootTag = parser.name.lowercase(Locale.ROOT)
                break
            }
            parser.next()
        }
        return when (rootTag) {
            "feed" -> parseAtom(parser, feedUrl, nowMillis)
            "rss", "rdf", "channel" -> parseRssOrRdf(parser, feedUrl, nowMillis)
            else -> throw IllegalArgumentException("Unsupported feed root tag: <$rootTag>")
        }
    }

    private fun parseRssOrRdf(
        parser: XmlPullParser,
        feedUrl: String,
        nowMillis: Long,
    ): ParsedFeed {
        var feedTitle = ""
        var siteUrl: String? = null
        var iconUrl: String? = null
        val articles = mutableListOf<ParsedArticle>()

        var inImage = false
        var inItem = false
        var itemTitle = ""
        var itemLink = ""
        var itemGuid = ""
        var itemAuthor: String? = null
        var itemDescription: String? = null
        var itemContentEncoded: String? = null
        var itemImage: String? = null
        var itemDate: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val local = parser.name.lowercase(Locale.ROOT)
                    val prefix = parser.prefix?.lowercase(Locale.ROOT).orEmpty()
                    if (!inItem) {
                        when (local) {
                            "item" -> {
                                inItem = true
                                itemTitle = ""
                                itemLink = ""
                                itemGuid = ""
                                itemAuthor = null
                                itemDescription = null
                                itemContentEncoded = null
                                itemImage = null
                                itemDate = null
                            }

                            "image" -> inImage = true

                            "title" -> if (!inImage && feedTitle.isEmpty()) feedTitle = safeNextText(parser).trim()

                            "link" -> {
                                if (!inImage && siteUrl == null) {
                                    val href = parser.getAttributeValue(null, "href")
                                    val text = if (href.isNullOrBlank()) safeNextText(parser).trim() else href.trim()
                                    if (text.isNotEmpty() && !text.endsWith(".xml", true)) {
                                        siteUrl = resolveUrl(feedUrl, text)
                                    }
                                }
                            }

                            "url" -> if (inImage && iconUrl == null) iconUrl = resolveUrl(siteUrl ?: feedUrl, safeNextText(parser).trim())

                            "icon" -> if (iconUrl == null) iconUrl = resolveUrl(siteUrl ?: feedUrl, safeNextText(parser).trim())
                        }
                    } else {
                        when {
                            local == "title" && itemTitle.isEmpty() -> itemTitle = safeNextText(parser).trim()

                            local == "link" -> {
                                val rel = parser.getAttributeValue(null, "rel").orEmpty()
                                val href = parser.getAttributeValue(null, "href")
                                if (itemLink.isEmpty() && (rel.isEmpty() || rel.equals("alternate", true))) {
                                    itemLink = (href ?: safeNextText(parser)).trim()
                                }
                            }

                            local == "guid" || local == "id" -> {
                                val g = safeNextText(parser).trim()
                                if (g.isNotEmpty()) itemGuid = g
                            }

                            local == "encoded" || (prefix == "content" && local == "encoded") -> {
                                val body = safeNextText(parser).trim()
                                if (body.isNotEmpty()) itemContentEncoded = body
                            }

                            local == "description" || local == "summary" -> {
                                val desc = safeNextText(parser).trim()
                                if (desc.isNotEmpty() && itemDescription.isNullOrEmpty()) itemDescription = desc
                            }

                            local == "creator" || local == "author" -> {
                                val a = safeNextText(parser).trim()
                                if (a.isNotEmpty() && itemAuthor.isNullOrEmpty()) itemAuthor = cleanAuthor(a)
                            }

                            local == "pubdate" || local == "date" || local == "published" || local == "updated" -> {
                                val d = safeNextText(parser).trim()
                                if (d.isNotEmpty() && itemDate.isNullOrEmpty()) itemDate = d
                            }

                            local == "enclosure" -> {
                                val type = parser.getAttributeValue(null, "type").orEmpty()
                                val url = parser.getAttributeValue(null, "url").orEmpty().trim()
                                if (itemImage == null && url.isNotEmpty() && type.startsWith("image/", true)) {
                                    itemImage = url
                                }
                            }

                            (prefix == "media" && (local == "content" || local == "thumbnail")) || local == "thumbnail" -> {
                                val medium = parser.getAttributeValue(null, "medium").orEmpty()
                                val type = parser.getAttributeValue(null, "type").orEmpty()
                                val url = parser.getAttributeValue(null, "url").orEmpty().trim()
                                val isImage =
                                    local == "thumbnail" ||
                                        medium.equals("image", true) ||
                                        type.startsWith("image/", true) ||
                                        (medium.isEmpty() && type.isEmpty() && looksLikeImageUrl(url))
                                if (itemImage == null && url.isNotEmpty() && isImage) {
                                    itemImage = url
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val local = parser.name.lowercase(Locale.ROOT)
                    if (local == "image" && !inItem) {
                        inImage = false
                    } else if (local == "item" && inItem) {
                        inItem = false
                        val base = siteUrl ?: feedUrl
                        val resolvedLink =
                            itemLink.takeIf { it.isNotEmpty() }?.let { resolveUrl(base, it) }
                                ?: itemGuid.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                        if (!resolvedLink.isNullOrBlank()) {
                            val bestHtml =
                                when {
                                    !itemContentEncoded.isNullOrBlank() &&
                                        itemContentEncoded.length >= (itemDescription?.length ?: 0) -> itemContentEncoded
                                    else -> itemDescription ?: itemContentEncoded
                                }
                            val snippet = summarizeText(itemDescription ?: itemContentEncoded, 260)
                            val title = cleanTitle(itemTitle.ifEmpty { summarizeText(bestHtml, 90) }.ifEmpty { resolvedLink })
                            val resolvedImg =
                                itemImage?.let { resolveUrl(resolvedLink, it) } ?: extractFirstImage(bestHtml, resolvedLink)
                            val extId = itemGuid.ifEmpty { resolvedLink }
                            articles +=
                                ParsedArticle(
                                    externalId = stableHash("$feedUrl|$extId"),
                                    title = title,
                                    link = resolvedLink,
                                    author = itemAuthor,
                                    summaryHtml = bestHtml,
                                    snippet = snippet,
                                    imageUrl = resolvedImg,
                                    publishedAt = parseFeedDate(itemDate) ?: nowMillis,
                                )
                        }
                    }
                }
            }
            event = parser.next()
        }

        val resolvedFeedTitle = cleanTitle(feedTitle).ifEmpty { hostOf(siteUrl ?: feedUrl) }
        return ParsedFeed(
            title = resolvedFeedTitle,
            siteUrl = siteUrl,
            iconUrl = iconUrl,
            articles = articles,
        )
    }

    private fun parseAtom(
        parser: XmlPullParser,
        feedUrl: String,
        nowMillis: Long,
    ): ParsedFeed {
        var feedTitle = ""
        var siteUrl: String? = null
        var iconUrl: String? = null
        val articles = mutableListOf<ParsedArticle>()

        var inEntry = false
        var inAuthor = false
        var entryTitle = ""
        var entryLink = ""
        var entryId = ""
        var entryAuthor: String? = null
        var entrySummary: String? = null
        var entryContent: String? = null
        var entryImage: String? = null
        var entryDate: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val local = parser.name.lowercase(Locale.ROOT)
                    val prefix = parser.prefix?.lowercase(Locale.ROOT).orEmpty()
                    if (!inEntry) {
                        when (local) {
                            "entry" -> {
                                inEntry = true
                                inAuthor = false
                                entryTitle = ""
                                entryLink = ""
                                entryId = ""
                                entryAuthor = null
                                entrySummary = null
                                entryContent = null
                                entryImage = null
                                entryDate = null
                            }

                            "title" -> if (feedTitle.isEmpty()) feedTitle = safeNextText(parser).trim()

                            "link" -> {
                                val rel = parser.getAttributeValue(null, "rel").orEmpty()
                                val href = parser.getAttributeValue(null, "href").orEmpty().trim()
                                if (siteUrl == null && href.isNotEmpty() && (rel.isEmpty() || rel.equals("alternate", true))) {
                                    siteUrl = resolveUrl(feedUrl, href)
                                }
                            }

                            "icon", "logo" -> {
                                if (iconUrl == null) {
                                    val v = safeNextText(parser).trim()
                                    if (v.isNotEmpty()) iconUrl = resolveUrl(siteUrl ?: feedUrl, v)
                                }
                            }
                        }
                    } else {
                        when {
                            local == "author" -> inAuthor = true

                            local == "name" && inAuthor -> {
                                val n = safeNextText(parser).trim()
                                if (n.isNotEmpty() && entryAuthor.isNullOrEmpty()) entryAuthor = n
                            }

                            local == "title" && entryTitle.isEmpty() -> entryTitle = safeNextText(parser).trim()

                            local == "id" && entryId.isEmpty() -> entryId = safeNextText(parser).trim()

                            local == "link" -> {
                                val rel = parser.getAttributeValue(null, "rel").orEmpty()
                                val type = parser.getAttributeValue(null, "type").orEmpty()
                                val href = parser.getAttributeValue(null, "href").orEmpty().trim()
                                if (href.isNotEmpty()) {
                                    if (rel.equals("enclosure", true) && type.startsWith("image/", true) && entryImage == null) {
                                        entryImage = href
                                    } else if (entryLink.isEmpty() && (rel.isEmpty() || rel.equals("alternate", true))) {
                                        entryLink = href
                                    }
                                }
                            }

                            local == "content" -> {
                                val body = safeNextText(parser).trim()
                                if (body.isNotEmpty()) entryContent = body
                            }

                            local == "summary" -> {
                                val s = safeNextText(parser).trim()
                                if (s.isNotEmpty() && entrySummary.isNullOrEmpty()) entrySummary = s
                            }

                            local == "published" || local == "updated" -> {
                                val d = safeNextText(parser).trim()
                                if (d.isNotEmpty() && (entryDate.isNullOrEmpty() || local == "published")) {
                                    entryDate = d
                                }
                            }

                            (prefix == "media" && (local == "content" || local == "thumbnail")) || local == "thumbnail" -> {
                                val url = parser.getAttributeValue(null, "url").orEmpty().trim()
                                val type = parser.getAttributeValue(null, "type").orEmpty()
                                val medium = parser.getAttributeValue(null, "medium").orEmpty()
                                val isImage =
                                    local == "thumbnail" ||
                                        medium.equals("image", true) ||
                                        type.startsWith("image/", true) ||
                                        looksLikeImageUrl(url)
                                if (entryImage == null && url.isNotEmpty() && isImage) entryImage = url
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val local = parser.name.lowercase(Locale.ROOT)
                    if (local == "author" && inEntry) {
                        inAuthor = false
                    } else if (local == "entry" && inEntry) {
                        inEntry = false
                        inAuthor = false
                        val base = siteUrl ?: feedUrl
                        val resolvedLink =
                            entryLink.takeIf { it.isNotEmpty() }?.let { resolveUrl(base, it) }
                                ?: entryId.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                        if (!resolvedLink.isNullOrBlank()) {
                            val bestHtml =
                                when {
                                    !entryContent.isNullOrBlank() &&
                                        entryContent.length >= (entrySummary?.length ?: 0) -> entryContent
                                    else -> entrySummary ?: entryContent
                                }
                            val snippet = summarizeText(entrySummary ?: entryContent, 260)
                            val title = cleanTitle(entryTitle.ifEmpty { summarizeText(bestHtml, 90) }.ifEmpty { resolvedLink })
                            val resolvedImg =
                                entryImage?.let { resolveUrl(resolvedLink, it) } ?: extractFirstImage(bestHtml, resolvedLink)
                            val extId = entryId.ifEmpty { resolvedLink }
                            articles +=
                                ParsedArticle(
                                    externalId = stableHash("$feedUrl|$extId"),
                                    title = title,
                                    link = resolvedLink,
                                    author = entryAuthor,
                                    summaryHtml = bestHtml,
                                    snippet = snippet,
                                    imageUrl = resolvedImg,
                                    publishedAt = parseFeedDate(entryDate) ?: nowMillis,
                                )
                        }
                    }
                }
            }
            event = parser.next()
        }

        val resolvedFeedTitle = cleanTitle(feedTitle).ifEmpty { hostOf(siteUrl ?: feedUrl) }
        return ParsedFeed(
            title = resolvedFeedTitle,
            siteUrl = siteUrl,
            iconUrl = iconUrl,
            articles = articles,
        )
    }

    /** Reads text (and XHTML inline children if present) without crashing on nested markup. */
    private fun safeNextText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text.orEmpty())

                XmlPullParser.START_TAG -> {
                    depth++
                    sb.append('<').append(parser.name)
                    for (i in 0 until parser.attributeCount) {
                        sb.append(' ')
                            .append(parser.getAttributeName(i))
                            .append("=\"")
                            .append(escapeHtml(parser.getAttributeValue(i).orEmpty()))
                            .append('"')
                    }
                    sb.append('>')
                }

                XmlPullParser.END_TAG -> {
                    depth--
                    if (depth > 0) sb.append("</").append(parser.name).append('>')
                }

                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString()
    }

    internal fun parseFeedDate(raw: String?): Long? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // 1. ISO-8601 / RFC-3339 (Atom, JSON Feed)
        runCatching { return Instant.parse(s).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        // 2. Fallback common RSS formats (handles GMT/UTC/EDT/PST and missing seconds)
        val normalized = s.replace(Regex("\\s+"), " ")
        for (pattern in FALLBACK_DATE_PATTERNS) {
            runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                fmt.parse(normalized)?.time?.let { return it }
            }
        }
        return null
    }

    internal fun summarizeText(
        htmlOrText: String?,
        maxChars: Int,
    ): String {
        if (htmlOrText.isNullOrBlank()) return ""
        val plain =
            if (htmlOrText.contains('<') || htmlOrText.contains('&')) {
                Ksoup.parse(htmlOrText).apply { select("script,style,noscript").remove() }.text()
            } else {
                htmlOrText
            }.replace(WHITESPACE_REGEX, " ").trim()
        if (plain.length <= maxChars) return plain
        val cut = plain.take(maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > maxChars / 2) cut.substring(0, lastSpace) else cut).trimEnd() + "…"
    }

    internal fun cleanTitle(raw: String): String =
        if (raw.contains('&') || raw.contains('<')) {
            Ksoup.parse(raw).text().replace(WHITESPACE_REGEX, " ").trim()
        } else {
            raw.replace(WHITESPACE_REGEX, " ").trim()
        }

    internal fun resolveUrl(
        base: String,
        target: String,
    ): String {
        val trimmed = target.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) return trimmed
        if (trimmed.startsWith("//")) return "https:$trimmed"
        return runCatching { URI(base).resolve(trimmed).toString() }.getOrDefault(trimmed)
    }

    private fun extractFirstImage(
        html: String?,
        baseUrl: String,
    ): String? {
        if (html.isNullOrBlank() || !html.contains("<img", ignoreCase = true)) return null
        val imgs = Ksoup.parse(html, baseUrl).select("img[src]")
        for (img in imgs) {
            val src = img.absUrl("src").ifBlank { resolveUrl(baseUrl, img.attr("src")) }
            if (src.isBlank() || src.startsWith("data:", true)) continue
            val w = img.attr("width").toIntOrNull()
            val h = img.attr("height").toIntOrNull()
            if ((w != null && w <= 2) || (h != null && h <= 2)) continue
            if (TRACKER_PIXEL_HINTS.any { src.contains(it, ignoreCase = true) }) continue
            return src
        }
        return null
    }

    private fun looksLikeImageUrl(url: String): Boolean {
        val lower = url.substringBefore('?').lowercase(Locale.ROOT)
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".avif")
    }

    private fun cleanAuthor(raw: String): String {
        val match = EMAIL_WITH_NAME.find(raw)
        return (match?.groupValues?.getOrNull(1) ?: raw).trim()
    }

    private fun hostOf(url: String): String =
        runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    internal fun stableHash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }

    private val INVALID_XML_CHARS = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val EMAIL_WITH_NAME = Regex("""\(([^)]+)\)""")
    private val TRACKER_PIXEL_HINTS = listOf("feedburner.com/~r/", "pixel.", "/pixel", "1x1", "spacer.gif", "feedsportal.com")
    private val FALLBACK_DATE_PATTERNS =
        listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm Z",
            "dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss Z",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
        )
}
