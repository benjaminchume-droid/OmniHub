package com.omnihub.mcp

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.omnihub.data.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class McpClient(private val context: Context) {

    enum class Transport { SSE, HTTP, STDIO }

    data class McpServer(
        val id: String,
        val name: String,
        val url: String,
        val transport: Transport = Transport.SSE,
        val connected: Boolean = false,
        val lastError: String? = null,
        val tools: List<String> = emptyList()
    )

    private val prefs = context.getSharedPreferences("omnihub_mcp", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun listServers(): List<McpServer> {
        return prefs.all.keys
            .filter { it.startsWith("server_") }
            .mapNotNull { key ->
                val raw = prefs.getString(key, null) ?: return@mapNotNull null
                try {
                    val j = JSONObject(raw)
                    McpServer(
                        id = j.getString("id"),
                        name = j.getString("name"),
                        url = j.getString("url"),
                        transport = Transport.valueOf(j.optString("transport", "SSE")),
                        connected = j.optBoolean("connected", false),
                        lastError = j.optString("lastError", null).takeIf { !it.isNullOrBlank() },
                        tools = j.optJSONArray("tools")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList()
                    )
                } catch (_: Exception) { null }
            }
    }

    private fun saveServer(server: McpServer) {
        val j = JSONObject().apply {
            put("id", server.id)
            put("name", server.name)
            put("url", server.url)
            put("transport", server.transport.name)
            put("connected", server.connected)
            put("lastError", server.lastError ?: "")
            put("tools", org.json.JSONArray(server.tools))
        }
        prefs.edit().putString("server_${server.id}", j.toString()).apply()
    }

    private fun validateUrl(raw: String): String {
        val u = raw.trim()
        require(u.startsWith("http://") || u.startsWith("https://") || u.startsWith("sse://")) {
            "MCP URL must start with https:// or sse://"
        }
        require(u.length < 500) { "URL too long" }
        require(!u.contains(" ") && !u.contains("<") && !u.contains(">")) {
            "Invalid characters in URL"
        }
        return u.replace("sse://", "https://")
    }

    suspend fun connect(url: String, name: String = "MCP Server"): Result<McpServer> =
        withContext(Dispatchers.IO) {
            try {
                val safeUrl = validateUrl(url)
                val id = java.util.UUID.randomUUID().toString()
                val probe = Request.Builder().url(safeUrl).get().build()
                val tools = mutableListOf<String>()
                var connected = false
                var lastError: String? = null

                client.newCall(probe).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    when {
                        resp.isSuccessful -> {
                            connected = true
                            if (body.contains("\"tools\"") || body.contains("\"name\"")) {
                                try {
                                    val j = JSONObject(body)
                                    val arr = j.optJSONArray("tools")
                                    if (arr != null) {
                                        for (i in 0 until arr.length()) {
                                            val t = arr.optJSONObject(i)?.optString("name")
                                            if (!t.isNullOrBlank()) tools.add(t)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        resp.code in 300..399 -> {
                            connected = false
                            lastError = "Auth required (redirect ${resp.code})"
                        }
                        resp.code == 401 || resp.code == 403 -> {
                            connected = false
                            lastError = "Unauthorized – open the service and grant access"
                        }
                        else -> lastError = "HTTP ${resp.code}"
                    }
                }

                if (!connected && tools.isEmpty()) {
                    listOf("/sse", "/mcp", "/v1/sse", "/.well-known/mcp").forEach { path ->
                        try {
                            val u = safeUrl.trimEnd('/') + path
                            val r = client.newCall(Request.Builder().url(u).get().build()).execute()
                            if (r.isSuccessful) {
                                connected = true
                                r.close()
                                return@forEach
                            }
                            r.close()
                        } catch (_: Exception) {}
                    }
                }

                val server = McpServer(
                    id = id,
                    name = name.ifBlank { "MCP @ ${safeUrl.take(40)}" },
                    url = safeUrl,
                    transport = Transport.SSE,
                    connected = connected,
                    lastError = lastError,
                    tools = tools
                )
                saveServer(server)
                Result.success(server)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun openAuthorization(builtIn: BuiltInMcp) {
        val url = builtIn.docsUrl ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun disconnect(serverId: String) {
        prefs.edit().remove("server_$serverId").apply()
    }

    fun setToken(serverId: String, token: String) {
        SecureStore.putSecret(context, "mcp_token_$serverId", token)
    }

    fun getToken(serverId: String): String? =
        SecureStore.getSecret(context, "mcp_token_$serverId")

    suspend fun callTool(
        server: McpServer,
        toolName: String,
        arguments: Map<String, Any> = emptyMap()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getToken(server.id)
            val body = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/call")
                put("params", JSONObject().apply {
                    put("name", toolName)
                    put("arguments", JSONObject(arguments))
                })
            }
            val reqBuilder = Request.Builder()
                .url(server.url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("MCP ${resp.code}: $text"))
                }
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
