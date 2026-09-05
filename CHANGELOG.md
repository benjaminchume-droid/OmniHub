# OmniHub Changelog

## v1.0.2 — 2026-09-05

### Highlights
- **Source-first architecture**: `AiSource` contract, SourceManager, SourceRouter, health-based routing
- **Bundled Sources**: ChatGPT, Claude, Gemini, DeepSeek, Perplexity, Groq, Kimi, Z.ai, Mistral, OpenRouter (API)
- **APICore / WebCore / MCPCore** foundations for reusable transports
- **Soul memory**: typed SoulUnits, `soul.md` projection, cross-provider continuity
- **Omni Analytics**: local event pipeline, usage/tokens/latency/sources, privacy controls, premium gate
- **Agent foundation**: Planner + AgentRuntime skeleton (plan → act → observe)
- **Workspace + PermissionEngine** foundations
- **CI**: restored official Gradle wrapper, hard-fail if APK missing, GitHub Release with APK

### Fixes
- Corrupted/truncated `gradlew` replaced with official Gradle 8.7 wrapper script
- `gradle-wrapper.jar` restored in CI
- Release artifact was ~4KB (log only) — now requires real APK ≥ 500KB or job fails
- AuthType / DescriptorSource / catalog parse alignment
- Release minify temporarily disabled until R8 rules stabilize

### Not in this release (next)
- Full WebSource protocol isolation per site
- Signed APK Source extensions (Phase 3)
- OmniHub-Sources factory + 1000+ catalog (Phases 5–6)
- OmniNode / voice / full agent tool runtime

### Install
1. Download `OmniHub-v1.0.2.apk` from this release
2. Enable install from unknown sources if needed
3. Open Sources → add API keys or web sessions
4. Chat — router picks a healthy Source

### Version
- `versionName` 1.0.2 · `versionCode` 3
