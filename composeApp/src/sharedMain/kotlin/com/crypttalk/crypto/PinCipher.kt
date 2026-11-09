package com.crypttalk.crypto

import java.security.SecureRandom

object PinCipher {
    const val PROFILE_SK_AAD = "crypttalk/profile/sk/v1"
    const val SESSION_KEY_AAD = "crypttalk/session/key/v1"
    const val EPH_SK_AAD = "crypttalk/session/eph-sk/v1"

    private val random = SecureRandom()

    fun isValidPin(pin: String): Boolean = PIN_REGEX.matches(pin)

    /** enc_k = first 128 bits of SM3(pin || unix)，unix 以十进制字符串参与拼接 */
    fun deriveEncKey(pin: String, unix: Long): ByteArray {
        return Sm3.hash(
            pin.toByteArray(Charsets.UTF_8),
            unix.toString().toByteArray(Charsets.UTF_8)
        ).copyOf(Sm4.KEY_SIZE)
    }

    fun encrypt(encKey: ByteArray, plaintext: ByteArray, aad: String): String {
        val nonce = ByteArray(Sm4.NONCE_SIZE)
        random.nextBytes(nonce)
        val ciphertext = Sm4.gcmEncrypt(encKey, nonce, plaintext, aad.toByteArray(Charsets.UTF_8))
        return B64.encode(nonce + ciphertext)
    }

    fun decrypt(encKey: ByteArray, blob: String, aad: String): ByteArray {
        val raw = try {
            B64.decode(blob)
        } catch (e: Exception) {
            throw CryptoException("加密数据格式损坏", e)
        }
        if (raw.size < Sm4.NONCE_SIZE + Sm4.TAG_BITS / 8) throw CryptoException("加密数据不完整")
        val nonce = raw.copyOfRange(0, Sm4.NONCE_SIZE)
        val ciphertext = raw.copyOfRange(Sm4.NONCE_SIZE, raw.size)
        return Sm4.gcmDecrypt(encKey, nonce, ciphertext, aad.toByteArray(Charsets.UTF_8))
    }

    private val PIN_REGEX = Regex("^[!-~]{6,16}$")
}
