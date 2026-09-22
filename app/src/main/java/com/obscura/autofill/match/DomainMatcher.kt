package com.obscura.autofill.match

import java.net.URI

/**
 * Decides whether a saved site matches the site a form belongs to.
 *
 * The rule is deliberately narrow: the observed host must be the saved domain itself or a
 * subdomain of it. `evil-bank.com` and `bank.com.evil.com` share text with `bank.com` and match
 * neither. A saved value that is only a public suffix (`co.uk`, `github.io`) matches nothing at
 * all, or one entry would cover every site under it.
 */
class DomainMatcher(private val publicSuffixList: PublicSuffixList) {

    /** True when an entry saved for [savedHost] may be offered on [observedHost]. */
    fun matches(savedHost: String, observedHost: String): Boolean {
        val saved = normalizeHost(savedHost) ?: return false
        val observed = normalizeHost(observedHost) ?: return false

        // An address has no domain hierarchy: only the very same address counts.
        if (isIpAddress(saved) || isIpAddress(observed)) return saved == observed

        if (publicSuffixList.isPublicSuffix(saved)) return false
        return observed == saved || observed.endsWith(".$saved")
    }

    /** What to store for a site: the host itself, refused when it is only a public suffix. */
    fun storableHost(rawValue: String): String? {
        val host = hostOf(rawValue) ?: return null
        if (isIpAddress(host)) return host
        if (publicSuffixList.isPublicSuffix(host)) return null
        // A host with no registrable domain is a suffix in disguise, e.g. a bare TLD.
        if (publicSuffixList.registrableDomainOf(host) == null) return null
        return host
    }

    companion object {
        private val HOST_PATTERN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$")
        private val IPV4_PATTERN = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

        /**
         * Pulls a host out of a stored value: `https://github.com/login`, `github.com:8443` or
         * plain `github.com`. Anything that is not a host comes back as null.
         */
        fun hostOf(rawValue: String): String? {
            val value = rawValue.trim()
            if (value.isEmpty()) return null

            val candidate = if (value.contains("://")) {
                runCatching { URI(value).host }.getOrNull() ?: return null
            } else {
                // No scheme: cut anything a URL could still carry after the host.
                value.substringBefore('/').substringBefore('?').substringBefore('#')
                    .substringAfterLast('@')
            }
            return normalizeHost(candidate)
        }

        fun normalizeHost(rawHost: String?): String? {
            var host = rawHost?.trim()?.lowercase() ?: return null
            if (host.startsWith("[") && host.endsWith("]")) return host // IPv6 literal, used as is
            host = host.substringBefore('/')
            // A port never belongs to the host; an IPv6 literal has none in this branch.
            if (host.count { it == ':' } == 1) host = host.substringBefore(':')
            host = host.trimEnd('.')
            if (host.isEmpty() || !HOST_PATTERN.matches(host)) return null
            return host
        }

        fun isIpAddress(host: String): Boolean =
            IPV4_PATTERN.matches(host) || (host.startsWith("[") && host.endsWith("]"))
    }
}
