package dev.dimension.flare.data.privacy

/**
 * Classification of an outgoing request for the on-device traffic log.
 *
 * The categories are chosen so a user can answer "why did Flare contact this host, and did it
 * send anything identifying?" without reading a URL.
 */
public enum class PrivacyCategory {
    /** Timeline, post, and profile fetches that carry the content the user asked for. */
    Content,

    /** Media bytes. Large, but not identifying on its own. */
    Media,

    /** Counters and "seen"/impression pings whose only purpose is behavioural measurement. */
    Telemetry,

    /** Discovery and autocomplete calls whose only purpose is profiling interest. */
    Profiling,

    /** Authentication and session establishment. */
    Auth,

    /** Anything not matched by a rule. */
    Unknown,
}

@kotlin.native.HiddenFromObjC
public data class PrivacyVerdict(
    val category: PrivacyCategory,
    val host: String,
    val path: String,
    val method: String,
    /** Header/query names removed by policy, never their values. */
    val strippedParameters: List<String> = emptyList(),
    /** True when the request was refused by policy rather than sent. */
    val blocked: Boolean = false,
) {
    public val isTelemetry: Boolean get() = category == PrivacyCategory.Telemetry
}
