package dev.dimension.flare.data.privacy

/**
 * Query parameters that identify a specific user, session, or referral.
 *
 * These travel in URLs, which means they reach the server, the CDN edge, and every proxy in
 * between — including logs Flare does not control. Removing them at the client is the only place
 * they can be reliably dropped.
 */
public object TrackingParameters {
    public val known: Set<String> =
        setOf(
            // X
            "twclid",
            "twgr",
            "twcamp",
            "twterm",
            "twcon",
            "twsource",
            // Meta
            "fbclid",
            "gclid",
            "dclid",
            "msclkid",
            "yclid",
            "igshid",
            "igsh",
            "mibextid",
            // Generic
            "si",
            "ref",
            "ref_src",
            "ref_url",
            "referrer",
            "source",
            "spm",
            "scm",
            "share_source",
            "share_medium",
            "campaign",
            "campaign_id",
            "utm_source",
            "utm_medium",
            "utm_campaign",
            "utm_term",
            "utm_content",
            "utm_id",
            "utm_source_platform",
            "gclid",
            "_hsenc",
            "_hsmi",
            "hsctatracking",
            "mc_cid",
            "mc_eid",
            "vero_id",
            "oly_enc_id",
            "oly_anon_id",
            "wickedid",
            // Misc ad/analytics
            "at_recipient_id",
            "at_recipient_list",
            "s_cid",
            "tsc",
            "bsft_clkid",
        )

    /**
     * Whether [name] should be dropped.
     *
     * Exact match only. Substring matching would corrupt legitimate parameters such as
     * `refund_id` or `source_language`.
     */
    public fun shouldStrip(name: String): Boolean = name.lowercase() in known
}
