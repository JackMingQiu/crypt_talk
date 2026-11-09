package com.crypttalk.crypto

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.engines.SM4Engine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

object Sm4 {
    const val KEY_SIZE = 16
    const val NONCE_SIZE = 12
    const val TAG_BITS = 128

    fun gcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "SM4 密钥必须是 128 位" }
        require(nonce.size == NONCE_SIZE) { "GCM nonce 必须是 12 字节" }
        val cipher = GCMBlockCipher.newInstance(SM4Engine())
        cipher.init(true, AEADParameters(KeyParameter(key), TAG_BITS, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        var len = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        len += cipher.doFinal(out, len)
        return out.copyOf(len)
    }

    fun gcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "SM4 密钥必须是 128 位" }
        require(nonce.size == NONCE_SIZE) { "GCM nonce 必须是 12 字节" }
        val cipher = GCMBlockCipher.newInstance(SM4Engine())
        cipher.init(false, AEADParameters(KeyParameter(key), TAG_BITS, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        return try {
            var len = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
            len += cipher.doFinal(out, len)
            out.copyOf(len)
        } catch (e: InvalidCipherTextException) {
            throw CryptoException("解密失败：密钥错误或数据被篡改", e)
        }
    }
}
