package org.crux.ui.component

import coil3.request.ImageRequest

internal expect fun ImageRequest.Builder.applyAllowHardware(allowHardware: Boolean): ImageRequest.Builder
