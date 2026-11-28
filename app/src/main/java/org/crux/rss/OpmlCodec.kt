package org.crux.rss

import org.crux.data.network.PublicUrlValidator
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.Locale

/**
 * Streaming OPML 1.0 / 2.0 importer & exporter preserving folder categories and validating feed URLs.
 */
internal object OpmlCodec {
    private const val MAX_FEEDS = 1_500

    fun parse(
        opmlXml: String,
        validateUrl: (String) -> Boolean = PublicUrlValidator::isSafePublicHttpUrl,
    ): List<OpmlFeedEntry> {
        val trimmed = opmlXml.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (trimmed.isEmpty()) return emptyList()
        val factory =
            XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching { setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
            }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(trimmed))

        val results = LinkedHashMap<String, OpmlFeedEntry>()
        val categoryStack = ArrayDeque<String?>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && results.size < MAX_FEEDS) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true)) {
                        val xmlUrl = attrIgnoreCase(parser, "xmlUrl")?.trim()
                        val htmlUrl = attrIgnoreCase(parser, "htmlUrl")?.trim()?.takeIf { it.isNotEmpty() }
                        val label =
                            attrIgnoreCase(parser, "title")?.trim()?.takeIf { it.isNotEmpty() }
                                ?: attrIgnoreCase(parser, "text")?.trim()?.takeIf { it.isNotEmpty() }
                        if (!xmlUrl.isNullOrEmpty()) {
                            val key = xmlUrl.lowercase(Locale.ROOT)
                            if (key !in results && validateUrl(xmlUrl)) {
                                val category = categoryStack.lastOrNull { !it.isNullOrBlank() }
                                results[key] =
                                    OpmlFeedEntry(
                                        url = xmlUrl,
                                        title = label ?: xmlUrl,
                                        category = category,
                                        siteUrl = htmlUrl,
                                    )
                            }
                            categoryStack.addLast(null)
                        } else {
                            categoryStack.addLast(label)
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true) && categoryStack.isNotEmpty()) {
                        categoryStack.removeLast()
                    }
                }
            }
            event = parser.next()
        }
        return results.values.toList()
    }

    fun countImportableFeeds(
        opmlXml: String,
        validateUrl: (String) -> Boolean = PublicUrlValidator::isSafePublicHttpUrl,
    ): Int = parse(opmlXml, validateUrl).size

    fun export(
        feeds: List<RssFeed>,
        title: String = "Crux Feeds",
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<opml version="2.0">""")
        sb.appendLine("  <head>")
        sb.appendLine("    <title>${escapeXml(title)}</title>")
        sb.appendLine("  </head>")
        sb.appendLine("  <body>")
        val grouped = feeds.groupBy { it.category?.trim()?.takeIf { c -> c.isNotEmpty() } }
        grouped[null].orEmpty().forEach { feed ->
            sb.appendLine("    ${outlineLine(feed)}")
        }
        grouped.entries
            .filter { it.key != null }
            .sortedBy { it.key!!.lowercase(Locale.ROOT) }
            .forEach { (category, groupFeeds) ->
                val safeCat = escapeXml(category!!)
                sb.appendLine("""    <outline text="$safeCat" title="$safeCat">""")
                groupFeeds.forEach { feed ->
                    sb.appendLine("      ${outlineLine(feed)}")
                }
                sb.appendLine("    </outline>")
            }
        sb.appendLine("  </body>")
        sb.appendLine("</opml>")
        return sb.toString()
    }

    private fun outlineLine(feed: RssFeed): String {
        val safeTitle = escapeXml(feed.title)
        val safeXml = escapeXml(feed.url)
        val htmlPart = feed.siteUrl?.takeIf { it.isNotBlank() }?.let { """ htmlUrl="${escapeXml(it)}"""" }.orEmpty()
        return """<outline type="rss" text="$safeTitle" title="$safeTitle" xmlUrl="$safeXml"$htmlPart />"""
    }

    private fun attrIgnoreCase(
        parser: XmlPullParser,
        name: String,
    ): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).equals(name, ignoreCase = true)) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
