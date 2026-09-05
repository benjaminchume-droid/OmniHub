# OmniHub

**Universal AI Aggregator + Android Digital Assistant**

OmniHub is a powerful Android application that brings multiple AI providers directly to your phone, unified under a single, intelligent interface. Act as your system digital assistant, or use it as your personal AI hub for seamless multi-provider access.

## What is OmniHub?

OmniHub solves the fragmentation of AI services. Instead of switching between ChatGPT, Claude, Gemini, and dozens of other AI platforms, OmniHub intelligently routes your queries to the best available provider based on your needs—cost, speed, capability, or preference.

### Core Features

- **Multi-Provider Routing**: Access OpenAI, Anthropic, Google Gemini, DeepSeek, xAI/Grok, Perplexity, Mistral, NVIDIA, Groq, and more from a single interface
- **Intelligent Fallback**: If your preferred provider fails, OmniHub automatically tries the next best candidate
- **Soul Memory System**: Persistent, compressed memory (soul.md) that remembers your preferences, habits, and context across conversations
- **MCP Skills Integration**: Connect Model Context Protocol servers directly to expand capabilities with custom tools and integrations
- **System Assistant**: Set OmniHub as your Android system digital assistant for voice commands and system integration
- **Web Session Support**: Use browser-based AI platforms (ChatGPT Web, Claude Web, Gemini Web, etc.) without API keys
- **Beautiful UI**: Inspired by Grok and Perplexity, with intuitive navigation, customization, and conversation management

### How It Works

**Setup is simple:**
1. Add your AI provider API keys (OpenAI, Anthropic, Gemini, etc.)
2. Customize your behavior, tone, and skills
3. Connect MCP servers for additional capabilities
4. Start chatting—OmniHub handles the rest

**Intelligent Routing:**
OmniHub automatically selects the best provider based on:
- Model capabilities (vision, tool use)
- Cost efficiency
- Response latency
- Reliability metrics
- Your preferences

**Extended Memory:**
Your "soul" is a compressed knowledge base of your preferences, facts about you, and past conversations. OmniHub injects this into every query so your AI feels continuous and personalized.

**MCP Server Integration:**
Paste any MCP server URL into the Customize screen, and its tools become instantly available—no configuration needed.

## Use Cases

- **Personal Assistant**: Replace your phone's default assistant with a multi-powered AI that learns and adapts
- **Cost Optimization**: Route requests to the cheapest provider without sacrificing quality
- **Capability Stacking**: Access specialized models for vision, coding, analysis, and creative tasks
- **Custom Automation**: Connect MCP servers to automate workflows, access APIs, and extend functionality
- **Seamless Switching**: Use provider that's currently best—API down? OmniHub automatically failovers

## Privacy & Security

- **Encrypted Storage**: All API keys and session data are stored using Android Keystore (AES256-GCM encryption)
- **Local Processing**: Chat history and memory stay on your device by default
- **No Forced Telemetry**: Your data, your choice
- **Transparent ToS**: Web session providers are clearly marked with their provider terms

## Architecture

```
OmniHub
├── UI Layer          # Compose-based chat, customize, settings screens
├── Router Core       # Intelligent provider selection & fallback
├── Provider Adapters # OpenAI, Anthropic, Gemini, Groq, and 8+ others
├── MCP Client        # Model Context Protocol integration
├── Soul System       # Memory & knowledge base management
├── Chat History      # Room database with encrypted storage
└── Voice Assistant   # System digital assistant integration
```

## Legal Notice

By using web-session providers, you acknowledge that harvesting browser cookies may violate the terms of service of those platforms. Use at your own discretion and responsibility.

## License

MIT

---

**OmniHub**: One interface. Infinite possibilities.
