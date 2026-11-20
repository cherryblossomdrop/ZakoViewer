package moe.zako.viewer.data.source.cosav

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object CosCrypto {
    private const val COS_KEY = "CosAppMakeBigMoneyCosplayAPPContent"
    
    // Key used is MD5 of COS_KEY represented as 32-char hex string: 58de4cb9bf7ce1ab6fe84ca0889e8445
    // Each character is 1 byte, totaling 32 bytes (256-bit AES key)
    val KEY_HEX: String by lazy {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(COS_KEY.toByteArray(StandardCharsets.UTF_8))
        bytes.joinToString("") { "%02x".format(it) }
    }

    private val secretKeySpec: SecretKeySpec by lazy {
        // In original JS: enc.Utf8.parse(this.cosKeyMd5) -> 32-byte UTF-8 string
        val keyBytes = KEY_HEX.toByteArray(StandardCharsets.UTF_8)
        SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Decrypts AES-256-ECB PKCS5/PKCS7 padded Base64 ciphertext
     */
    fun decrypt(cipherTextBase64: String): String {
        return try {
            val decoded = Base64.decode(cipherTextBase64, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
            val decryptedBytes = cipher.doFinal(decoded)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Encrypts plaintext to AES-256-ECB PKCS5/PKCS7 Base64 ciphertext
     */
    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }
}
