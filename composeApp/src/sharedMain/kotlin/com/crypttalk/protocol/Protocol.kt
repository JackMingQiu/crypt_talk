package com.crypttalk.protocol

import com.crypttalk.crypto.B64
import com.crypttalk.crypto.CryptoException
import com.crypttalk.crypto.Hex
import com.crypttalk.crypto.Sm2
import com.crypttalk.crypto.Sm3
import com.crypttalk.crypto.Sm4
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.security.SecureRandom

object Protocol {
    const val PREFIX = "CTK1:"
    const val VERSION = 1

    const val TYPE_INVITE = "invite"
    const val TYPE_ACCEPT = "accept"
    const val TYPE_MESSAGE = "msg"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }
    private val random = SecureRandom()

    fun encode(envelope: Envelope): String {
        val payload = json.encodeToString(Envelope.serializer(), envelope).toByteArray(Charsets.UTF_8)
        return PREFIX + B64.encode(payload)
    }

    fun decode(text: String): Envelope {
        val compact = text.filterNot { it.isWhitespace() }
        val index = compact.indexOf(PREFIX)
        if (index < 0) throw CryptoException("不是 CryptTalk 密文（应以 CTK1: 开头）")
        val payload = compact.substring(index + PREFIX.length)
            .takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
        if (payload.isEmpty()) throw CryptoException("密文内容为空")
        val envelope = try {
            json.decodeFromString(Envelope.serializer(), String(B64.decode(payload), Charsets.UTF_8))
        } catch (e: Exception) {
            throw CryptoException("密文格式无法解析", e)
        }
        if (envelope.v != VERSION) throw CryptoException("不支持的协议版本：${envelope.v}")
        return envelope
    }

    fun fingerprint(pkBytes: ByteArray): String =
        Hex.encode(Sm3.hash(pkBytes).copyOf(8)).chunked(4).joinToString(" ")

    fun sessionId(initiatorEphPk: ByteArray, responderEphPk: ByteArray): String =
        Hex.encode(Sm3.hash(initiatorEphPk, responderEphPk).copyOf(8))

    /** 会话密钥 = first 128 bits of SM3(ECDH共享x || 按字典序排列的两个临时公钥) */
    fun deriveSessionKey(sharedX: ByteArray, ephA: ByteArray, ephB: ByteArray): ByteArray {
        val aFirst = compareUnsigned(ephA, ephB) <= 0
        val first = if (aFirst) ephA else ephB
        val second = if (aFirst) ephB else ephA
        return Sm3.hash(sharedX, first, second).copyOf(Sm4.KEY_SIZE)
    }

    data class InviteCreated(val envelope: String, val ephemeralPrivateKey: BigInteger, val ephemeralPublicKey: ByteArray)

    fun createInvite(name: String, identitySk: BigInteger, identityPk: ByteArray): InviteCreated {
        val ephemeral = Sm2.generateKeyPair()
        val ephemeralPk = Sm2.publicKeyBytes(ephemeral.q)
        val signature = Sm2.sign(identitySk, ephemeralPk)
        val envelope = Envelope(
            type = TYPE_INVITE,
            from = name,
            pk = Hex.encode(identityPk),
            eph = Hex.encode(ephemeralPk),
            sig = B64.encode(signature)
        )
        return InviteCreated(encode(envelope), ephemeral.d, ephemeralPk)
    }

    data class ParsedInvite(val from: String, val identityPk: ByteArray, val ephemeralPk: ByteArray)

    fun parseInvite(envelope: Envelope, expectedContactPkHex: String): ParsedInvite {
        if (envelope.type != TYPE_INVITE) throw CryptoException("这不是会话邀请码")
        val pk = requiredField(envelope.pk, "公钥")
        val eph = requiredField(envelope.eph, "临时公钥")
        val sig = requiredField(envelope.sig, "签名")
        val pkBytes = try {
            Hex.decode(pk)
        } catch (e: Exception) {
            throw CryptoException("邀请码公钥格式错误", e)
        }
        val ephBytes = try {
            Hex.decode(eph)
        } catch (e: Exception) {
            throw CryptoException("邀请码临时公钥格式错误", e)
        }
        val identityPoint = Sm2.decodePublicKey(pkBytes)
        if (!Sm2.publicKeyBytes(identityPoint).contentEquals(expectedBytes(expectedContactPkHex))) {
            throw CryptoException("邀请码不属于当前联系人")
        }
        val signature = try {
            B64.decode(sig)
        } catch (e: Exception) {
            throw CryptoException("邀请码签名格式错误", e)
        }
        Sm2.decodePublicKey(ephBytes)
        if (!Sm2.verify(identityPoint, ephBytes, signature)) {
            throw CryptoException("邀请码签名验证失败")
        }
        return ParsedInvite(envelope.from.orEmpty(), pkBytes, ephBytes)
    }

    data class AcceptCreated(
        val envelope: String,
        val sid: String,
        val sessionKey: ByteArray,
        val ephemeralPublicKey: ByteArray
    )

    fun createAccept(
        name: String,
        identitySk: BigInteger,
        identityPk: ByteArray,
        initiatorEphemeralPk: ByteArray
    ): AcceptCreated {
        val ephemeral = Sm2.generateKeyPair()
        val ephemeralPk = Sm2.publicKeyBytes(ephemeral.q)
        val signature = Sm2.sign(identitySk, ephemeralPk)
        val sid = sessionId(initiatorEphemeralPk, ephemeralPk)
        val sharedX = Sm2.ecdh(ephemeral.d, Sm2.decodePublicKey(initiatorEphemeralPk))
        val key = deriveSessionKey(sharedX, initiatorEphemeralPk, ephemeralPk)
        val envelope = Envelope(
            type = TYPE_ACCEPT,
            from = name,
            pk = Hex.encode(identityPk),
            eph = Hex.encode(ephemeralPk),
            sig = B64.encode(signature),
            sid = sid
        )
        return AcceptCreated(encode(envelope), sid, key, ephemeralPk)
    }

    data class ParsedAccept(val from: String, val identityPk: ByteArray, val ephemeralPk: ByteArray, val sid: String)

    fun parseAccept(envelope: Envelope, expectedContactPkHex: String): ParsedAccept {
        if (envelope.type != TYPE_ACCEPT) throw CryptoException("这不是会话接受码")
        val pk = requiredField(envelope.pk, "公钥")
        val eph = requiredField(envelope.eph, "临时公钥")
        val sig = requiredField(envelope.sig, "签名")
        val sid = requiredField(envelope.sid, "会话标识")
        val pkBytes = Hex.decode(pk)
        val ephBytes = Hex.decode(eph)
        val signature = B64.decode(sig)
        val identityPoint = Sm2.decodePublicKey(pkBytes)
        if (!Sm2.publicKeyBytes(identityPoint).contentEquals(expectedBytes(expectedContactPkHex))) {
            throw CryptoException("接受码不属于当前联系人")
        }
        Sm2.decodePublicKey(ephBytes)
        if (!Sm2.verify(identityPoint, ephBytes, signature)) {
            throw CryptoException("接受码签名验证失败")
        }
        return ParsedAccept(envelope.from.orEmpty(), pkBytes, ephBytes, sid)
    }

    data class InitiatorSession(val sid: String, val key: ByteArray)

    fun deriveInitiatorSession(ownEphemeralSk: BigInteger, responderEphemeralPk: ByteArray): InitiatorSession {
        val ownPk = Sm2.publicKeyBytes(Sm2.derivePublicKey(ownEphemeralSk))
        val sid = sessionId(ownPk, responderEphemeralPk)
        val sharedX = Sm2.ecdh(ownEphemeralSk, Sm2.decodePublicKey(responderEphemeralPk))
        val key = deriveSessionKey(sharedX, ownPk, responderEphemeralPk)
        return InitiatorSession(sid, key)
    }

    fun encryptMessage(
        name: String,
        identityPk: ByteArray,
        sid: String,
        seq: Long,
        sessionKey: ByteArray,
        plaintext: String
    ): String {
        val nonce = ByteArray(Sm4.NONCE_SIZE)
        random.nextBytes(nonce)
        val payload = json.encodeToString(
            MessagePayload.serializer(),
            MessagePayload(from = name, pk = Hex.encode(identityPk), text = plaintext)
        )
        val ciphertext = Sm4.gcmEncrypt(
            sessionKey,
            nonce,
            payload.toByteArray(Charsets.UTF_8),
            messageAad(sid, seq)
        )
        val envelope = Envelope(
            type = TYPE_MESSAGE,
            sid = sid,
            seq = seq,
            nonce = B64.encode(nonce),
            ct = B64.encode(ciphertext)
        )
        return encode(envelope)
    }

    fun decryptMessage(envelope: Envelope, sessionKey: ByteArray): MessagePayload {
        if (envelope.type != TYPE_MESSAGE) throw CryptoException("这不是聊天消息")
        val sid = requiredField(envelope.sid, "会话标识")
        val nonce = B64.decode(requiredField(envelope.nonce, "随机数"))
        val ciphertext = B64.decode(requiredField(envelope.ct, "密文"))
        val seq = envelope.seq ?: throw CryptoException("消息缺少序号")

        // 兼容旧版信封：from/pk 在信封中、AAD 含发送者信息、明文为纯文本
        if (envelope.from != null || envelope.pk != null) {
            val from = requiredField(envelope.from, "发送者")
            val pk = requiredField(envelope.pk, "公钥")
            val plaintext = Sm4.gcmDecrypt(sessionKey, nonce, ciphertext, legacyMessageAad(sid, from, pk, seq))
            return MessagePayload(from = from, pk = pk, text = String(plaintext, Charsets.UTF_8))
        }

        val plaintext = Sm4.gcmDecrypt(sessionKey, nonce, ciphertext, messageAad(sid, seq))
        return try {
            json.decodeFromString(MessagePayload.serializer(), String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            throw CryptoException("消息明文格式不正确", e)
        }
    }

    private fun messageAad(sid: String, seq: Long): ByteArray =
        "CTK1|$TYPE_MESSAGE|$sid|$seq".toByteArray(Charsets.UTF_8)

    private fun legacyMessageAad(sid: String, from: String, pkHex: String, seq: Long): ByteArray =
        "CTK1|$TYPE_MESSAGE|$sid|$from|$pkHex|$seq".toByteArray(Charsets.UTF_8)

    private fun requiredField(value: String?, name: String): String =
        value?.takeIf { it.isNotEmpty() } ?: throw CryptoException("密文缺少$name")

    private fun expectedBytes(pkHex: String): ByteArray = Sm2.publicKeyBytes(Sm2.decodePublicKey(Hex.decode(pkHex)))

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        val size = minOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a[i].toInt() and 0xFF
            val y = b[i].toInt() and 0xFF
            if (x != y) return x - y
        }
        return a.size - b.size
    }
}
