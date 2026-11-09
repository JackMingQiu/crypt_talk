package com.crypttalk.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class Store(root: File) {
    private val root = root.also { it.mkdirs() }
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val profileFile = File(this.root, "profile.json")
    private val contactsFile = File(this.root, "contacts.json")
    private val sessionsFile = File(this.root, "sessions.json")
    private val messagesFile = File(this.root, "messages.json")

    fun loadProfile(): Profile? = read(profileFile, Profile.serializer())

    fun saveProfile(profile: Profile) = write(profileFile, json.encodeToString(Profile.serializer(), profile))

    fun loadContacts(): List<Contact> =
        read(contactsFile, ContactsFile.serializer())?.contacts.orEmpty()

    fun saveContacts(contacts: List<Contact>) =
        write(contactsFile, json.encodeToString(ContactsFile.serializer(), ContactsFile(contacts = contacts)))

    fun loadSessions(): Map<String, List<SessionRecord>> =
        read(sessionsFile, SessionsFile.serializer())?.sessions.orEmpty()

    fun saveSessions(sessions: Map<String, List<SessionRecord>>) =
        write(sessionsFile, json.encodeToString(SessionsFile.serializer(), SessionsFile(sessions = sessions)))

    fun loadMessages(): Map<String, List<StoredMessage>> =
        read(messagesFile, MessagesFile.serializer())?.messages.orEmpty()

    fun saveMessages(messages: Map<String, List<StoredMessage>>) =
        write(messagesFile, json.encodeToString(MessagesFile.serializer(), MessagesFile(messages = messages)))

    fun clearAll() {
        listOf(profileFile, contactsFile, sessionsFile, messagesFile).forEach { file ->
            runCatching { Files.deleteIfExists(file.toPath()) }
        }
    }

    private fun <T> read(file: File, serializer: KSerializer<T>): T? {
        if (!file.exists()) return null
        return json.decodeFromString(serializer, file.readText(Charsets.UTF_8))
    }

    private fun write(file: File, text: String) {
        root.mkdirs()
        val temp = File(root, file.name + ".tmp")
        temp.writeText(text, Charsets.UTF_8)
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (e: Exception) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
