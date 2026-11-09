package com.crypttalk.crypto

import java.util.Base64

object Hex {
    private val DIGITS = "0123456789abcdef".toCharArray()

    fun encode(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = DIGITS[v ushr 4]
            out[i * 2 + 1] = DIGITS[v and 0x0F]
        }
        return String(out)
    }

    fun decode(text: String): ByteArray {
        val cleaned = text.filterNot { it.isWhitespace() }
        require(cleaned.length % 2 == 0) { "十六进制长度不正确" }
        val out = ByteArray(cleaned.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(cleaned[i * 2], 16)
            val lo = Character.digit(cleaned[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "包含非十六进制字符" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}

object B64 {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    fun decode(text: String): ByteArray = decoder.decode(text.filterNot { it.isWhitespace() })
}
