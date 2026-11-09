package com.crypttalk.crypto

import org.bouncycastle.crypto.digests.SM3Digest

class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

object Sm3 {
    fun hash(vararg parts: ByteArray): ByteArray {
        val digest = SM3Digest()
        for (part in parts) {
            digest.update(part, 0, part.size)
        }
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)
        return out
    }
}
