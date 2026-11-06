package org.crux.feature.agent.database

import org.crux.common.PlatformDispatchers
import org.crux.data.database.DriverFactory
import org.crux.data.database.createDatabaseDriver
import org.koin.core.annotation.Single

private const val AGENT_DATABASE_NAME = "agent.db"

@Single
internal fun provideAgentDatabase(driverFactory: DriverFactory): AgentDatabase =
    driverFactory
        .createBuilder<AgentDatabase>(AGENT_DATABASE_NAME)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(createDatabaseDriver())
        .setQueryCoroutineContext(PlatformDispatchers.IO)
        .build()
