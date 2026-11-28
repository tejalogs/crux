package org.crux.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

public val screenHorizontalPadding: Dp = 16.dp

public val LocalIsLightTheme: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { true }

@Composable
@ReadOnlyComposable
public fun isLightTheme(): Boolean = LocalIsLightTheme.current

public object ListCardShapes {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    public fun container(): CornerBasedShape =
        ListItemDefaults.single().shape as? CornerBasedShape ?: MaterialTheme.shapes.large

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    public fun item(): Shape = ListItemDefaults.item().shape

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    public fun topCard(): Shape = ListItemDefaults.first().shape

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    public fun bottomCard(): Shape = ListItemDefaults.last().shape
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun ListItemDefaults.first(): ListItemShapes = ListItemDefaults.segmentedShapes(0, 2)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun ListItemDefaults.item(): ListItemShapes = ListItemDefaults.segmentedShapes(1, 3)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun ListItemDefaults.last(): ListItemShapes = ListItemDefaults.segmentedShapes(1, 2)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun ListItemDefaults.segmentedShapes2(
    index: Int,
    count: Int,
): ListItemShapes =
    if (count == 1) {
        ListItemDefaults.single()
    } else {
        ListItemDefaults.segmentedShapes(
            index = index,
            count = count,
        )
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun ListItemDefaults.single(): ListItemShapes {
    val first = first()
    val last = last()
    val firstShape = first.shape
    val lastShape = last.shape
    return if (firstShape is CornerBasedShape && lastShape is CornerBasedShape) {
        first.copy(
            shape =
                firstShape.copy(
                    bottomStart = lastShape.bottomStart,
                    bottomEnd = lastShape.bottomEnd,
                ),
        )
    } else {
        ListItemDefaults.segmentedShapes(0, 1)
    }
}
