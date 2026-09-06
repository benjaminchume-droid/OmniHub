package com.omnihub.history

import android.content.Context
import android.util.Base64
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * One folder per chat id under app-private storage.
 * Messages are encrypted at rest (AES-GCM) so a new chat = new folder.
 */
class ChatFolderStore(context: Context) {
    private val root = File(context.filesDir, "chats").also { it.mkdirs() }
    private val key = deriveKey(context)

    private fun dir(chatId: String): File =
        File(root, sanitize(chatId)).also { it.mkdirs() }

    private fun sanitize(id: String): String =
        id.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")

    private fun deriveKey(context: Context): SecretKeySpec {
        val seed = context.packageName + ":omni-chat-folder-v1"
        val hash = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(hash, "AES")
    }

    private fun encrypt(plain: String): String {
        val iv = MessageDigest.getInstance("SHA-256")
            .digest(plain.take(32).toByteArray())
            .copyOf(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val out = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(iv + out, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, 12)
        val data = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), StandardCharsets.UTF_8)
    }

    fun ensureChat(chatId: String, title: String) {
        val d = dir(chatId)
        val meta = File(d, "meta.json")
        if (!meta.exists()) {
            meta.writeText(
                JSONObject()
                    .put("id", chatId)
                    .put("title", title)
                    .put("createdAt", System.currentTimeMillis())
                    .toString()
            )
        }
        File(d, "messages.jsonl").let { if (!it.exists()) it.writeText("") }
    }

    fun appendMessage(chatId: String, role: String, content: String) {
        ensureChat(chatId, content.take(40).ifBlank { "Chat" })
        val line = JSONObject()
            .put("role", role)
            .put("content", content)
            .put("ts", System.currentTimeMillis())
            .toString()
        val enc = encrypt(line)
        File(dir(chatId), "messages.jsonl").appendText(enc + "\n")
    }

    fun readMessages(chatId: String): List<Pair<String, String>> {
        val f = File(dir(chatId), "messages.jsonl")
        if (!f.exists()) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        f.readLines().forEach { line ->
            if (line.isBlank()) return@forEach
            runCatching {
                val plain = decrypt(line.trim())
                val o = JSONObject(plain)
                out.add(o.optString("role") to o.optString("content"))
            }
        }
        return out
    }

    fun deleteChat(chatId: String) {
        dir(chatId).deleteRecursively()
    }

    fun listChatIds(): List<String> =
        root.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
}
