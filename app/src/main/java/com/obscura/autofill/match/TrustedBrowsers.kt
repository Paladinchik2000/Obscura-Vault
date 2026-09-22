package com.obscura.autofill.match

/**
 * Browsers whose webDomain we believe.
 *
 * A browser tells the autofill framework which site a form belongs to. Any other app could put
 * "bank.com" into its own layout and be handed a bank password, so the domain is only taken from
 * a browser we recognise by package name *and* signing certificate. Everything else is treated as
 * a plain app: matched by package and certificate, never by domain.
 *
 * Hashes are lower-case hex SHA-256 of the signing certificate.
 *
 * Source: Android Password Store, FeatureAndTrustDetection.kt (trustedBrowserCertificateHashes),
 * commit 282e9519ae5c of 2024-08-13, converted from base64 to hex. Cross-checked against a real
 * package: Chrome from the android-36.1 emulator prints the same digest through apksigner.
 *
 * Samsung Internet (com.sec.android.app.sbrowser) is deliberately absent: no hash for it could be
 * verified from a package or a public list here. Until one is added from the device
 * (`apksigner verify --print-certs`), it counts as an ordinary app and its webDomain is ignored.
 */
object TrustedBrowsers {

    private val CERTIFICATE_HASHES: Map<String, Set<String>> = mapOf(
        "app.vanadium.browser" to setOf(
            "c6adb8b83c6d4c17d292afde56fd488a51d316ff8f2c11c5410223bff8a7dbb3"
        ),
        "com.android.chrome" to setOf(
            "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"
        ),
        "com.brave.browser" to setOf(
            "9c2db70513515fdbfbbc585b3edf3d7123d4dc67c94ffd306361c1d79bbf18ac"
        ),
        "com.chrome.beta" to setOf(
            "da633d34b69e63ae2103b49d53ce052fc5f7f3c53aab94fdc2a208bdfd14249c"
        ),
        "com.chrome.canary" to setOf(
            "2019dfa1fb23efbf70c5bcd1443c5beab04f3f2ff4366e9ac1e3457639a24cfc"
        ),
        "com.chrome.dev" to setOf(
            "9044ee5fee4bbc5e21dd44665431c4eb1f1f71a32716a0bc927bcbb39233cabf"
        ),
        "com.duckduckgo.mobile.android" to setOf(
            "bb7bb31c573c46a1da7fc5c528a6acf432108456feec50810c7f33694eb3d2d4",
            "f0707d021c0bf3e6f8dcc11ba3f5700425d597dca301a31e210556934ebb1b0a"
        ),
        "com.kiwibrowser.browser" to setOf(
            "c069ea966332e91e0a0c3cc577e6f509fe3d9ddaf7015ad0c5c5ef8fda3f8628"
        ),
        "com.microsoft.emmx" to setOf(
            "01e1999710a82c2749b4d50c445dc85d670b6136089d0a766a73827c82a1eac9"
        ),
        "com.opera.mini.native" to setOf(
            "57acbc525f1b2ebd19196cd6f014397cc910fd18841e0ae850febc3e1e593ff2"
        ),
        "com.opera.touch" to setOf(
            "aad8e204d24d177934c9cd0c63cc6aa38efbf42c4a6957097e2210f57faa67aa"
        ),
        "com.vivaldi.browser" to setOf(
            "e8a78544655ba8c09817f732768f5689b1662ec4b2bc5a0bc0ec138d33ca3d1e"
        ),
        "org.bromite.bromite" to setOf(
            "e1ee5cd076d7b0dc84cb2b45fb78b86df2eb39a3b6c56ba3dc292a5e0c3b9504"
        ),
        "org.cromite.cromite" to setOf(
            "633fa41d8211d6d0916a819b89668c6de92e64232da67f9d16fd81c3b7e923ff"
        ),
        "org.mozilla.fenix" to setOf(
            "5004779088e7f988d5bc5cc5f8798febf4f8cd084a1b2a46efd4c8ee4aeaf211"
        ),
        "org.mozilla.firefox" to setOf(
            "a78b62a5165b4494b2fead9e76a280d22d937fee6251aece599446b2ea319b04"
        ),
        "org.mozilla.firefox_beta" to setOf(
            "a78b62a5165b4494b2fead9e76a280d22d937fee6251aece599446b2ea319b04"
        ),
        "org.mozilla.focus" to setOf(
            "6203a473be36d64ee37f87fa500edbc79eab930610ab9b9fa4ca7d5c1f1b4ffc"
        ),
        "org.mozilla.klar" to setOf(
            "6203a473be36d64ee37f87fa500edbc79eab930610ab9b9fa4ca7d5c1f1b4ffc"
        ),
        "org.torproject.torbrowser" to setOf(
            "20061f045e737c67375c17794cfedb436a03cec6bacb7cb9f96642205ca2cec8"
        ),
        "org.ungoogled.chromium.stable" to setOf(
            "dbd50e3b9717a313bf7bf847de13aee9b6ed835332e2d2ba122936626e4aaed9"
        ),
    )

    /** True when [packageName] is a browser we know and [certificateHashes] contains its own. */
    fun isTrusted(packageName: String, certificateHashes: Collection<String>): Boolean {
        val known = CERTIFICATE_HASHES[packageName] ?: return false
        return certificateHashes.any { it.lowercase() in known }
    }

    fun isKnownBrowserPackage(packageName: String): Boolean = packageName in CERTIFICATE_HASHES
}
