package org.crux.ui.render

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import org.crux.common.SerializableImmutableList
import java.text.Bidi

@Serializable
public sealed class RenderContent {
    @Serializable
    public data class Text(
        val runs: SerializableImmutableList<RenderRun>,
        val block: RenderBlockStyle = RenderBlockStyle(),
    ) : RenderContent() {
        public val hasLink: Boolean =
            runs.any { run ->
                run is RenderRun.Text && !run.style.link.isNullOrEmpty()
            }

        public val hasInlineImage: Boolean = runs.any { it is RenderRun.Image }
    }

    @Serializable
    public data class BlockImage(
        val url: String,
        val href: String?,
    ) : RenderContent()
}

@Serializable
public sealed class RenderRun {
    @Serializable
    public data class Text(
        val text: String,
        val style: RenderTextStyle = RenderTextStyle(),
    ) : RenderRun()

    @Serializable
    public data class Image(
        val url: String,
        val alt: String,
    ) : RenderRun()
}

@Serializable
public data class RenderTextStyle(
    val link: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val monospace: Boolean = false,
    val code: Boolean = false,
    val underline: Boolean = false,
    val small: Boolean = false,
    val time: Boolean = false,
)

@Serializable
public data class RenderBlockStyle(
    val headingLevel: Int? = null,
    val textAlignment: RenderTextAlignment? = null,
    val isListItem: Boolean = false,
    val isBlockQuote: Boolean = false,
    val isFigCaption: Boolean = false,
)

@Serializable
public enum class RenderTextAlignment {
    Start,
    Center,
}

@Serializable
@Immutable
public data class UiRichText(
    public val renderRuns: SerializableImmutableList<RenderContent>,
    val isRtl: Boolean,
    public val raw: String,
    public val innerText: String,
    public val imageUrls: SerializableImmutableList<String>,
) {
    public val isEmpty: Boolean =
        renderRuns.all { content ->
            when (content) {
                is RenderContent.BlockImage -> false

                is RenderContent.Text -> {
                    content.runs.all { run ->
                        when (run) {
                            is RenderRun.Image -> false
                            is RenderRun.Text -> run.text.isEmpty()
                        }
                    }
                }
            }
        }
    public val isLongText: Boolean = Character.codePointCount(innerText, 0, innerText.length) > 500
}

public fun uiRichTextOf(
    renderRuns: List<RenderContent>,
    raw: String? = null,
    innerText: String? = null,
    imageUrls: List<String>? = null,
    sourceLanguages: List<String> = emptyList(),
): UiRichText {
    val contents = renderRuns.toImmutableList()
    val resolvedInnerText = innerText ?: contents.joinToString(separator = "") { it.plainText() }
    return UiRichText(
        renderRuns = contents,
        isRtl = resolvedInnerText.resolveRtl(sourceLanguages),
        raw = raw ?: resolvedInnerText,
        innerText = resolvedInnerText,
        imageUrls = (imageUrls ?: contents.imageUrls()).toImmutableList(),
    )
}

public fun String.toUiPlainText(sourceLanguages: List<String> = emptyList()): UiRichText =
    UiRichText(
        renderRuns =
            persistentListOf(
                RenderContent.Text(
                    runs =
                        persistentListOf(
                            RenderRun.Text(text = this),
                        ),
                ),
            ),
        isRtl = resolveRtl(sourceLanguages),
        raw = this,
        innerText = this,
        imageUrls = persistentListOf(),
    )

public fun UiRichText.toFlatText(): String {
    val builder = StringBuilder()
    var appendedTextBlock = false
    renderRuns.forEach { content ->
        when (content) {
            is RenderContent.BlockImage -> {}

            is RenderContent.Text -> {
                val blockText = content.plainText().trim()
                if (blockText.isNotEmpty()) {
                    if (appendedTextBlock) {
                        builder.append('\n')
                    }
                    builder.append(blockText)
                    appendedTextBlock = true
                }
            }
        }
    }
    return builder.toString().trim()
}

public fun RenderContent.Text.plainText(): String =
    buildString {
        runs.forEach { run ->
            when (run) {
                is RenderRun.Image -> {
                    if (run.alt.isNotBlank()) {
                        append(run.alt)
                    }
                }

                is RenderRun.Text -> {
                    append(run.text)
                }
            }
        }
    }

private fun RenderContent.plainText(): String =
    when (this) {
        is RenderContent.BlockImage -> ""
        is RenderContent.Text -> plainText()
    }

private fun List<RenderContent>.imageUrls(): List<String> =
    flatMap { content ->
        when (content) {
            is RenderContent.BlockImage -> listOf(content.url)

            is RenderContent.Text -> {
                content.runs.mapNotNull { run ->
                    when (run) {
                        is RenderRun.Image -> run.url
                        is RenderRun.Text -> null
                    }
                }
            }
        }
    }

internal fun String.resolveRtl(sourceLanguages: List<String> = emptyList()): Boolean {
    sourceLanguages.firstNotNullOfOrNull { it.languageIsRtl() }?.let {
        return it
    }
    if (isBlank() || isLatinText()) {
        return false
    }
    if (!hasStrongRtlCodePoint()) {
        return false
    }
    return !Bidi(this, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()
}

private fun String.languageIsRtl(): Boolean? {
    val language =
        substringBefore('-')
            .substringBefore('_')
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?: return null
    if (language in unknownLanguageCodes) {
        return null
    }
    return language in rtlLanguageCodes
}

private fun String.isLatinText(): Boolean =
    all { char ->
        !char.isLetter() || char.isLatinLetter()
    }

private fun Char.isLatinLetter(): Boolean =
    this in '\u0041'..'\u005A' ||
        this in '\u0061'..'\u007A' ||
        this in '\u00C0'..'\u024F' ||
        this in '\u1E00'..'\u1EFF'

private fun String.hasStrongRtlCodePoint(): Boolean {
    var index = 0
    while (index < length) {
        val codePoint = Character.codePointAt(this, index)
        if (codePoint.isStrongRtlCodePoint()) {
            return true
        }
        index += Character.charCount(codePoint)
    }
    return false
}

private fun Int.isStrongRtlCodePoint(): Boolean =
    rtlCodePointRanges.any { range ->
        this in range
    }

private val rtlCodePointRanges =
    listOf(
        0x0590..0x05FF,
        0x0600..0x06FF,
        0x0700..0x074F,
        0x0750..0x077F,
        0x0780..0x07BF,
        0x07C0..0x07FF,
        0x0800..0x083F,
        0x0840..0x085F,
        0x0860..0x086F,
        0x0870..0x089F,
        0x08A0..0x08FF,
        0xFB1D..0xFB4F,
        0xFB50..0xFDFF,
        0xFE70..0xFEFF,
        0x10800..0x10FFF,
        0x1E800..0x1E95F,
    )

private val rtlLanguageCodes =
    setOf(
        "ar", "arc", "dv", "fa", "ha", "he", "iw", "ks", "ku",
        "nqo", "pa", "ps", "sd", "syr", "ug", "ur", "yi",
    )

private val unknownLanguageCodes =
    setOf("mis", "mul", "qaa", "und", "zxx")
