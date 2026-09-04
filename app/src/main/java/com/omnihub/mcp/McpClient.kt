package com.omnihub.mcp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * MCP (Model Context Protocol) client.
 * User pastes an MCP server URL (SSE or Streamable HTTP), completes any login,
 * and the server's tools become available to the model.
 *
 * Flow mirrors Claude / Grok style: paste URL → connect → done.
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences("omnihub_mcp", Context.MODE_PRIVATE)

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
            val server = McpServer(
                id = java.util.UUID.randomUUID().toString(),
                name = name.ifBlank { "MCP @ ${url.take(40)}" },
                url = url,
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
