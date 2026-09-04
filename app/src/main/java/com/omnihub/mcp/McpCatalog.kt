package com.omnihub.mcp

data class BuiltInMcp(
    val id: String,
    val name: String,
    val description: String,
    val authType: AuthType,
    val docsUrl: String? = null
)

enum class AuthType { API_KEY, WEB_SESSION, OAUTH, URL }

object McpCatalog {
    val builtIn = listOf(
        BuiltInMcp(
            id = "github",
            name = "GitHub",
            description = "Repos, issues, PRs, code search, and more.",
            authType = AuthType.API_KEY,
            docsUrl = "https://github.com/settings/tokens"
        ),
        BuiltInMcp(
            id = "supabase",
            name = "Supabase",
            description = "Database, auth, storage, and edge functions.",
            authType = AuthType.API_KEY,
            docsUrl = "https://supabase.com/dashboard/project/_/settings/api"
        ),
        BuiltInMcp(
            id = "vercel",
            name = "Vercel",
            description = "Deployments, projects, domains, and logs.",
            authType = AuthType.API_KEY,
            docsUrl = "https://vercel.com/account/tokens"
        ),
        BuiltInMcp(
            id = "gmail",
            name = "Gmail",
            description = "Read and send email. Uses web session when no API key.",
            authType = AuthType.WEB_SESSION,
            docsUrl = "https://mail.google.com"
        ),
        BuiltInMcp(
            id = "google_maps",
            name = "Google Maps",
            description = "Places, directions, geocoding. Web session or API key.",
            authType = AuthType.WEB_SESSION,
            docsUrl = "https://maps.google.com"
        )
    )
}
