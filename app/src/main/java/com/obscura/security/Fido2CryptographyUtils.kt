package com.obscura.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.Keep
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Fido2CryptographyUtils
 *
 * Cryptographic utility object for Passkey (FIDO2 / WebAuthn) operations adhering to
 * W3C Web Authentication & FIDO Alliance CTAP2 specifications:
 *
 * 1. P-256 (secp256r1 / prime256v1 / ES256) KeyPair Generation (Software & Android Keystore).
 * 2. SHA-256 Hashing for clientDataJSON (clientDataHash) and RP ID (rpIdHash).
 * 3. Authenticator Data (authData) structure construction (flags UP=0x01, UV=0x04, AT=0x40).
 * 4. FIDO2 Assertion signing (SHA256withECDSA over authData || clientDataHash).
 * 5. COSE Key encoding (RFC 8152 / RFC 9052) for ES256 (-7) public keys.
 * 6. WebAuthn JSON registration & assertion response formatting.
 */
@Keep
object Fido2CryptographyUtils {

    const val EC_CURVE_P256 = "secp256r1"
    const val SIGN_ALGORITHM_ECDSA_SHA256 = "SHA256withECDSA"
    const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"

    // FIDO2 / CTAP2 Authenticator Flags (Bit masks)
    const val FLAG_USER_PRESENT: Byte = 0x01.toByte()      // Bit 0: UP
    const val FLAG_USER_VERIFIED: Byte = 0x04.toByte()     // Bit 2: UV (Biometrics / Device PIN)
    const val FLAG_ATTESTED_DATA: Byte = 0x40.toByte()     // Bit 6: AT (Attested Credential Data included)
    const val FLAG_EXTENSION_DATA: Byte = 0x80.toByte()    // Bit 7: ED (Extensions present)

    // COSE Algorithm Identifiers
    const val COSE_ALG_ES256 = -7
    const val COSE_KEY_TYPE_EC2 = 2
    const val COSE_CURVE_P256 = 1

    /**
     * Generates a standard exportable ECDSA P-256 (secp256r1) KeyPair.
     * Used when the private key is encrypted with the Master Vault key and stored in Room DB.
     */
    @JvmStatic
    fun generateEcP256KeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec(EC_CURVE_P256)
        keyPairGenerator.initialize(ecSpec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Generates a hardware-backed P-256 KeyPair inside Android Keystore with an alias.
     */
    @JvmStatic
    fun generateKeystoreP256KeyPair(
        alias: String,
        requireUserAuthentication: Boolean = false,
        authTimeoutSeconds: Int = -1
    ): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE_PROVIDER
        )

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE_P256))
            .setDigests(KeyProperties.DIGEST_SHA256)

        if (requireUserAuthentication) {
            builder.setUserAuthenticationRequired(true)
            if (authTimeoutSeconds > 0) {
                builder.setUserAuthenticationParameters(
                    authTimeoutSeconds,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
            }
        }

        keyPairGenerator.initialize(builder.build())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Computes the SHA-256 cryptographic digest of raw byte array.
     */
    @JvmStatic
    fun sha256(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data)
    }

    /**
     * Computes SHA-256 hash of UTF-8 string (e.g. clientDataJSON string or RP ID).
     */
    @JvmStatic
    fun sha256(data: String): ByteArray {
        return sha256(data.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Signs the FIDO2 Assertion payload according to W3C WebAuthn spec:
     * Signature payload = authenticatorData (authData) || clientDataHash
     *
     * @param privateKey The ECDSA P-256 private key
     * @param authData The 37-byte authenticator data (rpIdHash + flags + signCount)
     * @param clientDataHash The 32-byte SHA-256 digest of clientDataJSON
     * @return ASN.1 DER-encoded ECDSA signature bytes
     */
    @JvmStatic
    fun signFido2Assertion(
        privateKey: PrivateKey,
        authData: ByteArray,
        clientDataHash: ByteArray
    ): ByteArray {
        // Concatenate authData || clientDataHash
        val dataToSign = ByteArray(authData.size + clientDataHash.size)
        System.arraycopy(authData, 0, dataToSign, 0, authData.size)
        System.arraycopy(clientDataHash, 0, dataToSign, authData.size, clientDataHash.size)

        val signer = Signature.getInstance(SIGN_ALGORITHM_ECDSA_SHA256)
        signer.initSign(privateKey)
        signer.update(dataToSign)
        return signer.sign()
    }

    /**
     * Generic signature generator using SHA256withECDSA over arbitrary byte payload.
     */
    @JvmStatic
    fun signPayload(privateKey: PrivateKey, payload: ByteArray): ByteArray {
        val signer = Signature.getInstance(SIGN_ALGORITHM_ECDSA_SHA256)
        signer.initSign(privateKey)
        signer.update(payload)
        return signer.sign()
    }

    /**
     * Verifies a FIDO2 Assertion signature against (authData || clientDataHash).
     */
    @JvmStatic
    fun verifyFido2Assertion(
        publicKey: PublicKey,
        authData: ByteArray,
        clientDataHash: ByteArray,
        signature: ByteArray
    ): Boolean {
        val dataToVerify = ByteArray(authData.size + clientDataHash.size)
        System.arraycopy(authData, 0, dataToVerify, 0, authData.size)
        System.arraycopy(clientDataHash, 0, dataToVerify, authData.size, clientDataHash.size)

        val verifier = Signature.getInstance(SIGN_ALGORITHM_ECDSA_SHA256)
        verifier.initVerify(publicKey)
        verifier.update(dataToVerify)
        return verifier.verify(signature)
    }

    /**
     * Builds the standard Authenticator Data (authData) structure:
     * - 32 bytes: SHA-256 Hash of RP ID
     * - 1 byte: Flags (UP, UV, AT, ED)
     * - 4 bytes: 32-bit big-endian sign counter
     * - Optional: Attested Credential Data (for registration)
     */
    @JvmStatic
    fun buildAuthenticatorData(
        rpId: String,
        userPresent: Boolean = true,
        userVerified: Boolean = true,
        signCount: Int = 1,
        attestedCredentialData: ByteArray? = null
    ): ByteArray {
        val rpIdHash = sha256(rpId)
        val stream = ByteArrayOutputStream()

        // 1. RP ID Hash (32 bytes)
        stream.write(rpIdHash)

        // 2. Flags (1 byte)
        var flags = 0
        if (userPresent) flags = flags or FLAG_USER_PRESENT.toInt()
        if (userVerified) flags = flags or FLAG_USER_VERIFIED.toInt()
        if (attestedCredentialData != null && attestedCredentialData.isNotEmpty()) {
            flags = flags or FLAG_ATTESTED_DATA.toInt()
        }
        stream.write(flags and 0xFF)

        // 3. Sign Counter (4 bytes, Big-Endian)
        val counterBytes = ByteBuffer.allocate(4).putInt(signCount).array()
        stream.write(counterBytes)

        // 4. Attested Credential Data (if present)
        if (attestedCredentialData != null && attestedCredentialData.isNotEmpty()) {
            stream.write(attestedCredentialData)
        }

        return stream.toByteArray()
    }

    /**
     * Builds Attested Credential Data for registration:
     * - AAGUID: 16 bytes (all zeros for software authenticator)
     * - Credential ID Length: 2 bytes (Big-Endian uint16)
     * - Credential ID: raw bytes (e.g. 16-64 bytes)
     * - Credential Public Key: COSE Key (RFC 8152 / RFC 9052)
     */
    @JvmStatic
    fun buildAttestedCredentialData(
        credentialId: ByteArray,
        publicKey: ECPublicKey,
        aaguid: ByteArray = ByteArray(16)
    ): ByteArray {
        val stream = ByteArrayOutputStream()

        // 1. AAGUID (16 bytes)
        stream.write(aaguid)

        // 2. Credential ID Length (2 bytes Big-Endian)
        val idLenBytes = ByteBuffer.allocate(2).putShort(credentialId.size.toShort()).array()
        stream.write(idLenBytes)

        // 3. Credential ID
        stream.write(credentialId)

        // 4. Encoded COSE Key
        val coseKey = encodeCosePublicKey(publicKey)
        stream.write(coseKey)

        return stream.toByteArray()
    }

    /**
     * Encodes a P-256 ECPublicKey into standard COSE Key format (CBOR map).
     *
     * COSE Key Structure (RFC 8152 / 9052):
     * Key Type (1): 2 (EC2)
     * Algorithm (3): -7 (ES256)
     * Curve (-1): 1 (P-256)
     * X-Coordinate (-2): 32-byte big-endian byte string
     * Y-Coordinate (-3): 32-byte big-endian byte string
     */
    @JvmStatic
    fun encodeCosePublicKey(publicKey: ECPublicKey): ByteArray {
        val affineX = toFixedLengthByteArray(publicKey.w.affineX.toByteArray(), 32)
        val affineY = toFixedLengthByteArray(publicKey.w.affineY.toByteArray(), 32)

        val out = ByteArrayOutputStream()
        out.write(0xA5) // CBOR map with 5 key-value pairs

        // 1 (kty) -> 2 (EC2)
        out.write(0x01)
        out.write(0x02)

        // 3 (alg) -> -7 (ES256: in CBOR negative integer -7 is 0x26)
        out.write(0x03)
        out.write(0x26)

        // -1 (crv) -> 1 (P-256)
        out.write(0x20)
        out.write(0x01)

        // -2 (x) -> 32-byte byte string (0x58 0x20)
        out.write(0x21)
        out.write(0x58)
        out.write(0x20)
        out.write(affineX)

        // -3 (y) -> 32-byte byte string (0x58 0x20)
        out.write(0x22)
        out.write(0x58)
        out.write(0x20)
        out.write(affineY)

        return out.toByteArray()
    }

    /**
     * Builds standard CBOR Attestation Object for format "none":
     * { "fmt": "none", "attStmt": {}, "authData": bytes }
     */
    @JvmStatic
    fun buildNoneAttestationObject(authData: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xA3) // CBOR map with 3 keys

        // "fmt" : "none"
        out.write(0x63)
        out.write("fmt".toByteArray(StandardCharsets.UTF_8))
        out.write(0x64)
        out.write("none".toByteArray(StandardCharsets.UTF_8))

        // "attStmt" : {} (empty map 0xA0)
        out.write(0x67)
        out.write("attStmt".toByteArray(StandardCharsets.UTF_8))
        out.write(0xA0)

        // "authData" : byte string
        out.write(0x68)
        out.write("authData".toByteArray(StandardCharsets.UTF_8))
        if (authData.size <= 255) {
            out.write(0x58)
            out.write(authData.size)
        } else {
            out.write(0x59)
            out.write((authData.size shr 8) and 0xFF)
            out.write(authData.size and 0xFF)
        }
        out.write(authData)

        return out.toByteArray()
    }

    /**
     * Builds standard WebAuthn Registration Response JSON
     */
    @JvmStatic
    fun createRegistrationResponseJson(
        credentialId: ByteArray,
        keyPair: KeyPair,
        rpId: String,
        clientDataJsonBase64: String
    ): String {
        val ecPublicKey = keyPair.public as ECPublicKey
        val attestedData = buildAttestedCredentialData(credentialId, ecPublicKey)
        val authData = buildAuthenticatorData(
            rpId = rpId,
            userPresent = true,
            userVerified = true,
            signCount = 1,
            attestedCredentialData = attestedData
        )
        val attestationObject = buildNoneAttestationObject(authData)

        val base64CredentialId = encodeBase64Url(credentialId)
        val base64Attestation = encodeBase64Url(attestationObject)
        val base64AuthData = encodeBase64Url(authData)

        return JSONObject().apply {
            put("id", base64CredentialId)
            put("rawId", base64CredentialId)
            put("type", "public-key")
            put("response", JSONObject().apply {
                put("clientDataJSON", clientDataJsonBase64)
                put("attestationObject", base64Attestation)
                put("authenticatorData", base64AuthData)
            })
            put("authenticatorAttachment", "platform")
        }.toString()
    }

    /**
     * Builds standard WebAuthn Authentication (Assertion) Response JSON
     */
    @JvmStatic
    fun createAssertionResponseJson(
        credentialId: ByteArray,
        privateKey: PrivateKey,
        rpId: String,
        clientDataHash: ByteArray,
        clientDataJsonBase64: String,
        userHandle: ByteArray? = null,
        signCount: Int = 1
    ): String {
        val authData = buildAuthenticatorData(
            rpId = rpId,
            userPresent = true,
            userVerified = true,
            signCount = signCount
        )

        val signature = signFido2Assertion(privateKey, authData, clientDataHash)

        val base64CredentialId = encodeBase64Url(credentialId)
        val base64AuthData = encodeBase64Url(authData)
        val base64Signature = encodeBase64Url(signature)

        return JSONObject().apply {
            put("id", base64CredentialId)
            put("rawId", base64CredentialId)
            put("type", "public-key")
            put("response", JSONObject().apply {
                put("clientDataJSON", clientDataJsonBase64)
                put("authenticatorData", base64AuthData)
                put("signature", base64Signature)
                if (userHandle != null && userHandle.isNotEmpty()) {
                    put("userHandle", encodeBase64Url(userHandle))
                }
            })
            put("authenticatorAttachment", "platform")
        }.toString()
    }

    /**
     * Reconstitutes an ECPrivateKey from PKCS#8 DER bytes
     */
    @JvmStatic
    fun privateKeyFromPkcs8(pkcs8Bytes: ByteArray): ECPrivateKey {
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes)) as ECPrivateKey
    }

    /**
     * Reconstitutes an ECPublicKey from X.509 DER bytes
     */
    @JvmStatic
    fun publicKeyFromX509(x509Bytes: ByteArray): ECPublicKey {
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(X509EncodedKeySpec(x509Bytes)) as ECPublicKey
    }

    /**
     * URL-safe Base64 encoding without padding or line breaks (RFC 4648 §5)
     */
    @JvmStatic
    fun encodeBase64Url(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * URL-safe Base64 decoding
     */
    @JvmStatic
    fun decodeBase64Url(data: String): ByteArray {
        return Base64.decode(data, Base64.URL_SAFE)
    }

    private fun toFixedLengthByteArray(src: ByteArray, targetLen: Int): ByteArray {
        if (src.size == targetLen) return src
        val result = ByteArray(targetLen)
        if (src.size > targetLen) {
            // Trim leading zero bytes from BigInteger representation
            System.arraycopy(src, src.size - targetLen, result, 0, targetLen)
        } else {
            // Pad with leading zeros
            System.arraycopy(src, 0, result, targetLen - src.size, src.size)
        }
        return result
    }
}
