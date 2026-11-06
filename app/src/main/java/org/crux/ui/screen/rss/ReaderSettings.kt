package org.crux.ui.screen.rss

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import org.crux.R
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val PREFS = "reader_settings"
private const val POSITIONS_PREFS = "reader_positions"
private const val MAX_SAVED_POSITIONS = 200
private const val WORDS_PER_MINUTE = 220
private const val BASE_TEXT_SP = 18f

internal enum class ReaderMode(
    val labelRes: Int,
) {
    Pages(R.string.reader_mode_pages),
    Scroll(R.string.reader_mode_scroll),
}

internal enum class ReaderFont(
    val family: FontFamily,
    val labelRes: Int,
) {
    Sans(FontFamily.SansSerif, R.string.reader_font_sans),
    Serif(FontFamily.Serif, R.string.reader_font_serif),
}

internal enum class ReaderSpacing(
    val lineHeight: Float,
    val labelRes: Int,
) {
    Compact(1.4f, R.string.reader_spacing_compact),
    Comfortable(1.6f, R.string.reader_spacing_comfortable),
    Relaxed(1.85f, R.string.reader_spacing_relaxed),
}

internal enum class ReaderTheme(
    val background: Color?,
    val text: Color?,
    val labelRes: Int,
) {
    App(null, null, R.string.reader_theme_app),
    Sepia(Color(0xFFF6EEDD), Color(0xFF4A3A28), R.string.reader_theme_sepia),
    Night(Color(0xFF0E0E10), Color(0xFFC9C9CC), R.string.reader_theme_night),
}

internal data class ReaderSettings(
    val textScale: Float = 1f,
    val font: ReaderFont = ReaderFont.Serif,
    val spacing: ReaderSpacing = ReaderSpacing.Comfortable,
    val theme: ReaderTheme = ReaderTheme.Sepia,
    val mode: ReaderMode = ReaderMode.Pages,
) {
    fun bodyStyle(base: TextStyle): TextStyle {
        val size = BASE_TEXT_SP * textScale
        return base.copy(
            fontFamily = font.family,
            fontSize = size.sp,
            lineHeight = (size * spacing.lineHeight).sp,
            // Justified text leaves wide gaps at phone widths, so keep a natural right edge.
            textAlign = TextAlign.Start,
            hyphens = Hyphens.Auto,
            lineBreak = LineBreak.Paragraph,
        )
    }
}

/** One page turn in pixels: whole lines, keeping the last line visible so nothing is cut off. */
internal fun pageStep(
    viewportPx: Int,
    lineHeightPx: Float,
): Int {
    if (lineHeightPx <= 0f) return viewportPx
    val lines = (viewportPx / lineHeightPx).toInt() - 1
    return (lines.coerceAtLeast(1) * lineHeightPx).toInt()
}

/** Reading time from word count, never below one minute. */
internal fun readingMinutes(text: String): Int {
    val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
    return ceil(words / WORDS_PER_MINUTE.toFloat()).toInt().coerceAtLeast(1)
}

private fun Context.readSettings(): ReaderSettings {
    val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val defaults = ReaderSettings()
    return ReaderSettings(
        textScale = prefs.getFloat("scale", defaults.textScale),
        font = enumOf(prefs.getString("font", null), defaults.font),
        spacing = enumOf(prefs.getString("spacing", null), defaults.spacing),
        theme = enumOf(prefs.getString("theme", null), defaults.theme),
        mode = enumOf(prefs.getString("mode", null), defaults.mode),
    )
}

private inline fun <reified T : Enum<T>> enumOf(
    name: String?,
    fallback: T,
): T = enumValues<T>().firstOrNull { it.name == name } ?: fallback

private fun Context.writeSettings(settings: ReaderSettings) {
    getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
        putFloat("scale", settings.textScale)
        putString("font", settings.font.name)
        putString("spacing", settings.spacing.name)
        putString("theme", settings.theme.name)
        putString("mode", settings.mode.name)
    }
}

internal class ReaderSettingsState(
    private val context: Context,
) {
    var value by mutableStateOf(context.readSettings())
        private set

    fun update(settings: ReaderSettings) {
        value = settings
        context.writeSettings(settings)
    }
}

@Composable
internal fun rememberReaderSettings(): ReaderSettingsState {
    val context = LocalContext.current
    return remember(context) { ReaderSettingsState(context.applicationContext) }
}

/** How long the scroll range must hold still before the saved place is treated as restored. */
internal const val READER_LAYOUT_SETTLE_MILLIS = 300L

private const val POSITION_KEY_PREFIX = "frac:"
private const val TIME_KEY_PREFIX = "time:"

/** How far down the article the reader is, from 0 (top) to 1 (end). Independent of text size and screen. */
internal fun scrollFractionOf(
    value: Int,
    maxValue: Int,
): Float = if (maxValue <= 0) 0f else (value.toFloat() / maxValue).coerceIn(0f, 1f)

/** The scroll offset that puts the reader at [fraction] of an article whose scroll range is [maxValue]. */
internal fun scrollPositionFor(
    fraction: Float,
    maxValue: Int,
): Int = (fraction.coerceIn(0f, 1f) * maxValue).roundToInt().coerceIn(0, maxValue.coerceAtLeast(0))

/** The keys to forget so that at most [limit] remain once one more is added: the least recently saved. */
internal fun oldestToEvict(
    savedAt: Map<String, Long>,
    limit: Int,
): List<String> {
    val excess = savedAt.size - limit + 1
    if (excess <= 0) return emptyList()
    return savedAt.entries
        .sortedBy { it.value }
        .take(excess)
        .map { it.key }
}

/** Where the reader stopped in each article, as a fraction of its length, kept on this device only. */
internal class ReaderPositions(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(POSITIONS_PREFS, Context.MODE_PRIVATE)

    fun get(url: String): Float = prefs.getFloat(POSITION_KEY_PREFIX + url, 0f)

    fun save(
        url: String,
        fraction: Float,
    ) {
        val savedAt =
            prefs.all.keys
                .filter { it.startsWith(POSITION_KEY_PREFIX) }
                .map { it.removePrefix(POSITION_KEY_PREFIX) }
                .filter { it != url }
                .associateWith { prefs.getLong(TIME_KEY_PREFIX + it, 0L) }
        prefs.edit {
            // Earlier versions stored raw pixels under the bare URL; those can no longer be used.
            prefs.all.keys
                .filterNot { it.startsWith(POSITION_KEY_PREFIX) || it.startsWith(TIME_KEY_PREFIX) }
                .forEach { remove(it) }
            oldestToEvict(savedAt, MAX_SAVED_POSITIONS).forEach { old ->
                remove(POSITION_KEY_PREFIX + old)
                remove(TIME_KEY_PREFIX + old)
            }
            putFloat(POSITION_KEY_PREFIX + url, fraction.coerceIn(0f, 1f))
            putLong(TIME_KEY_PREFIX + url, System.currentTimeMillis())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_text_size, (settings.textScale * 100).roundToInt()),
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = settings.textScale,
                onValueChange = { onChange(settings.copy(textScale = it)) },
                valueRange = 0.8f..1.6f,
                steps = 7,
            )
            OptionRow(
                title = R.string.reader_mode,
                options = ReaderMode.entries,
                selected = settings.mode,
                label = { it.labelRes },
                onSelect = { onChange(settings.copy(mode = it)) },
            )
            OptionRow(
                title = R.string.reader_font,
                options = ReaderFont.entries,
                selected = settings.font,
                label = { it.labelRes },
                onSelect = { onChange(settings.copy(font = it)) },
            )
            OptionRow(
                title = R.string.reader_spacing,
                options = ReaderSpacing.entries,
                selected = settings.spacing,
                label = { it.labelRes },
                onSelect = { onChange(settings.copy(spacing = it)) },
            )
            OptionRow(
                title = R.string.reader_theme,
                options = ReaderTheme.entries,
                selected = settings.theme,
                label = { it.labelRes },
                onSelect = { onChange(settings.copy(theme = it)) },
            )
        }
    }
}

@Composable
private fun <T> OptionRow(
    title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(title), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(text = stringResource(label(option))) },
                )
            }
        }
    }
}
