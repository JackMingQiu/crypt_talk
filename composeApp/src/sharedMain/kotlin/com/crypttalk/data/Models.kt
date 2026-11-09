package com.crypttalk.data

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val version: Int = 1,
    val username: String,
    val pkHex: String,
    val skEnc: String,
    val unix: Long,
    val createdAt: Long
)

@Serializable
data class Contact(
    val id: String,
    val name: String,
    val pkHex: String,
    val createdAt: Long
)

@Serializable
data class SessionRecord(
    val sid: String,
    val contactId: String,
    val role: String,
    val peerEphPkHex: String,
    val keyEnc: String,
    val status: String,
    val ephSkEnc: String,
    val outSeq: Long,
    val seenSeqs: List<Long>,
    val createdAt: Long,
    val lastCode: String = ""
)

@Serializable
data class StoredMessage(
    val id: String,
    val contactId: String,
    val sid: String,
    val direction: String,
    val kind: String,
    val envelope: String,
    val text: String,
    val seq: Long,
    val ts: Long
)

@Serializable
data class ContactsFile(
    val version: Int = 1,
    val contacts: List<Contact> = emptyList()
)

@Serializable
data class SessionsFile(
    val version: Int = 1,
    val sessions: Map<String, List<SessionRecord>> = emptyMap()
)

@Serializable
data class MessagesFile(
    val version: Int = 1,
    val messages: Map<String, List<StoredMessage>> = emptyMap()
)
