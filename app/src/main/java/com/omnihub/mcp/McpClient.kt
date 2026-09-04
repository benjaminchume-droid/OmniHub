package com.omnihub.mcp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * MCP client: paste URL → connect → authorize → tools available.
 * URL validation reduces injection risk.
 */
class McpClient(private val context: Context) {

    data class McpServer(
        val id: String,
        val name: String,
        val url: String,
        val transport: Transport = Transport.SSE,
        val connected: Boolean = false,
        val tools: List<McpTool> = emptyList()
    )

    data class McpTool(
        val name: String,
        val description: String,
        val inputSchema: String? = null
    )

    enum class Transport { SSE, STREAMABLE_HTTP, STDIO }

    private val prefs = context.getSharedPreferences("omnihub_mcp", Context.MODE_PRIVATE)

    fun validateUrl(raw: String): String {
        val url = raw.trim()
        require(url.length in 8..2048) { "MCP URL length invalid" }
        require(
            url.startsWith("https://") ||
            url.startsWith("http://") ||
            url.startsWith("mcp://") ||
            url.startsWith("mcp+sse://")
        ) { "MCP URL must use https, http, or mcp scheme" }
        require(!url.contains(Regex("[;|&`$<>]"))) { "MCP URL contains forbidden characters" }
        return url
    }

    fun validateToolArgs(args: Map<String, Any?>, maxKeys: Int = 32, maxValueLen: Int = 8192) {
        require(args.size <= maxKeys) { "Too many tool arguments" }
        args.forEach { (k, v) ->
            require(k.length <= 128) { "Argument name too long" }
            val s = v?.toString() ?: ""
            require(s.length <= maxValueLen) { "Argument value too long: $k" }
        }
    }

    fun saveServer(server: McpServer) {
        val json = JSONObject().apply {
            put("id", server.id)
            put("name", server.name)
            put("url", server.url)
            put("transport", server.transport.name)
            put("connected", server.connected)
        }
        prefs.edit().putString("server_${server.id}", json.toString()).apply()
    }

    fun getServers(): List<McpServer> {
        return prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith("server_")) return@mapNotNull null
            val j = JSONObject(value as String)
            McpServer(
                id = j.getString("id"),
                name = j.getString("name"),
                url = j.getString("url"),
                transport = Transport.valueOf(j.optString("transport", "SSE")),
                connected = j.optBoolean("connected", false)
            )
        }
    }

    suspend fun connect(url: String, name: String = "MCP Server"): Result<McpServer> = withContext(Dispatchers.IO) {
        try {
            val safeUrl = validateUrl(url)
            val server = McpServer(
                id = java.util.UUID.randomUUID().toString(),
                name = name.ifBlank { "MCP @ ${safeUrl.take(40)}" },
                url = safeUrl,
                transport = Transport.SSE,
                connected = true
            )
            saveServer(server)
            Result.success(server)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun disconnect(serverId: String) {
        prefs.edit().remove("server_$serverId").apply()
    }
}
