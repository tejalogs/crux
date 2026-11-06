package org.crux.data.privacy

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.encodedPath
import io.ktor.util.AttributeKey

/**
 * Request-pipeline privacy policy, installed on every Crux HTTP client.
 *
 * Runs before the request is sent and applies four things in order: host blocking, endpoint
 * blocking, query-parameter scrubbing, and header policy. Everything it removes is recorded by
 * name only in [PrivacyLog], so the user can audit the client without the audit itself becoming a
 * store of identifiers.
 */
internal val PrivacyPlugin =
    createClientPlugin("PrivacyPlugin", ::PrivacyPluginConfig) {
        val logResponses = pluginConfig.logResponses
        onRequest { request, _ ->
            val settings = PrivacyPolicyCache.current()
            val host = request.url.host.lowercase()
            val path = request.url.encodedPath

            if (PrivacyClassifier.hostMatches(host, settings.blockedHosts)) {
                PrivacyLog.get().record(
                    verdict =
                        PrivacyVerdict(
                            category = PrivacyCategory.Unknown,
                            host = host,
                            path = path,
                            method = request.method.value,
                            blocked = true,
                        ),
                )
                throw BlockedByPrivacyException(host)
            }

            val verdict =
                PrivacyClassifier.classify(
                    method = request.method.value,
                    host = host,
                    path = path,
                    blockTelemetry = settings.blockTelemetry,
                    blockProfiling = settings.blockProfiling,
                )

            if (verdict.blocked) {
                PrivacyLog.get().record(verdict = verdict)
                throw BlockedByPrivacyException(path)
            }

            val stripped =
                if (settings.scrubTrackingParameters) {
                    request.removeTrackingParameters()
                } else {
                    emptyList()
                }

            if (settings.stripFingerprintHeaders) {
                request.stripFingerprintHeaders()
            }

            if (settings.loggingEnabled) {
                // Recorded on the way out so the log reflects what was *attempted*, including
                // requests that later fail. Relying on onResponse alone silently undercounts.
                val sequence = PrivacyLog.get().record(verdict = verdict, strippedParameters = stripped)
                request.attributes.put(PrivacyVerdictKey, verdict)
                request.attributes.put(PrivacyStrippedKey, stripped)
                request.attributes.put(PrivacySequenceKey, sequence)
            }
        }

        onResponse { response ->
            // The attempt was already recorded in onRequest; this only attaches the status code so
            // the log shows whether it actually succeeded. Recording again here would double-count.
            if (!logResponses) return@onResponse
            val request = response.call.request
            val verdict = request.attributes.getOrNull(PrivacyVerdictKey) ?: return@onResponse
            PrivacyLog.get().attachStatus(
                sequence = request.attributes.getOrNull(PrivacySequenceKey) ?: return@onResponse,
                status = response.status.value,
            )
        }
    }

internal val PrivacyVerdictKey: AttributeKey<PrivacyVerdict> = AttributeKey("CruxPrivacyVerdict")
internal val PrivacyStrippedKey: AttributeKey<List<String>> = AttributeKey("CruxPrivacyStripped")
internal val PrivacySequenceKey: AttributeKey<Long> = AttributeKey("CruxPrivacySequence")

/**
 * Removes known tracking parameters from this request's URL, returning the removed names.
 */
internal fun HttpRequestBuilder.removeTrackingParameters(): List<String> {
    val parameters = url.parameters
    val doomed = parameters.names().filter { TrackingParameters.shouldStrip(it) }
    if (doomed.isEmpty()) return emptyList()
    doomed.forEach { parameters.remove(it) }
    return doomed
}

/**
 * Removes client-fingerprint headers.
 *
 * Only meaningful where the header is not required for the request to be accepted; callers gate
 * this behind an explicit user preference for that reason.
 */
internal fun HttpRequestBuilder.stripFingerprintHeaders() {
    headers.remove("Sec-Ch-Ua")
    headers.remove("Sec-Ch-Ua-Mobile")
    headers.remove("Sec-Ch-Ua-Platform")
    headers.remove("Sec-Ch-Ua-Arch")
    headers.remove("Sec-Ch-Ua-Full-Version-List")
    headers.remove("Sec-Ch-Ua-Platform-Version")
    headers.remove("Sec-Ch-Ua-Model")
    headers.remove("Sec-Ch-Ua-Bitness")
    headers.remove("X-Requested-With")
}

/** Thrown to abort a request that privacy policy refused to send. */
public class BlockedByPrivacyException(
    public val target: String,
) : RuntimeException("Blocked by privacy policy: $target")

internal class PrivacyPluginConfig {
    var accountKey: String? = null
    var logResponses: Boolean = true
}

/** Convenience installer so call sites read as one line. */
public fun HttpClientConfig<*>.installPrivacy(accountKey: String? = null) {
    install(PrivacyPlugin) {
        this.accountKey = accountKey
        this.logResponses = true
    }
}
