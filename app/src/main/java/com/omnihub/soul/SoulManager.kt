package com.omnihub.soul

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SoulManager(private val context: Context) {

    private val file: File get() = File(context.filesDir, "soul_units.json")
    private val legacyMd: File get() = File(context.filesDir, "soul.md")

    data class SoulUnit(
        val id: String,
        val type: String,
        val text: String,
        val tags: List<String>,
        val createdAt: Long,
        val scoreBoost: Double = 0.0
    )

    fun readAll(): List<SoulUnit> {
        migrateLegacyIfNeeded()
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val tags = mutableListOf<String>()
                    o.optJSONArray("tags")?.let { t -> for (j in 0 until t.length()) tags.add(t.getString(j)) }
                    add(SoulUnit(o.getString("id"), o.optString("type", "FACT"), o.getString("text"), tags, o.optLong("createdAt", 0L), o.optDouble("scoreBoost", 0.0)))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun writeAll(units: List<SoulUnit>) {
        val arr = JSONArray()
        units.takeLast(400).forEach { u ->
            val tags = JSONArray(); u.tags.forEach { tags.put(it) }
            arr.put(JSONObject().put("id", u.id).put("type", u.type).put("text", u.text.take(800)).put("tags", tags).put("createdAt", u.createdAt).put("scoreBoost", u.scoreBoost))
        }
        file.writeText(arr.toString())
    }

    fun append(knowledge: String, type: String = "FACT", tags: List<String> = emptyList()) {
        if (knowledge.isBlank()) return
        val units = readAll().toMutableList()
        units.add(SoulUnit(java.util.UUID.randomUUID().toString(), type, knowledge.trim(), tags, System.currentTimeMillis()))
        writeAll(units)
    }

    fun ingestExchange(user: String, assistant: String) {
        append("User: ${user.take(200)} | AI: ${assistant.take(300)}", type = "EPISODE", tags = tokenize(user).take(8))
    }

    fun retrieveForQuery(query: String, maxChars: Int = 2500, maxUnits: Int = 12): String {
        val units = readAll()
        if (units.isEmpty()) return ""
        val qTokens = tokenize(query).toSet()
        if (qTokens.isEmpty()) {
            return units.filter { it.type == "EPISODE" || it.type == "FACT" }.takeLast(5)
                .joinToString("\n") { "- [${it.type}] ${it.text}" }.take(maxChars)
        }
        val ranked = units.map { u ->
            val uTokens = tokenize(u.text + " " + u.tags.joinToString(" ")).toSet()
            val overlap = qTokens.intersect(uTokens).size.toDouble()
            val typeBoost = when (u.type) { "PREFERENCE" -> 1.4; "FACT" -> 1.2; "PROJECT" -> 1.3; else -> 1.0 }
            u to (overlap * typeBoost + u.scoreBoost)
        }.filter { it.second > 0.0 }.sortedByDescending { it.second }.map { it.first }
            .ifEmpty { units.filter { it.type == "FACT" || it.type == "PREFERENCE" }.takeLast(4) }

        val sb = StringBuilder().appendLine("## Soul memory (selective)")
        var used = 0; var count = 0
        for (u in ranked) {
            if (count >= maxUnits) break
            val line = "- [${u.type}] ${u.text}\n"
            if (used + line.length > maxChars) break
            sb.append(line); used += line.length; count++
        }
        return sb.toString().trim()
    }

    fun clear() {
        if (file.exists()) file.delete()
        if (legacyMd.exists()) legacyMd.delete()
    }

    private fun migrateLegacyIfNeeded() {
        if (file.exists() || !legacyMd.exists()) return
        val text = legacyMd.readText().trim()
        if (text.isBlank()) return
        writeAll(text.chunked(600).mapIndexed { i, c -> SoulUnit("legacy_$i", "FACT", c, emptyList(), System.currentTimeMillis()) })
    }

    private fun tokenize(s: String): List<String> =
        s.lowercase().split(Regex("[^a-z0-9_]+")).filter { it.length >= 3 }.distinct()
}
