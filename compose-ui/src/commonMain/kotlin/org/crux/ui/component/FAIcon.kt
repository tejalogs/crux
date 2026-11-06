package org.crux.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.crux.ui.component.platform.PlatformIcon
import org.crux.ui.theme.PlatformContentColor

@Composable
public fun FAIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = PlatformContentColor.current,
) {
    PlatformIcon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier =
            modifier
                .size(20.dp),
        tint = tint,
    )
}
