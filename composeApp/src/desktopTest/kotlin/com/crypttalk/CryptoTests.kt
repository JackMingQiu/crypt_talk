package com.crypttalk

import com.crypttalk.crypto.CryptoException
import com.crypttalk.crypto.Hex
import com.crypttalk.crypto.PinCipher
import com.crypttalk.crypto.Sm2
import com.crypttalk.crypto.Sm3
import com.crypttalk.crypto.Sm4
import com.crypttalk.protocol.Protocol
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoTests {
    @Test
    fun sm3KnownVectors() {
        assertEquals(
            "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0",
            Hex.encode(Sm3.hash("abc".toByteArray(Charsets.UTF_8)))
        )
        assertEquals(
            "debe9ff92275b8a138604889c18e5a4d6fdb70e5387e5765293dcba39c0c5732",
            Hex.encode(Sm3.hash("abcd".repeat(16).toByteArray(Charsets.UTF_8)))
        )
    }

    @Test
    fun sm4GcmRoundTripAndTamperDetection() {
        val key = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(12) { (it * 3).toByte() }
        val aad = "crypttalk-test".toByteArray(Charsets.UTF_8)
        val plaintext = "你好，SM4-GCM 加密世界！".toByteArray(Charsets.UTF_8)

        val ciphertext = Sm4.gcmEncrypt(key, nonce, plaintext, aad)
        assertFalse(ciphertext.contentEquals(plaintext))
        assertContentEquals(plaintext, Sm4.gcmDecrypt(key, nonce, ciphertext, aad))

        val tampered = ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFailsWith<CryptoException> { Sm4.gcmDecrypt(key, nonce, tampered, aad) }

        val wrongAad = "other".toByteArray(Charsets.UTF_8)
        assertFailsWith<CryptoException> { Sm4.gcmDecrypt(key, nonce, ciphertext, wrongAad) }
    }

    @Test
    fun sm2SignVerifyAndKeyRoundTrip() {
        val keyPair = Sm2.generateKeyPair()
        val message = "SM2 signature test".toByteArray(Charsets.UTF_8)
        val signature = Sm2.sign(keyPair.d, message)

        assertTrue(Sm2.verify(keyPair.q, message, signature))
        assertFalse(Sm2.verify(keyPair.q, "tampered".toByteArray(Charsets.UTF_8), signature))

        val encoded = Sm2.publicKeyBytes(keyPair.q)
        val decoded = Sm2.decodePublicKey(encoded)
        assertContentEquals(encoded, Sm2.publicKeyBytes(decoded))
    }

    @Test
    fun ecdhAndSessionKeyAgree() {
        val alice = Sm2.generateKeyPair()
        val bob = Sm2.generateKeyPair()
        val alicePk = Sm2.publicKeyBytes(alice.q)
        val bobPk = Sm2.publicKeyBytes(bob.q)

        val aliceShared = Sm2.ecdh(alice.d, bob.q)
        val bobShared = Sm2.ecdh(bob.d, alice.q)
        assertContentEquals(aliceShared, bobShared)

        val aliceKey = Protocol.deriveSessionKey(aliceShared, alicePk, bobPk)
        val bobKey = Protocol.deriveSessionKey(bobShared, bobPk, alicePk)
        assertContentEquals(aliceKey, bobKey)
        assertEquals(16, aliceKey.size)
    }

    @Test
    fun pinKeyDerivationAndEncryption() {
        val encKey = PinCipher.deriveEncKey("Aa123456!", 1_700_000_000L)
        assertEquals(16, encKey.size)
        assertContentEquals(encKey, PinCipher.deriveEncKey("Aa123456!", 1_700_000_000L))
        assertFalse(encKey.contentEquals(PinCipher.deriveEncKey("Aa123456!", 1_700_000_001L)))

        val plaintext = ByteArray(32) { (it * 7).toByte() }
        val blob = PinCipher.encrypt(encKey, plaintext, PinCipher.PROFILE_SK_AAD)
        assertContentEquals(plaintext, PinCipher.decrypt(encKey, blob, PinCipher.PROFILE_SK_AAD))

        val wrongPin = PinCipher.deriveEncKey("Aa123456?", 1_700_000_000L)
        assertFailsWith<CryptoException> {
            PinCipher.decrypt(wrongPin, blob, PinCipher.PROFILE_SK_AAD)
        }
    }

    @Test
    fun handshakeAndMessageRoundTrip() {
        val alice = Sm2.generateKeyPair()
        val bob = Sm2.generateKeyPair()
        val alicePk = Sm2.publicKeyBytes(alice.q)
        val bobPk = Sm2.publicKeyBytes(bob.q)

        val invite = Protocol.createInvite("alice", alice.d, alicePk)
        val inviteEnvelope = Protocol.decode(invite.envelope)
        val parsedInvite = Protocol.parseInvite(inviteEnvelope, Hex.encode(alicePk))

        assertFailsWith<CryptoException> {
            Protocol.parseInvite(inviteEnvelope, Hex.encode(bobPk))
        }

        val accept = Protocol.createAccept("bob", bob.d, bobPk, parsedInvite.ephemeralPk)
        val parsedAccept = Protocol.parseAccept(Protocol.decode(accept.envelope), Hex.encode(bobPk))
        val initiator = Protocol.deriveInitiatorSession(invite.ephemeralPrivateKey, parsedAccept.ephemeralPk)

        assertEquals(accept.sid, initiator.sid)
        assertContentEquals(accept.sessionKey, initiator.key)

        val code = Protocol.encryptMessage("alice", alicePk, accept.sid, 1, initiator.key, "你好，Bob")
        val decrypted = Protocol.decryptMessage(Protocol.decode(code), accept.sessionKey)
        assertEquals("你好，Bob", decrypted)

        val wrapped = code.chunked(40).joinToString("\n")
        assertEquals(code, Protocol.encode(Protocol.decode(wrapped)))

        val tampered = Protocol.decode(code).copy(ct = "AAAA")
        assertFailsWith<CryptoException> {
            Protocol.decode(code)
            Protocol.decryptMessage(tampered, accept.sessionKey)
        }
    }
}

class AppFlowTests {
    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "crypttalk-test-${System.nanoTime()}").also { it.mkdirs() }

    private fun controller(dir: File): AppController = AppController(
        AppEnvironment(
            dataDir = dir,
            copyText = {},
            pasteText = { "" }
        )
    )

    @Test
    fun fullConversationAcrossTwoInstances() {
        val alice = controller(tempDir())
        val bob = controller(tempDir())

        assertNullError(alice.setup("alice", "Aa123456!", "Aa123456!"))
        assertNullError(bob.setup("bob", "Bb123456!", "Bb123456!"))

        assertNullError(alice.addContact("bob", bob.myPkHex))
        assertNullError(bob.addContact("alice", alice.myPkHex))

        val aliceToBob = alice.contacts.single().id
        val bobToAlice = bob.contacts.single().id

        val invite = alice.startSession(aliceToBob)
        assertNotNull(invite.inviteCode, "邀请码生成失败：${invite.error}")

        val accept = bob.handlePasted(bobToAlice, invite.inviteCode!!)
        assertNotNull(accept.replyCode, "接受码生成失败：${accept.error}")

        val completed = alice.handlePasted(aliceToBob, accept.replyCode!!)
        assertEquals(null, completed.error)
        assertNotNull(alice.activeSession(aliceToBob))
        assertNotNull(bob.activeSession(bobToAlice))

        val sent = alice.sendMessage(aliceToBob, "第一条加密消息")
        assertNotNull(sent.code, "加密失败：${sent.error}")
        val received = bob.handlePasted(bobToAlice, sent.code!!)
        assertEquals(null, received.error)

        val incoming = bob.messagesFor(bobToAlice).last()
        assertEquals("第一条加密消息", bob.messageText(incoming))

        val reply = bob.sendMessage(bobToAlice, "收到！")
        assertNotNull(reply.code)
        assertEquals(null, alice.handlePasted(aliceToBob, reply.code!!).error)
        assertEquals("收到！", alice.messageText(alice.messagesFor(aliceToBob).last()))

        val duplicate = bob.handlePasted(bobToAlice, sent.code)
        assertNotNull(duplicate.error)

        alice.lock()
        assertEquals("PIN 错误", alice.unlock("wrong-pin"))
        assertEquals(null, alice.unlock("Aa123456!"))
        assertEquals("收到！", alice.messageText(alice.messagesFor(aliceToBob).last()))
    }

    private fun assertNullError(error: String?) {
        assertEquals(null, error)
    }
}
