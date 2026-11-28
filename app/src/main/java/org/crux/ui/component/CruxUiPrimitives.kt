package org.crux.ui.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import com.eygraber.compose.placeholder.PlaceholderDefaults
import com.eygraber.compose.placeholder.PlaceholderHighlight
import com.eygraber.compose.placeholder.fade
import com.eygraber.compose.placeholder.placeholder
import kotlinx.collections.immutable.ImmutableMap
import org.crux.data.datastore.model.AppSettings
import org.crux.data.model.appearance.GlobalAppearance
import org.crux.data.model.appearance.TimelineAppearance
import org.crux.ui.render.UiRichText
import org.crux.ui.theme.ListCardShapes

public val LocalGlobalAppearance: ProvidableCompositionLocal<GlobalAppearance> =
    staticCompositionLocalOf {
        GlobalAppearance.Default
    }

public val LocalTimelineAppearance: ProvidableCompositionLocal<TimelineAppearance> =
    staticCompositionLocalOf {
        TimelineAppearance.Default
    }

public val LocalAppSettings: ProvidableCompositionLocal<AppSettings> =
    staticCompositionLocalOf {
        AppSettings(version = "")
    }

public val LocalNetworkImageAllowHardware: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { true }

@Composable
public fun rememberImagesEnabled(): Boolean = LocalGlobalAppearance.current.liteMode.loadsMedia

@Composable
public fun FAIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(20.dp),
        tint = tint,
    )
}

@Composable
public fun CruxDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
        properties = properties,
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
public fun Glassify(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 4.dp,
    shadowElevation: Dp = 4.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
    ) {
        Box(contentAlignment = contentAlignment) {
            Box(modifier = Modifier.matchParentSize().background(color = color))
            content()
        }
    }
}

@Composable
public fun Glassify(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 4.dp,
    shadowElevation: Dp = 4.dp,
    border: BorderStroke? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
    ) {
        Box(contentAlignment = contentAlignment) {
            Box(modifier = Modifier.matchParentSize().background(color = color))
            content()
        }
    }
}

@Composable
public fun Modifier.listCard(
    index: Int = 0,
    totalCount: Int = 0,
): Modifier = this.clip(listCardShape(index, totalCount))

@Composable
public fun listCardShape(
    index: Int = 0,
    totalCount: Int = 0,
): Shape =
    if (totalCount > 1) {
        when (index) {
            0 -> ListCardShapes.topCard()
            totalCount - 1 -> ListCardShapes.bottomCard()
            else -> ListCardShapes.item()
        }
    } else {
        ListCardShapes.container()
    }

@Composable
public fun Modifier.listCardContainer(): Modifier = this.clip(ListCardShapes.container())

@Composable
public fun Modifier.listCardItem(): Modifier = this.clip(ListCardShapes.item())

@Composable
@ReadOnlyComposable
public fun Modifier.placeholder(
    visible: Boolean,
    color: Color = Color.Unspecified,
    shape: Shape? = null,
    highlight: PlaceholderHighlight? =
        PlaceholderHighlight.fade(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.3f)),
    placeholderFadeAnimationSpec: AnimationSpec<Float> = spring(),
    contentFadeAnimationSpec: AnimationSpec<Float> = spring(),
): Modifier =
    placeholder(
        visible = visible,
        color = if (color.isSpecified) color else PlaceholderDefaults.color(),
        shape = shape ?: MaterialTheme.shapes.small,
        highlight = highlight,
        placeholderFadeAnimationSpec = placeholderFadeAnimationSpec,
        contentFadeAnimationSpec = contentFadeAnimationSpec,
    )

@Composable
@ReadOnlyComposable
public fun PlaceholderDefaults.color(
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentAlpha: Float = 0.1f,
): Color = contentColor.copy(contentAlpha).compositeOver(backgroundColor)

@Composable
public fun RichText(
    text: UiRichText,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Text(
        text = text.innerText,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
public fun NetworkImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    placeholder: Painter? = null,
    customHeaders: ImmutableMap<String, String>? = null,
) {
    val platformContext = LocalPlatformContext.current
    val placeholderColor = MaterialTheme.colorScheme.outlineVariant
    val allowHardware = LocalNetworkImageAllowHardware.current
    val effectiveModel = if (rememberImagesEnabled()) model else null
    AsyncImage(
        model =
            remember(effectiveModel, platformContext, customHeaders, allowHardware) {
                ImageRequest
                    .Builder(platformContext)
                    .data(effectiveModel)
                    .crossfade(allowHardware)
                    .let { req ->
                        if (customHeaders != null) {
                            req.httpHeaders(
                                NetworkHeaders
                                    .Builder()
                                    .apply { customHeaders.forEach { (k, v) -> set(k, v) } }
                                    .build(),
                            )
                        } else {
                            req
                        }
                    }.let { req ->
                        if (effectiveModel != null) req.memoryCacheKey(effectiveModel) else req
                    }.allowHardware(allowHardware)
                    .build()
            },
        contentDescription = contentDescription,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        modifier = modifier,
        filterQuality = filterQuality,
        placeholder = placeholder ?: remember(placeholderColor) { ColorPainter(placeholderColor) },
    )
}

@Composable
public fun SubcomposeNetworkImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    customHeaders: ImmutableMap<String, String>? = null,
) {
    val platformContext = LocalPlatformContext.current
    val allowHardware = LocalNetworkImageAllowHardware.current
    SubcomposeAsyncImage(
        model =
            remember(model, platformContext, customHeaders, allowHardware) {
                ImageRequest
                    .Builder(platformContext)
                    .data(model)
                    .crossfade(allowHardware)
                    .let { req ->
                        if (customHeaders != null) {
                            req.httpHeaders(
                                NetworkHeaders
                                    .Builder()
                                    .apply { customHeaders.forEach { (k, v) -> set(k, v) } }
                                    .build(),
                            )
                        } else {
                            req
                        }
                    }.let { req ->
                        if (model != null) req.memoryCacheKey(model) else req
                    }.allowHardware(allowHardware)
                    .build()
            },
        contentDescription = contentDescription,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        modifier = modifier,
        filterQuality = filterQuality,
    )
}
