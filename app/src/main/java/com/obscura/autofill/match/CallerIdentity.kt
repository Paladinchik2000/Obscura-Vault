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
    val certificateHashes: Set<String>,
    /** What the package is signed with right now; this is what a new link records. */
    val currentCertificateHash: String
) {
    companion object {

        /** Null when the package cannot be read: without a certificate nothing may be offered. */
        fun of(context: Context, packageName: String): CallerIdentity? {
            val manager = context.packageManager
            val current = currentSigningCertificates(manager, packageName)
            val all = current + historicSigningCertificates(manager, packageName)
            if (all.isEmpty()) return null
            return CallerIdentity(
                packageName = packageName,
                certificateHashes = all,
                currentCertificateHash = current.firstOrNull() ?: all.first()
            )
        }

        /** What the package is signed with today; several entries when it has several signers. */
        private fun currentSigningCertificates(packageManager: PackageManager, packageName: String): Set<String> =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val info = packageManager
                        .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                        .signingInfo ?: return emptySet()
                    info.apkContentsSigners.orEmpty().toHashes()
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                        .signatures.orEmpty().toHashes()
                }
            }.getOrDefault(emptySet())

        /**
         * Certificates the package was signed with before its key was rotated (APK signature
         * scheme v3). A link saved before a rotation has to keep working afterwards.
         */
        private fun historicSigningCertificates(packageManager: PackageManager, packageName: String): Set<String> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptySet()
            return runCatching {
                val info = packageManager
                    .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo ?: return emptySet()
                if (info.hasMultipleSigners()) emptySet() else info.signingCertificateHistory.orEmpty().toHashes()
            }.getOrDefault(emptySet())
        }

        private fun Array<out Signature>.toHashes(): Set<String> =
            mapNotNullTo(HashSet()) { signature ->
                runCatching { sha256Hex(signature.toByteArray()) }.getOrNull()
            }

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
