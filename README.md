# OmniHub

**Universal AI Aggregator + Android Digital Assistant**

Inspired by [OmniRoute](https://github.com/diegosouzapw/OmniRoute) — the current king of self-hosted AI gateways — OmniHub brings the same multi-provider power **directly to your phone** and turns it into a real system-level digital assistant.

## Core Vision

- One app. Hundreds of models.
- Bring your own API keys **or** use free web sessions (ChatGPT Web, Claude Web, Gemini Web, Grok Web, Kimi, Z.AI, etc.).
- Intelligent routing (cost, latency, capability, quota).
- Persistent `soul.md` — a living compressed memory that rides with every request and saves massive tokens.
- Full local history (Room + SQLite).
- MCP skills system.
- Can become the phone’s **default digital assistant** (VoiceInteractionService + AssistIntent).
- Theme skins that clone ChatGPT / Claude / Perplexity / DeepSeek / Grok looks.
- Android-first, with future desktop/web companions.

## Supported Provider Types

| Type              | Examples                                      | Notes                                      |
|-------------------|-----------------------------------------------|--------------------------------------------|
| API Key           | OpenAI, Anthropic, Google, DeepSeek, xAI, Mistral, Groq, Together, Fireworks, NVIDIA NIM, Perplexity, Cohere, SiliconFlow, Kimi, Z.AI | Standard Bearer / x-api-key |
| Free / No-Auth    | Pollinations, some Cloudflare AI | Zero key required |
| Web Session       | ChatGPT Web, Claude Web, Gemini Web, Grok Web, Kimi Web, DeepSeek Web | Paste cookie / session token from browser |
| Local             | Ollama, LM Studio, local GGUF via AICore | On-device when available |

The router will keep expanding. Goal is the same as OmniRoute: support **everything we can get our hands on**.

## Architecture Highlights

```
User Prompt
    ↓
SoulCompressor (soul.md) + TokenOptimizer
    ↓
OmniRouter (score providers by cost/latency/capability/quota)
    ↓
Provider Executor (API Key / Web Session / Local)
    ↓
Response → History (Room) + Soul update
```

- **soul.md** lives in app private storage and is continuously distilled.
- Pre-request compression happens before any network call.
- Web Session providers use WebView + CookieManager (or imported cookies) — same grey-area technique OmniRoute uses.

## Project Status

This is the **official bootstrap** of OmniHub.

- Android app skeleton (Kotlin + Jetpack Compose)
- Core routing + provider abstraction
- Soul system
- Local history stubs
- Assistant service stub
- Provider registry with 20+ providers already declared

**Not production-ready yet.** Full web-session executors, polished UI skins, and complete assistant integration are next.

## Quick Start (Developer)

```bash
git clone https://github.com/benjaminchume-droid/OmniHub.git
cd OmniHub
# Open in Android Studio
# Sync Gradle
# Run on device/emulator (Android 8+)
```

## Roadmap

- [x] Repo + core architecture
- [x] Provider registry (OpenAI, Anthropic, Gemini, DeepSeek, xAI/Grok, Perplexity, Mistral, Groq, NVIDIA, Together, Fireworks, Cohere, SiliconFlow, Kimi, Z.AI, Pollinations + Web Session stubs)
- [x] OmniRouter scoring
- [x] SoulManager + soul.md
- [x] RequestPipeline
- [x] VoiceInteractionService stub for default assistant
- [ ] Full Web Session executors (cookie replay)
- [ ] Room history implementation
- [ ] Compose UI + theme skins
- [ ] MCP skill loader
- [ ] Settings for API keys + cookie import
- [ ] Local LLM fallback

## Legal / Reality Check

Web-session providers work by using your logged-in browser session. This violates most providers’ Terms of Service. Accounts can be banned. Use at your own risk. Prefer official API keys whenever possible.

## License

MIT

---

Built to be the phone’s brain.
