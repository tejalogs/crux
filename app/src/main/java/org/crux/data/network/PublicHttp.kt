package org.crux.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom

private const val MAX_PUBLIC_REDIRECTS = 5

private val REDIRECT_STATUSES =
    setOf(
        HttpStatusCode.MovedPermanently,
        HttpStatusCode.Found,
        HttpStatusCode.SeeOther,
        HttpStatusCode.TemporaryRedirect,
        HttpStatusCode.PermanentRedirect,
    )

/**
 * A client for untrusted, user- or feed-supplied URLs. Redirects are followed by [getPublic],
 * never by the engine, so every hop is checked against [PublicUrlValidator].
 */
public fun publicWebClient(): HttpClient =
    ktorClient {
        followRedirects = false
    }

/**
 * GET [url] following up to [MAX_PUBLIC_REDIRECTS] redirects, refusing any hop that points at
 * localhost, a private network or another non-public address. Without this, a feed or article
 * host could answer with a redirect into the user's home network.
 */
public suspend fun HttpClient.getPublic(
    url: String,
    block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    var current = PublicUrlValidator.requireSafePublicHttpUrl(url)
    repeat(MAX_PUBLIC_REDIRECTS + 1) {
        val response = get(current, block)
        if (response.status !in REDIRECT_STATUSES) return response
        val location =
            response.headers[HttpHeaders.Location]
                ?: return response
        current = PublicUrlValidator.requireSafePublicHttpUrl(resolveRedirect(current, location))
    }
    throw IllegalStateException("Too many redirects.")
}

internal fun resolveRedirect(
    base: String,
    location: String,
): String = URLBuilder(base).takeFrom(location).buildString()
