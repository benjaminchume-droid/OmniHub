package com.omnihub.source.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class McpServerMeta(
    val id: String,
    val name: String,
    val endpoint: String,
    val transport: String = "http",
    val authHeader: String? = null
)

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: String = "{}"
)

/**
 * MCP transport core. Tool execution always goes through Omni policy layer — never free execution.
 */
class McpCore {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun discoverTools(meta: McpServerMeta): List<McpTool> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(meta.endpoint.trimEnd('/') + "/tools/list")
                .apply { meta.authHeader?.let { addHeader("Authorization", it) } }
                .get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext emptyList()
                if (!resp.isSuccessful) return@withContext emptyList()
                val tools = mutableListOf<McpTool>()
                val arr = JSONObject(body).optJSONArray("tools") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tools.add(
                        McpTool(
                            name = o.optString("name"),
                            description = o.optString("description"),
                            inputSchema = o.optJSONObject("inputSchema")?.toString() ?: "{}"
                        )
                    )
                }
                tools
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun callTool(
        meta: McpServerMeta,
        toolName: String,
        argumentsJson: String
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("name", toolName)
            .put("arguments", JSONObject(argumentsJson))
        val req = Request.Builder()
            .url(meta.endpoint.trimEnd('/') + "/tools/call")
            .addHeader("Content-Type", "application/json")
            .apply { meta.authHeader?.let { addHeader("Authorization", it) } }
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw Exception("MCP ${resp.code}: ${text.take(300)}")
            text
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}
