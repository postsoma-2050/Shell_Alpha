package com.cylonid.nativealpha.util

object UrlSanitizer {
    // Validated 88 tracking parameters matching Hermit Lite Apps Browser's privacy tracking protection engine
    private val TRACKING_PARAMETERS = setOf(
        "__hsfp", "__hssc", "__hstc", "__s", "_hsenc", "_hsmi", "_openstat",
        "action_object_map", "action_ref_map", "action_type_map",
        "cvid", "dclid",
        "fb_action_ids", "fb_action_types", "fb_ref", "fb_source", "fbclid",
        "ga_campaign", "ga_content", "ga_medium", "ga_place", "ga_source", "ga_term",
        "gbraid", "gclid", "gs_l",
        "hsa_acc", "hsa_ad", "hsa_cam", "hsa_grp", "hsa_kw", "hsa_mt", "hsa_net", "hsa_src", "hsa_tgt", "hsa_ver", "hsctatracking",
        "icid", "igshid",
        "mc_cid", "mc_eid", "mkt_tok", "ml_subscriber", "ml_subscriber_hash", "msclkid",
        "oicd", "oly_anon_id", "oly_enc_id", "otc",
        "rb_clickid",
        "s_cid", "soc_src", "soc_trk",
        "stm_campaign", "stm_cid", "stm_content", "stm_medium", "stm_name", "stm_reader", "stm_referrer", "stm_social", "stm_social-type", "stm_source", "stm_term",
        "twclid",
        "utm_brand", "utm_campaign", "utm_cid", "utm_content", "utm_id", "utm_medium", "utm_name", "utm_place", "utm_pubreferrer", "utm_reader", "utm_referrer", "utm_social", "utm_social-type", "utm_source", "utm_swu", "utm_term", "utm_userid", "utm_viz_id",
        "vero_conv", "vero_id",
        "wbraid", "wickedid",
        "yclid"
    )

    private val SENSITIVE_PARAMETERS = setOf(
        "code",
        "state",
        "token",
        "id_token",
        "access_token",
        "sig",
        "signature",
        "x-amz-signature",
        "expires"
    )

    @JvmStatic
    fun sanitizeTrackingParameters(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) {
            return rawUrl
        }

        val scheme = rawUrl.substringBefore(":", "").lowercase()
        if (scheme.isBlank()) {
            return rawUrl
        }
        if (scheme != "http" && scheme != "https") {
            return rawUrl
        }

        val fragmentStart = rawUrl.indexOf('#')
        val withoutFragment = if (fragmentStart >= 0) {
            rawUrl.substring(0, fragmentStart)
        } else {
            rawUrl
        }
        val fragment = if (fragmentStart >= 0) rawUrl.substring(fragmentStart) else ""
        val queryStart = withoutFragment.indexOf('?')
        if (queryStart < 0) {
            return rawUrl
        }
        val urlWithoutQuery = withoutFragment.substring(0, queryStart)
        val rawQuery = withoutFragment.substring(queryStart + 1)
        val queryParts = rawQuery.split("&")
        val keys = queryParts.map { it.substringBefore("=").decodeQueryComponent().lowercase() }
        if (keys.any { it in SENSITIVE_PARAMETERS }) {
            return rawUrl
        }

        val filtered = queryParts.filterNot {
            it.substringBefore("=").decodeQueryComponent().lowercase() in TRACKING_PARAMETERS
        }
        if (filtered.size == queryParts.size) {
            return rawUrl
        }

        return if (filtered.isEmpty()) {
            urlWithoutQuery + fragment
        } else {
            urlWithoutQuery + "?" + filtered.joinToString("&") + fragment
        }
    }

    private fun String.decodeQueryComponent(): String =
        try {
            java.net.URLDecoder.decode(this, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            this
        }
}
