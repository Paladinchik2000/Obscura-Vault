package com.obscura.autofill.match

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * Who is asking to be filled: a package name and the SHA-256 of every certificate that package is
 * legitimately signed with.
 *
 * The package name alone proves nothing — an app installed outside a store can claim any name —
 * so a saved link only matches when the certificate matches too.
 */
data class CallerIdentity(
    val packageName: String,
    /** Lower-case hex SHA-256 of the signing certificates, including rotated-away ones. */
    val certificateHashes: Set<String>
) {
    companion object {

        /** Null when the package cannot be read: without a certificate nothing may be offered. */
        fun of(context: Context, packageName: String): CallerIdentity? {
            val hashes = signingCertificates(context.packageManager, packageName)
            if (hashes.isEmpty()) return null
            return CallerIdentity(packageName, hashes)
        }

        private fun signingCertificates(packageManager: PackageManager, packageName: String): Set<String> =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val info = packageManager
                        .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                        .signingInfo ?: return emptySet()
                    // An app signed by several parties has all of them at once; an app whose key
                    // was rotated (signature scheme v3) keeps the older certificates in history,
                    // and a link saved before the rotation has to keep working.
                    val signatures = if (info.hasMultipleSigners()) {
                        info.apkContentsSigners
                    } else {
                        info.signingCertificateHistory
                    }
                    signatures.orEmpty().toHashes()
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                        .signatures.orEmpty().toHashes()
                }
            }.getOrDefault(emptySet())

        private fun Array<out Signature>.toHashes(): Set<String> =
            mapNotNullTo(HashSet()) { signature ->
                runCatching { sha256Hex(signature.toByteArray()) }.getOrNull()
            }

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
