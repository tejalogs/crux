package org.crux.common

import kotlinx.coroutines.CoroutineDispatcher

public expect object PlatformDispatchers {
    public val IO: CoroutineDispatcher
}
