package com.crypttalk.crypto

import org.bouncycastle.asn1.gm.GMNamedCurves
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECKeyGenerationParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.SM2Signer
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.SecureRandom

object Sm2 {
    private val params = GMNamedCurves.getByName("sm2p256v1")
    val domain = ECDomainParameters(params.curve, params.g, params.n, params.h, params.seed)
    private val random = SecureRandom()

    data class KeyPair(val d: BigInteger, val q: ECPoint)

    fun generateKeyPair(): KeyPair {
        val generator = ECKeyPairGenerator()
        generator.init(ECKeyGenerationParameters(domain, random))
        val pair = generator.generateKeyPair()
        val d = (pair.private as ECPrivateKeyParameters).d
        val q = (pair.public as ECPublicKeyParameters).q.normalize()
        return KeyPair(d, q)
    }

    fun derivePublicKey(d: BigInteger): ECPoint = domain.g.multiply(d).normalize()

    fun publicKeyBytes(q: ECPoint): ByteArray = q.normalize().getEncoded(false)

    fun decodePublicKey(bytes: ByteArray): ECPoint {
        val point = try {
            domain.curve.decodePoint(bytes)
        } catch (e: Exception) {
            throw CryptoException("公钥格式不正确", e)
        }
        if (point.isInfinity || !point.isValid) {
            throw CryptoException("公钥不在 SM2 曲线上")
        }
        return point.normalize()
    }

    fun sign(privateKey: BigInteger, message: ByteArray): ByteArray {
        val signer = SM2Signer()
        signer.init(true, ECPrivateKeyParameters(privateKey, domain))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun verify(publicKey: ECPoint, message: ByteArray, signature: ByteArray): Boolean {
        return try {
            val verifier = SM2Signer()
            verifier.init(false, ECPublicKeyParameters(publicKey, domain))
            verifier.update(message, 0, message.size)
            verifier.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }

    /** ECDH：返回共享点的 x 坐标（32 字节定长） */
    fun ecdh(privateKey: BigInteger, peerPublicKey: ECPoint): ByteArray {
        val shared = peerPublicKey.multiply(privateKey).normalize()
        if (shared.isInfinity) throw CryptoException("ECDH 计算结果无效")
        return shared.affineXCoord.toBigInteger().toFixed32()
    }

    fun privateKeyBytes(d: BigInteger): ByteArray = d.toFixed32()

    fun privateKeyFromBytes(bytes: ByteArray): BigInteger {
        val d = BigInteger(1, bytes)
        if (d.signum() <= 0 || d >= domain.n) throw CryptoException("私钥数据无效")
        return d
    }

    fun toPublicKeyHex(bytes: ByteArray): String = Hex.encode(publicKeyBytes(decodePublicKey(bytes)))

    private fun BigInteger.toFixed32(): ByteArray {
        val raw = toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }
}
