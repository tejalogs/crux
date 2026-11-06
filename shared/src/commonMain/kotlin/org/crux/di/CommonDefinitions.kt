package org.crux.di

import kotlinx.coroutines.CoroutineScope
import org.crux.common.PlatformDispatchers
import org.koin.core.annotation.Single

@Single
internal fun coroutineScope(): CoroutineScope = CoroutineScope(PlatformDispatchers.IO)
