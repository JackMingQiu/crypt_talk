package com.crypttalk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crypttalk.crypto.CryptoException
import com.crypttalk.crypto.Hex
import com.crypttalk.crypto.PinCipher
import com.crypttalk.crypto.Sm2
import com.crypttalk.data.Contact
import com.crypttalk.data.Profile
import com.crypttalk.data.SessionRecord
import com.crypttalk.data.Store
import com.crypttalk.data.StoredMessage
import com.crypttalk.protocol.Envelope
import com.crypttalk.protocol.Protocol
import org.bouncycastle.math.ec.ECPoint
import java.io.File
import java.math.BigInteger
import java.util.UUID

class AppEnvironment(
    val dataDir: File,
    val copyText: (String) -> Unit,
    val pasteText: () -> String
)

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Chat : Screen
}

data class SessionStart(val inviteCode: String? = null, val error: String? = null)

data class SendResult(val code: String? = null, val error: String? = null)

data class PasteResult(val message: String? = null, val error: String? = null, val replyCode: String? = null)

object SessionStatus {
    const val PENDING = "pending"
    const val ACTIVE = "active"
    const val ENDED = "ended"
}

object SessionRole {
    const val INITIATOR = "initiator"
    const val RESPONDER = "responder"
}

class AppController(private val env: AppEnvironment) {
    private val store = Store(env.dataDir)

    var profile by mutableStateOf<Profile?>(null)
        private set
    var locked by mutableStateOf(true)
        private set
    var startupError by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var contacts by mutableStateOf<List<Contact>>(emptyList())
        private set
    var sessions by mutableStateOf<Map<String, List<SessionRecord>>>(emptyMap())
        private set
    var messages by mutableStateOf<Map<String, List<StoredMessage>>>(emptyMap())
        private set
    var currentContactId by mutableStateOf<String?>(null)
        private set

    private var encKey: ByteArray? = null
    private var identitySk: BigInteger? = null
    private var identityPk: ByteArray? = null
    private val sessionKeyCache = mutableMapOf<String, ByteArray>()
    private val messageTextCache = mutableMapOf<String, String>()

    init {
        val loaded = runCatching { store.loadProfile() }
        if (loaded.isFailure) {
            startupError = loaded.exceptionOrNull()?.message ?: "本地资料读取失败"
        } else {
            profile = loaded.getOrNull()
        }
    }

    val myPkHex: String
        get() = profile?.pkHex.orEmpty()

    fun setup(username: String, pin: String, confirmPin: String): String? {
        if (username.isBlank()) return "请输入用户名"
        if (username.trim().length > 32) return "用户名最多 32 个字符"
        if (!PinCipher.isValidPin(pin)) return "PIN 需为 6-16 位数字、大小写字母或标点符号（不含空格）"
        if (pin != confirmPin) return "两次输入的 PIN 不一致"

        return try {
            val unix = now()
            val keyPair = Sm2.generateKeyPair()
            val pkBytes = Sm2.publicKeyBytes(keyPair.q)
            val encK = PinCipher.deriveEncKey(pin, unix)
            val skEnc = PinCipher.encrypt(encK, Sm2.privateKeyBytes(keyPair.d), PinCipher.PROFILE_SK_AAD)
            val newProfile = Profile(
                username = username.trim(),
                pkHex = Hex.encode(pkBytes),
                skEnc = skEnc,
                unix = unix,
                createdAt = unix
            )
            store.saveProfile(newProfile)
            profile = newProfile
            encKey = encK
            identitySk = keyPair.d
            identityPk = pkBytes
            loadAll()
            locked = false
            startupError = null
            screen = Screen.Home
            notice = "初始化完成，请妥善保管 PIN"
            null
        } catch (e: Exception) {
            "初始化失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun unlock(pin: String): String? {
        val current = profile ?: return "应用尚未初始化"
        return try {
            val encK = PinCipher.deriveEncKey(pin, current.unix)
            val skBytes = PinCipher.decrypt(encK, current.skEnc, PinCipher.PROFILE_SK_AAD)
            val sk = Sm2.privateKeyFromBytes(skBytes)
            val pk = Sm2.derivePublicKey(sk)
            val pkHex = Hex.encode(Sm2.publicKeyBytes(pk))
            if (pkHex != current.pkHex) return "PIN 错误"
            encKey = encK
            identitySk = sk
            identityPk = Sm2.publicKeyBytes(pk)
            loadAll()
            locked = false
            screen = Screen.Home
            null
        } catch (e: CryptoException) {
            "PIN 错误"
        } catch (e: Exception) {
            "解锁失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun lock() {
        encKey = null
        identitySk = null
        identityPk = null
        sessionKeyCache.clear()
        messageTextCache.clear()
        locked = true
        screen = Screen.Home
        currentContactId = null
    }

    fun resetApp() {
        runCatching { store.clearAll() }
        profile = null
        locked = true
        startupError = null
        contacts = emptyList()
        sessions = emptyMap()
        messages = emptyMap()
        currentContactId = null
        encKey = null
        identitySk = null
        identityPk = null
        sessionKeyCache.clear()
        messageTextCache.clear()
        screen = Screen.Home
    }

    private fun loadAll() {
        val loadedContacts = store.loadContacts()
        contacts = loadedContacts
        sessions = store.loadSessions().filterKeys { id -> loadedContacts.any { it.id == id } }
        messages = store.loadMessages().filterKeys { id -> loadedContacts.any { it.id == id } }
        sessionKeyCache.clear()
        messageTextCache.clear()
    }

    fun contact(id: String): Contact? = contacts.firstOrNull { it.id == id }

    fun sessionsFor(contactId: String): List<SessionRecord> = sessions[contactId].orEmpty()

    fun activeSession(contactId: String): SessionRecord? =
        sessionsFor(contactId).lastOrNull { it.status == SessionStatus.ACTIVE }

    fun pendingSession(contactId: String): SessionRecord? =
        sessionsFor(contactId).lastOrNull { it.status == SessionStatus.PENDING }

    fun messagesFor(contactId: String): List<StoredMessage> = messages[contactId].orEmpty()

    fun addContact(name: String, pkHexInput: String): String? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return "请输入用户名"
        if (trimmedName.length > 32) return "用户名最多 32 个字符"
        val canonical = try {
            val bytes = Hex.decode(pkHexInput)
            Hex.encode(Sm2.publicKeyBytes(Sm2.decodePublicKey(bytes)))
        } catch (e: Exception) {
            return "身份公钥格式不正确（应为 SM2 未压缩公钥十六进制）"
        }
        if (canonical == myPkHex) return "不能添加自己为联系人"
        if (contacts.any { it.pkHex.equals(canonical, ignoreCase = true) }) return "该公钥已存在"
        val contact = Contact(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            pkHex = canonical,
            createdAt = now()
        )
        return try {
            contacts = contacts + contact
            store.saveContacts(contacts)
            null
        } catch (e: Exception) {
            contacts = contacts - contact
            "保存联系人失败：${e.message}"
        }
    }

    fun removeContact(contactId: String): String? {
        val updatedContacts = contacts.filterNot { it.id == contactId }
        val updatedSessions = sessions - contactId
        val updatedMessages = messages - contactId
        return try {
            store.saveContacts(updatedContacts)
            store.saveSessions(updatedSessions)
            store.saveMessages(updatedMessages)
            contacts = updatedContacts
            sessions = updatedSessions
            messages = updatedMessages
            if (currentContactId == contactId) {
                currentContactId = null
                screen = Screen.Home
            }
            null
        } catch (e: Exception) {
            "删除联系人失败：${e.message}"
        }
    }

    fun openChat(contactId: String) {
        currentContactId = contactId
        screen = Screen.Chat
    }

    fun goHome() {
        currentContactId = null
        screen = Screen.Home
    }

    fun openSettings() {
        screen = Screen.Settings
    }

    fun startSession(contactId: String): SessionStart {
        if (locked) return SessionStart(error = "应用已锁定")
        val sk = identitySk ?: return SessionStart(error = "应用已锁定")
        val pk = identityPk ?: return SessionStart(error = "应用已锁定")
        val username = profile?.username ?: return SessionStart(error = "应用尚未初始化")
        if (contact(contactId) == null) return SessionStart(error = "联系人不存在")
        return try {
            val invite = Protocol.createInvite(username, sk, pk)
            val record = SessionRecord(
                sid = "",
                contactId = contactId,
                role = SessionRole.INITIATOR,
                peerEphPkHex = "",
                keyEnc = "",
                status = SessionStatus.PENDING,
                ephSkEnc = PinCipher.encrypt(
                    requireNotNull(encKey),
                    Sm2.privateKeyBytes(invite.ephemeralPrivateKey),
                    PinCipher.EPH_SK_AAD
                ),
                outSeq = 0,
                seenSeqs = emptyList(),
                createdAt = now(),
                lastCode = invite.envelope
            )
            val updated = sessionsFor(contactId)
                .map {
                    if (it.status == SessionStatus.ACTIVE || it.status == SessionStatus.PENDING) {
                        it.copy(status = SessionStatus.ENDED)
                    } else {
                        it
                    }
                } + record
            updateSessions(contactId, updated)
            appendMessage(
                systemMessage(contactId, "已发起新会话，请把邀请码发送给对方，并粘贴对方返回的接受码")
            )
            SessionStart(inviteCode = invite.envelope)
        } catch (e: Exception) {
            SessionStart(error = e.message ?: "创建会话失败")
        }
    }

    fun handlePasted(contactId: String, text: String): PasteResult {
        if (locked) return PasteResult(error = "应用已锁定")
        return try {
            val envelope = Protocol.decode(text)
            when (envelope.type) {
                Protocol.TYPE_INVITE -> acceptInvite(contactId, envelope)
                Protocol.TYPE_ACCEPT -> completeSession(contactId, envelope)
                Protocol.TYPE_MESSAGE -> decryptIncoming(contactId, envelope)
                else -> PasteResult(error = "不支持的密文类型")
            }
        } catch (e: Exception) {
            PasteResult(error = e.message ?: "处理失败")
        }
    }

    private fun acceptInvite(contactId: String, envelope: Envelope): PasteResult {
        val contact = contact(contactId) ?: return PasteResult(error = "联系人不存在")
        val sk = identitySk ?: return PasteResult(error = "应用已锁定")
        val pk = identityPk ?: return PasteResult(error = "应用已锁定")
        val username = profile?.username ?: return PasteResult(error = "应用尚未初始化")
        val encK = encKey ?: return PasteResult(error = "应用已锁定")

        val parsed = try {
            Protocol.parseInvite(envelope, contact.pkHex)
        } catch (e: Exception) {
            return PasteResult(error = e.message ?: "邀请码验证失败")
        }
        if (parsed.from.isNotBlank() && parsed.from != contact.name) {
            return PasteResult(error = "邀请码中的用户名与联系人「${contact.name}」不一致")
        }

        return try {
            val accept = Protocol.createAccept(username, sk, pk, parsed.ephemeralPk)
            val record = SessionRecord(
                sid = accept.sid,
                contactId = contactId,
                role = SessionRole.RESPONDER,
                peerEphPkHex = Hex.encode(parsed.ephemeralPk),
                keyEnc = PinCipher.encrypt(encK, accept.sessionKey, PinCipher.SESSION_KEY_AAD),
                status = SessionStatus.ACTIVE,
                ephSkEnc = "",
                outSeq = 0,
                seenSeqs = emptyList(),
                createdAt = now(),
                lastCode = accept.envelope
            )
            sessionKeyCache[accept.sid] = accept.sessionKey
            val updated = sessionsFor(contactId).map {
                if (it.status == SessionStatus.ACTIVE || it.status == SessionStatus.PENDING) {
                    it.copy(status = SessionStatus.ENDED)
                } else {
                    it
                }
            } + record
            updateSessions(contactId, updated)
            appendMessage(systemMessage(contactId, "已接受会话邀请，会话密钥协商完成"))
            PasteResult(message = "会话已建立", replyCode = accept.envelope)
        } catch (e: Exception) {
            PasteResult(error = e.message ?: "接受邀请失败")
        }
    }

    private fun completeSession(contactId: String, envelope: Envelope): PasteResult {
        val contact = contact(contactId) ?: return PasteResult(error = "联系人不存在")
        val pending = pendingSession(contactId) ?: return PasteResult(error = "当前没有等待接受的会话")
        val encK = encKey ?: return PasteResult(error = "应用已锁定")

        val parsed = try {
            Protocol.parseAccept(envelope, contact.pkHex)
        } catch (e: Exception) {
            return PasteResult(error = e.message ?: "接受码验证失败")
        }

        return try {
            val ephSk = Sm2.privateKeyFromBytes(
                PinCipher.decrypt(encK, pending.ephSkEnc, PinCipher.EPH_SK_AAD)
            )
            val derived = Protocol.deriveInitiatorSession(ephSk, parsed.ephemeralPk)
            if (derived.sid != parsed.sid) {
                return PasteResult(error = "接受码与当前会话不匹配")
            }
            val keyEnc = PinCipher.encrypt(encK, derived.key, PinCipher.SESSION_KEY_AAD)
            val updated = sessionsFor(contactId).map { record ->
                when {
                    record == pending -> record.copy(
                        sid = derived.sid,
                        peerEphPkHex = Hex.encode(parsed.ephemeralPk),
                        keyEnc = keyEnc,
                        status = SessionStatus.ACTIVE,
                        ephSkEnc = ""
                    )
                    record.status == SessionStatus.ACTIVE -> record.copy(status = SessionStatus.ENDED)
                    else -> record
                }
            }
            sessionKeyCache[derived.sid] = derived.key
            updateSessions(contactId, updated)
            appendMessage(systemMessage(contactId, "对方已接受邀请，会话密钥协商完成"))
            PasteResult(message = "会话已建立")
        } catch (e: Exception) {
            PasteResult(error = e.message ?: "完成会话失败")
        }
    }

    private fun decryptIncoming(contactId: String, envelope: Envelope): PasteResult {
        val contact = contact(contactId) ?: return PasteResult(error = "联系人不存在")
        val senderPk = envelope.pk ?: return PasteResult(error = "消息缺少发送者公钥")
        if (!senderPk.equals(contact.pkHex, ignoreCase = true)) {
            return PasteResult(error = "消息发送者与当前联系人不一致")
        }
        val sid = envelope.sid?.takeIf { it.isNotEmpty() }
            ?: return PasteResult(error = "消息缺少会话标识")
        val record = sessionsFor(contactId).lastOrNull { it.sid == sid }
            ?: return PasteResult(error = "找不到对应的会话，可能已开启新会话")
        val key = sessionKey(record) ?: return PasteResult(error = "会话密钥不可用，请重新解锁")
        val seq = envelope.seq ?: return PasteResult(error = "消息缺少序号")
        if (record.seenSeqs.contains(seq)) {
            return PasteResult(error = "这条消息已经解密过了")
        }

        return try {
            val plaintext = Protocol.decryptMessage(envelope, key)
            val message = StoredMessage(
                id = UUID.randomUUID().toString(),
                contactId = contactId,
                sid = sid,
                direction = "incoming",
                kind = "text",
                envelope = Protocol.encode(envelope),
                text = "",
                seq = seq,
                ts = now()
            )
            messageTextCache[message.id] = plaintext
            val updatedRecord = record.copy(
                seenSeqs = (record.seenSeqs + seq).takeLast(500)
            )
            updateSessions(contactId, sessionsFor(contactId).map { if (it == record) updatedRecord else it })
            appendMessage(message)
            PasteResult(message = "已解密一条消息")
        } catch (e: Exception) {
            PasteResult(error = e.message ?: "解密失败")
        }
    }

    fun sendMessage(contactId: String, text: String): SendResult {
        if (locked) return SendResult(error = "应用已锁定")
        if (text.isBlank()) return SendResult(error = "消息内容不能为空")
        val contact = contact(contactId) ?: return SendResult(error = "联系人不存在")
        val active = activeSession(contactId) ?: return SendResult(error = "请先建立会话")
        val key = sessionKey(active) ?: return SendResult(error = "会话密钥不可用，请重新解锁")
        val username = profile?.username ?: return SendResult(error = "应用尚未初始化")
        val pk = identityPk ?: return SendResult(error = "应用已锁定")
        val seq = active.outSeq + 1

        return try {
            val code = Protocol.encryptMessage(username, pk, active.sid, seq, key, text)
            val message = StoredMessage(
                id = UUID.randomUUID().toString(),
                contactId = contactId,
                sid = active.sid,
                direction = "outgoing",
                kind = "text",
                envelope = code,
                text = "",
                seq = seq,
                ts = now()
            )
            messageTextCache[message.id] = text
            updateSessions(
                contactId,
                sessionsFor(contactId).map { if (it == active) it.copy(outSeq = seq) else it }
            )
            appendMessage(message)
            env.copyText(code)
            notice = "密文已复制到剪贴板"
            SendResult(code = code)
        } catch (e: Exception) {
            SendResult(error = e.message ?: "加密失败")
        }
    }

    fun messageText(message: StoredMessage): String {
        if (message.kind == "system") return message.text
        messageTextCache[message.id]?.let { return it }
        if (message.envelope.isEmpty()) return message.text
        val encK = encKey
        val record = sessionsFor(message.contactId).lastOrNull { it.sid == message.sid }
        val key = if (encK != null && record != null) sessionKey(record) else null
        if (key == null) return "［无法解密：会话密钥不可用］"
        return try {
            val envelope = Protocol.decode(message.envelope)
            val plaintext = Protocol.decryptMessage(envelope, key)
            messageTextCache[message.id] = plaintext
            plaintext
        } catch (e: Exception) {
            "［无法解密：${e.message ?: "数据损坏"}］"
        }
    }

    fun myPublicKey(): String = myPkHex

    fun copy(text: String) {
        env.copyText(text)
        notice = "已复制到剪贴板"
    }

    fun paste(): String = env.pasteText()

    fun showNotice(message: String) {
        notice = message
    }

    fun clearNotice() {
        notice = null
    }

    private fun sessionKey(record: SessionRecord): ByteArray? {
        if (record.sid.isNotEmpty()) {
            sessionKeyCache[record.sid]?.let { return it }
        }
        val encK = encKey ?: return null
        if (record.keyEnc.isEmpty()) return null
        return runCatching {
            PinCipher.decrypt(encK, record.keyEnc, PinCipher.SESSION_KEY_AAD)
        }.getOrNull()?.also {
            if (record.sid.isNotEmpty()) sessionKeyCache[record.sid] = it
        }
    }

    private fun updateSessions(contactId: String, list: List<SessionRecord>) {
        val updated = sessions + (contactId to list)
        store.saveSessions(updated)
        sessions = updated
    }

    private fun appendMessage(message: StoredMessage) {
        val updated = messages + (message.contactId to (messagesFor(message.contactId) + message))
        store.saveMessages(updated)
        messages = updated
    }

    private fun systemMessage(contactId: String, text: String): StoredMessage = StoredMessage(
        id = UUID.randomUUID().toString(),
        contactId = contactId,
        sid = "",
        direction = "system",
        kind = "system",
        envelope = "",
        text = text,
        seq = 0,
        ts = now()
    )

    private fun now(): Long = System.currentTimeMillis() / 1000
}
