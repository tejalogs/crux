package org.crux.ui.render

import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.nodes.Element
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public fun Element.resolvedHref(): String? {
    val href = attr("href").trim()
    if (href.isEmpty()) return null
    return StringUtil.resolve(baseUri(), href).takeIf { it.isNotEmpty() }
}

/** The image address resolved against the document's base URI, so relative and `//host` sources load. */
@HiddenFromObjC
public fun Element.resolvedSrc(): String? {
    val src = attr("src").trim()
    if (src.isEmpty()) return null
    if (src.startsWith("data:", ignoreCase = true)) return src
    return runCatching { StringUtil.resolve(baseUri(), src) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: src
}
