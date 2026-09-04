# OmniHub v1.0

**Universal AI Aggregator + Android Digital Assistant**

Inspired by [OmniRoute](https://github.com/diegosouzapw/OmniRoute). OmniHub brings multi-provider power **directly to your phone** and can become the system digital assistant.

## What's in v1.0

### UI (Grok / Perplexity / Claude style)
- **Swipe left** (or hamburger) → History drawer
- Top bar: **Temporary chat** icon + **New chat** button + Customize + Settings
- **Customize** screen with tabs:
  - Skills
  - Behavior (custom instructions)
  - Tone
  - **MCP Server** (paste URL → connect → done)
- Bottom of history drawer: Account + Connected AI Services + Add more

### Core
- Multi-provider routing (OpenAI, Anthropic, Gemini, DeepSeek, xAI/Grok, Perplexity, Kimi, Z.AI, NVIDIA, Groq, Mistral, etc.)
- Web Session provider stubs (ChatGPT Web, Claude Web, Gemini Web, Grok Web…)
- `soul.md` persistent compressed memory
- MCP client: paste server URL, connect, tools become available
- VoiceInteractionService for default digital assistant
- GitHub Actions workflow that builds and publishes APK releases

## MCP Server (Claude / Grok style)

1. Open **Customize → MCP Server**
2. Paste the MCP URL (SSE or Streamable HTTP)
3. Tap **Connect MCP Server**
4. Complete any login the server requires
5. Done — its tools are now available

## Build & Release (GitHub Actions)

The workflow `.github/workflows/build-release.yml` builds a signed APK and publishes it as a GitHub Release.

### Required Secrets (Repo → Settings → Secrets and variables → Actions)

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | Base64 of the release keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

A release keystore was generated for this project:

```
Alias: omnihub
Store password: omnihub2026
Key password: omnihub2026
```

To add the secret yourself:

1. Download / copy the base64 of `omnihub-release.jks` (or generate your own)
2. Go to the repo **Settings → Secrets and variables → Actions → New repository secret**
3. Name: `KEYSTORE_BASE64` → paste the base64
4. Create the other three secrets with the passwords above (or your own)

Then run the workflow manually (Actions → Build & Release APK → Run workflow) or push a tag `v1.0.0`.

## Quick Start

```bash
git clone https://github.com/benjaminchume-droid/OmniHub.git
cd OmniHub
# Open in Android Studio → Sync → Run
```

## Structure

```
app/src/main/java/com/omnihub/
├── ui/           # ChatScreen, CustomizeScreen, SettingsScreen, Theme
├── mcp/          # McpClient (paste URL → connect)
├── core/         # OmniRouter, RequestPipeline
├── soul/         # SoulManager + soul.md
├── providers/    # All AI providers
└── assistant/    # VoiceInteractionService
```

## Legal Note

Web-session providers use your browser cookies. This can violate provider ToS. Prefer official API keys.

## License

MIT
