package dev.forgeotalab.domain

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ed25519 signature verifier for the adapter manifest.
 *
 * WHY Ed25519: PRD specifies Ed25519 for manifest signature validation.
 * Ed25519 provides 128-bit security with small keys (32 bytes) and fast
 * verification — critical for mobile startup latency.
 *
 * WHY pinned in binary: The PRD requires "Ed25519 verification using embedded
 * public key (pinned in binary, not fetched)." The public key is a compile-time
 * constant — no runtime key exchange, no certificate chain, no MITM surface.
 *
 * PRODUCTION REPLACEMENT: Replace DEV_PUBLIC_KEY_HEX with the production
 * Ed25519 public key before release builds. The key pair is generated once
 * and the private key lives in the manifest signing service infrastructure.
 */
@Singleton
class ManifestSignatureVerifier @Inject constructor() {

    companion object {
        /**
         * Placeholder Ed25519 public key for development.
         *
         * TODO(P01): Replace with production key before closed beta.
         * Generate production keypair:
         *   openssl genpkey -algorithm ed25519 -out manifest_private.pem
         *   openssl pkey -in manifest_private.pem -pubout -outform DER | xxd -p -c 0 | tail -c 64
         *
         * The last 32 bytes of the DER-encoded public key are the raw Ed25519 public key.
         */
        private const val DEV_PUBLIC_KEY_HEX =
            "d75a980182b10ab7d54bfed3c964073a0ee172f3daa3f4a18446b0b8d186b8c7"

        /**
         * Decodes a hex string to a byte array.
         */
        private fun hexToBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Hex string must have even length" }
            return ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
    }

    private val publicKeyBytes: ByteArray = hexToBytes(DEV_PUBLIC_KEY_HEX)

    /**
     * Verify an Ed25519 signature against the manifest body.
     *
     * WHY verification BEFORE parsing: The PRD's Signed Manifest Contract
     * states "Signature verification happens BEFORE any manifest field is
     * parsed." This prevents any crafted manifest fields from being processed
     * if the envelope is tampered with.
     *
     * @param manifestBody Raw bytes of the manifest body JSON.
     * @param signatureBase64 Base64-encoded Ed25519 signature.
     * @return true if signature is valid.
     */
    fun verify(manifestBody: ByteArray, signatureBase64: String): Boolean {
        return try {
            val signatureBytes = Base64.getDecoder().decode(signatureBase64)
            if (signatureBytes.size != 64) {
                // Ed25519 signatures are always exactly 64 bytes
                return false
            }

            val publicKey = Ed25519PublicKeyParameters(publicKeyBytes, 0)
            val signer = Ed25519Signer()
            signer.init(false, publicKey)
            signer.update(manifestBody, 0, manifestBody.size)
            signer.verifySignature(signatureBytes)
        } catch (_: Exception) {
            // Any crypto error — malformed key, bad encoding, etc. — is a verification failure
            false
        }
    }
}
