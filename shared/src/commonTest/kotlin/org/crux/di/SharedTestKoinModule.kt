package org.crux.di

import org.crux.common.InAppNotification
import org.crux.common.Message
import org.crux.data.io.FileStorage
import org.crux.data.io.InMemoryFileStorage
import org.crux.data.model.tab.TimelineResolver
import org.crux.model.PlatformRuntimeData
import org.crux.testPlatformRuntimeData
import org.crux.unavailableAccountService
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.core.definition.Definition
import org.koin.dsl.KoinAppDeclaration
import org.koin.core.module.Module as KoinModule
import org.koin.plugin.module.dsl.startKoin as startKoinWithModules

@KoinApplication(configurations = ["test"])
internal class SharedTestKoinApplication

internal fun startKoin(appDeclaration: KoinAppDeclaration? = null) = startKoinWithModules<SharedTestKoinApplication>(appDeclaration)

// Koin 1.2.1 still emits duplicate Native DSL hints when test-local types are registered more than once.
// Pass dependencies explicitly: the compiler checks get() calls but cannot see registrations through this wrapper.
internal inline fun <reified T> KoinModule.testSingle(noinline definition: Definition<T>) = single(definition = definition)

@Module
@Configuration("test")
internal class SharedTestKoinModule {
    @Single
    fun platformRuntimeData(): PlatformRuntimeData = testPlatformRuntimeData()

    @Single
    fun fileStorage(): FileStorage = InMemoryFileStorage()

    @Single
    fun timelineResolver(): TimelineResolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService())

    @Single
    fun inAppNotification(): InAppNotification = TestInAppNotification
}

private object TestInAppNotification : InAppNotification {
    override fun onProgress(
        message: Message,
        progress: Int,
        total: Int,
    ) = Unit

    override fun onSuccess(message: Message) = Unit

    override fun onError(
        message: Message,
        throwable: Throwable,
    ) = Unit
}
