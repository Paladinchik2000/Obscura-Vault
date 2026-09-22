package com.obscura.autofill.match

import android.content.Context
import com.obscura.R
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * The Public Suffix List, bundled in the APK.
 *
 * Needed to tell "the part everyone shares" from "someone's domain": github.io and co.uk are
 * public suffixes, so a saved entry for one of them would otherwise match every site under it.
 * The app has no INTERNET permission and never will, so the list ships with the build and is
 * refreshed by hand.
 *
 * Source: https://publicsuffix.org/list/public_suffix_list.dat, fetched 2026-09-22,
 * sha256 e81c6f5f11359a79a2479238e732e08bd8521071fd95ee47053471e3426d7b54 of the original file.
 * Comments and blank lines were stripped before gzipping; 10330 rules remain.
 * The list is published by Mozilla under the Mozilla Public License 2.0.
 */
class PublicSuffixList internal constructor(private val source: () -> InputStream) {

    private data class Rules(
        val exact: Set<String>,
        /** For a rule `*.ck` this holds `ck`: any single label in front of it is part of the suffix. */
        val wildcardParents: Set<String>,
        /** For a rule `!www.ck` this holds `www.ck`. */
        val exceptions: Set<String>
    )

    private val rules: Rules by lazy { parse() }

    private fun parse(): Rules {
        val exact = HashSet<String>(16_384)
        val wildcardParents = HashSet<String>(64)
        val exceptions = HashSet<String>(64)

        GZIPInputStream(source()).bufferedReader().forEachLine { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("//") -> Unit
                line.startsWith("!") -> exceptions += line.substring(1).lowercase()
                line.startsWith("*.") -> wildcardParents += line.substring(2).lowercase()
                else -> exact += line.lowercase()
            }
        }
        return Rules(exact, wildcardParents, exceptions)
    }

    /**
     * The public suffix of [host], following the algorithm on publicsuffix.org: exception rules
     * win, then the longest matching rule, and a host that matches nothing keeps its last label.
     */
    fun publicSuffixOf(host: String): String {
        val labels = host.split('.')

        for (i in labels.indices) {
            val candidate = labels.subList(i, labels.size).joinToString(".")
            // An exception rule means the rule's own first label is *not* part of the suffix.
            if (candidate in rules.exceptions) return labels.subList(i + 1, labels.size).joinToString(".")
        }

        // The implicit "*" rule: without a match, the last label is the suffix.
        var suffixLabels = 1
        for (i in labels.indices) {
            val length = labels.size - i
            if (length <= suffixLabels) continue
            val candidate = labels.subList(i, labels.size).joinToString(".")
            if (candidate in rules.exact) suffixLabels = length
        }
        for (i in 0 until labels.size - 1) {
            val length = labels.size - i
            if (length <= suffixLabels) continue
            if (labels.subList(i + 1, labels.size).joinToString(".") in rules.wildcardParents) {
                suffixLabels = length
            }
        }

        return labels.subList(labels.size - minOf(suffixLabels, labels.size), labels.size).joinToString(".")
    }

    /** True when [host] is only a shared suffix, e.g. `co.uk` or `github.io`. */
    fun isPublicSuffix(host: String): Boolean = publicSuffixOf(host) == host

    /** The public suffix plus one label, e.g. `bank.co.uk`; null when [host] is itself a suffix. */
    fun registrableDomainOf(host: String): String? {
        val suffix = publicSuffixOf(host)
        if (suffix == host) return null
        val suffixLabels = suffix.split('.').size
        val labels = host.split('.')
        if (labels.size <= suffixLabels) return null
        return labels.subList(labels.size - suffixLabels - 1, labels.size).joinToString(".")
    }

    companion object {
        fun fromResources(context: Context): PublicSuffixList =
            PublicSuffixList { context.applicationContext.resources.openRawResource(R.raw.public_suffix_list) }
    }
}
