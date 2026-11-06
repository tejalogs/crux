package org.crux.ui.render

import kotlin.time.Instant

internal actual typealias PlatformDateTime = Instant

internal actual fun Instant.toPlatform(): PlatformDateTime = this
