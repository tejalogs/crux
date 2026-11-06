package org.crux.ui.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView

/*
 * Crux "calm" modes. Feeds are tuned to be compelling and colour is a big part of that pull, so
 * grayscale removes it everywhere, and paper tint swaps glowing white/black for printed-page tones.
 */

/** Warm off-white page and ink tones, a little like newsprint. */
private val PaperLight = Color(0xFFF3EEE3)
private val PaperLightRaised = Color(0xFFF8F4EB)
private val PaperLightSunken = Color(0xFFEAE4D6)
private val InkLight = Color(0xFF2B2722)
private val PaperDark = Color(0xFF1C1A17)
private val PaperDarkRaised = Color(0xFF25221E)
private val PaperDarkSunken = Color(0xFF151311)
private val InkDark = Color(0xFFE8E1D4)

internal fun ColorScheme.withPaperTint(dark: Boolean): ColorScheme =
    if (dark) {
        copy(
            background = PaperDark,
            surface = PaperDark,
            surfaceDim = PaperDarkSunken,
            surfaceBright = PaperDarkRaised,
            surfaceContainerLowest = PaperDarkSunken,
            surfaceContainerLow = PaperDark,
            surfaceContainer = PaperDark,
            surfaceContainerHigh = PaperDarkRaised,
            surfaceContainerHighest = Color(0xFF2E2A25),
            onBackground = InkDark,
            onSurface = InkDark,
        )
    } else {
        copy(
            background = PaperLight,
            surface = PaperLight,
            surfaceDim = PaperLightSunken,
            surfaceBright = PaperLightRaised,
            surfaceContainerLowest = PaperLightRaised,
            surfaceContainerLow = PaperLight,
            surfaceContainer = PaperLight,
            surfaceContainerHigh = PaperLightRaised,
            surfaceContainerHighest = Color(0xFFFCF9F3),
            onBackground = InkLight,
            onSurface = InkLight,
        )
    }

private fun Color.toGray(): Color {
    val l = 0.299f * red + 0.587f * green + 0.114f * blue
    return Color(l, l, l, alpha)
}

/**
 * Desaturates the theme itself, so dialogs and sheets (separate windows that the window-wide
 * filter below cannot reach) are gray too. Paper surfaces are kept warm on purpose.
 */
internal fun ColorScheme.desaturated(keepPaper: Boolean): ColorScheme {
    val gray =
        copy(
            primary = primary.toGray(),
            onPrimary = onPrimary.toGray(),
            primaryContainer = primaryContainer.toGray(),
            onPrimaryContainer = onPrimaryContainer.toGray(),
            inversePrimary = inversePrimary.toGray(),
            secondary = secondary.toGray(),
            onSecondary = onSecondary.toGray(),
            secondaryContainer = secondaryContainer.toGray(),
            onSecondaryContainer = onSecondaryContainer.toGray(),
            tertiary = tertiary.toGray(),
            onTertiary = onTertiary.toGray(),
            tertiaryContainer = tertiaryContainer.toGray(),
            onTertiaryContainer = onTertiaryContainer.toGray(),
            surfaceTint = surfaceTint.toGray(),
            inverseSurface = inverseSurface.toGray(),
            inverseOnSurface = inverseOnSurface.toGray(),
            error = error.toGray(),
            onError = onError.toGray(),
            errorContainer = errorContainer.toGray(),
            onErrorContainer = onErrorContainer.toGray(),
            outline = outline.toGray(),
            outlineVariant = outlineVariant.toGray(),
            scrim = scrim.toGray(),
            onSurfaceVariant = onSurfaceVariant.toGray(),
            surfaceVariant = surfaceVariant.toGray(),
        )
    return if (keepPaper) {
        gray
    } else {
        gray.copy(
            background = background.toGray(),
            onBackground = onBackground.toGray(),
            surface = surface.toGray(),
            onSurface = onSurface.toGray(),
            surfaceBright = surfaceBright.toGray(),
            surfaceDim = surfaceDim.toGray(),
            surfaceContainer = surfaceContainer.toGray(),
            surfaceContainerHigh = surfaceContainerHigh.toGray(),
            surfaceContainerHighest = surfaceContainerHighest.toGray(),
            surfaceContainerLow = surfaceContainerLow.toGray(),
            surfaceContainerLowest = surfaceContainerLowest.toGray(),
        )
    }
}

/** Luminance-only matrix; with [warm] the grays lean slightly toward a printed-paper sepia. */
internal fun calmColorMatrix(warm: Boolean): ColorMatrix {
    val r = 0.299f
    val g = 0.587f
    val b = 0.114f
    val (rs, gs, bs) = if (warm) Triple(1.0f, 0.97f, 0.88f) else Triple(1f, 1f, 1f)
    val (ro, go, bo) = if (warm) Triple(6f, 4f, 0f) else Triple(0f, 0f, 0f)
    return ColorMatrix(
        floatArrayOf(
            r * rs,
            g * rs,
            b * rs,
            0f,
            ro,
            r * gs,
            g * gs,
            b * gs,
            0f,
            go,
            r * bs,
            g * bs,
            b * bs,
            0f,
            bo,
            0f,
            0f,
            0f,
            1f,
            0f,
        ),
    )
}

/**
 * Draws the whole activity window through a grayscale filter. This is the only way to also reach
 * images, videos and the live site WebViews, whose colours Crux does not control.
 */
@Composable
internal fun ApplyWindowGrayscale(
    enabled: Boolean,
    warm: Boolean,
) {
    val root = LocalView.current.rootView
    DisposableEffect(root, enabled, warm) {
        if (enabled) {
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(calmColorMatrix(warm)) }
            root.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            root.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        onDispose {
            root.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }
}

/** True when a colour is closer to white than black; used to pick paper light/dark tones. */
internal fun ColorScheme.isLightBackground(): Boolean = background.luminance() > 0.5f
