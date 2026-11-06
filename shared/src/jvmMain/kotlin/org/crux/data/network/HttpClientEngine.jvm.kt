package org.crux.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

internal actual val httpClientEngine: HttpClientEngine = OkHttp.create { }
