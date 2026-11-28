package org.crux.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal object PlatformDispatchers {
    val IO: CoroutineDispatcher = Dispatchers.IO
    val Default: CoroutineDispatcher = Dispatchers.Default
    val Main: CoroutineDispatcher = Dispatchers.Main
}
