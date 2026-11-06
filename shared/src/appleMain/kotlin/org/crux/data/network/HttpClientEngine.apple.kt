package org.crux.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual val httpClientEngine: HttpClientEngine =
    Darwin.create {
    }
