# OmniHub Architecture: Phase 1-4 Implementation

## Overview
OmniHub evolves from a simple multi-provider router to a plugin-based ecosystem with dynamic source loading, web automation, and a curated marketplace.

---

## Phase 1: Built-in Source Interface (v1.0.2)
**Status**: Current implementation + refactor
**Timeline**: Week 1-2

### Core Concept
- All providers (OpenAI, Anthropic, Gemini, etc.) become `AiSource` implementations
- Bundled as `.apk` internal resources
- UI treats all sources uniformly—no provider-specific code in UI
- Soul system compresses conversation into `memory.md` for seamless provider switching

### Architecture
```
com.omnihub.source/
├── AiSource.kt              # Abstract interface for all providers
├── SourceRouter.kt          # Intelligent routing with fallback
├── SourceManager.kt         # Lifecycle management
├── builtin/
│   ├── OpenAiSource.kt
│   ├── AnthropicSource.kt
│   ├── GeminiSource.kt
│   ├── GroqSource.kt
│   ├── DeepSeekSource.kt
│   ├── MistralSource.kt
│   ├── PerplexitySource.kt
│   ├── KimiSource.kt
│   └── ZaiSource.kt
└── builtin/registry/
    └── BuiltinSourceRegistry.kt

com.omnihub.soul/
├── SoulUnit.kt              # Single memory entry (compressed)
├── SoulManager.kt           # Memory compression & serialization
├── SoulFormat.kt            # memory.md format definition
└── SoulDiffEngine.kt        # Extract new facts, avoid redundancy

com.omnihub.data/
├── dao/
│   ├── ConversationDao.kt
│   ├── MessageDao.kt
│   └── SoulDao.kt
└── entity/
    ├── ConversationEntity.kt
    ├── MessageEntity.kt
    └── SoulEntity.kt
```

### Key Interfaces
```kotlin
// AiSource.kt - All sources implement this
interface AiSource {
    val id: String                    // "openai", "anthropic", etc.
    val name: String                  // "ChatGPT", "Claude"
    val icon: Int                     // @drawable/ic_provider_*
    val models: List<ModelInfo>
    
    suspend fun chat(request: ChatRequest): ChatResponse
    suspend fun stream(request: ChatRequest): Flow<String>
    fun validateCredentials(): Boolean
    fun getAuthType(): AuthType       // API_KEY, WEB_SESSION, MCP
}

// SoulUnit.kt - Compressed memory
data class SoulUnit(
    val timestamp: Long,
    val providerId: String,
    val topic: String,
    val compressed: String,           // LZ4 compressed JSON
    val embeddings: FloatArray?       // Optional semantic search
)

// SourceRouter.kt
suspend fun routeWithFallback(
    request: ChatRequest,
    requirements: SourceRequirements = SourceRequirements()
): ChatResponse
```

### Soul System
**Purpose**: Compress conversation history into persistent memory that works across all providers.

**Format** (`memory.md`):
```markdown
# Memory Unit 1 (2024-09-05)
**Provider**: OpenAI
**Topic**: Python debugging
**Key Facts**:
- User prefers async/await over callbacks
- Working on FastAPI project
- Debugged SQLAlchemy connection pooling issue
**References**: [Conv-001, Conv-005]

# Memory Unit 2
...
```

**Compression Algorithm**:
1. Extract entities (topics, facts, preferences)
2. Remove duplicates across units
3. LZ4 compress JSON representation
4. Store embeddings for semantic search (optional, Phase 2)

---

## Phase 2: Remote Catalog (v1.1.0)
**Status**: Design phase
**Timeline**: Week 3-4

### Concept
- GitHub-hosted `index.min.json` with source descriptors
- Users install sources by downloading descriptor + config
- No code download yet—just metadata
- Enables "source marketplace" without app updates

### Remote Catalog Structure
```json
{
  "version": 2,
  "sources": [
    {
      "id": "openai",
      "name": "ChatGPT (OpenAI API)",
      "type": "API",
      "version": "1.2.0",
      "minOmniHubVersion": "1.1.0",
      "description": "Official OpenAI Chat Completions",
      "authType": "API_KEY",
      "endpoints": {
        "chat": "https://api.openai.com/v1/chat/completions",
        "models": "https://api.openai.com/v1/models"
      },
      "headers": {
        "Authorization": "Bearer {API_KEY}",
        "User-Agent": "OmniHub/1.1.0"
      },
      "models": [
        { "id": "gpt-4o", "name": "GPT-4 Optimized", "vision": true, "tools": true },
        { "id": "gpt-4o-mini", "name": "GPT-4 Mini", "vision": true, "tools": true }
      ],
      "capabilities": ["vision", "tools", "streaming"],
      "changelog": "Fixed token counting bug",
      "downloadUrl": "https://github.com/benjaminchume-droid/OmniHub/releases/download/sources/openai-1.2.0.jar"
    }
  ]
}
```

### Installation Flow
1. User opens "Discover Sources" → fetches `index.min.json`
2. Selects source → downloads descriptor JSON only
3. Enters credentials (API key, session cookie, etc.)
4. Source is registered in `SourceManager`
5. Next app launch: verify credentials, sync with remote catalog for updates

### Components
```
com.omnihub.source.catalog/
├── CatalogClient.kt         # Fetch index.min.json
├── CatalogRepository.kt     # Cache locally
├── SourceDescriptor.kt      # Model for source metadata
├── SourceInstaller.kt       # Install remote sources
└── SourceUpdater.kt         # Check for updates
```

---

## Phase 3: APK Extensions (v1.1.1)
**Status**: Design phase
**Timeline**: Week 5-6
**Inspired by**: Mihon (formerly Tachiyomi)

### Concept
- Sign APK sources with developer key
- OmniHub loads at runtime via `ExtensionLoader`
- Trust prompt on first install
- Enables custom Kotlin code for complex protocols (web scraping, auth flows)

### Architecture
```
com.omnihub.extension/
├── ExtensionLoader.kt       # Runtime APK loading
├── ExtensionTrustManager.kt # Verify signatures
├── ExtensionInterface.kt    # Abstract class extensions inherit
├── ExtensionPermissions.kt  # Granular permissions (network, storage, etc.)
└── ExtensionClassLoader.kt  # Isolated class loading

Extension Structure (External APK):
extension-chatgpt-web/
├── build.gradle.kts
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── com/example/extension/ChatGptWebSource.kt
│       └── AndroidManifest.xml
└── key/
    └── extension.keystore
```

### Extension Interface
```kotlin
// ExtensionSource.kt - Extensions implement this
abstract class ExtensionSource : AiSource {
    override val extensionVersion = "1.0.0"
    
    abstract override suspend fun chat(request: ChatRequest): ChatResponse
    abstract override fun validateCredentials(): Boolean
    
    // Extension-specific hooks
    open fun onInstall() {}        // Setup
    open fun onUpdate(from: String) {} // Migration
    open fun onUninstall() {}      // Cleanup
    
    // Permissions required
    open val permissions = listOf<String>()
}
```

### Loading Flow
1. Extension APK placed in `/data/data/com.omnihub/extensions/`
2. `ExtensionLoader.loadExtensions()` called on app startup
3. Verify APK signature against developer cert
4. Show trust prompt: "ChatGPT Web Extension wants permission to: Network, Storage"
5. Load DEX via `PathClassLoader`
6. Instantiate `ExtensionSource` via reflection
7. Register in `SourceManager`

---

## Phase 4: Web Sources (v1.1.2)
**Status**: Design phase
**Timeline**: Week 7-8
**Selling Point**: Automatic updates, web scraping isolation, multi-site routing

### Concept
- Each website (chatgpt.com, claude.ai, etc.) = separate source
- WebView-based protocol implementation
- Changes to site automatically downloaded (or manual update)
- Bundled at launch: ChatGPT, Claude, Gemini, Perplexity, Z.AI, Kimi, DeepSeek

### Architecture
```
com.omnihub.source.websession/
├── WebSessionSource.kt      # Base class for web-based sources
├── WebSessionManager.kt     # Cookie/localStorage management
├── BrowserAutomation.kt     # WebView + Selenium-like commands
├── sites/
│   ├── ChatGptWebSource.kt
│   ├── ClaudeWebSource.kt
│   ├── GeminiWebSource.kt
│   ├── PerplexityWebSource.kt
│   ├── ZaiWebSource.kt
│   ├── KimiWebSource.kt
│   └── DeepSeekWebSource.kt
└── protocol/
    ├── ProtocolFactory.kt
    ├── ChatGptProtocol.kt   # Specific chat protocol
    ├── ClaudeProtocol.kt
    └── ...
```

### Web Source Implementation
```kotlin
// ChatGptWebSource.kt
class ChatGptWebSource(context: Context) : WebSessionSource {
    override val id = "chatgpt-web"
    override val name = "ChatGPT (Web)"
    override val protocol = ChatGptProtocol()
    
    override suspend fun chat(request: ChatRequest): ChatResponse {
        // 1. Load website state
        val webState = webSessionManager.loadState("chatgpt")
        
        // 2. Route message through protocol (handles JS, API calls, etc.)
        return protocol.sendMessage(
            message = request.messages.last().content,
            conversationId = request.conversationId,
            cookies = webState.cookies
        )
    }
}

// ChatGptProtocol.kt - Handles chatgpt.com's specific API
class ChatGptProtocol : ChatProtocol {
    override suspend fun sendMessage(
        message: String,
        conversationId: String,
        cookies: Map<String, String>
    ): ChatResponse {
        // Direct HTTP to chatgpt.com API (no WebView needed for basic chat)
        val response = httpClient.post("https://api.openai.com/backend-api/conversation") {
            header("Authorization", "Bearer ${cookies["session_token"]}")
            setBody(mapOf("action" => "next", "messages" => [...]))
        }
        return parseResponse(response)
    }
}
```

### Protocol System (Extensible)
```kotlin
interface ChatProtocol {
    suspend fun sendMessage(
        message: String,
        conversationId: String,
        cookies: Map<String, String>
    ): ChatResponse
    
    suspend fun authenticate(username: String, password: String): AuthResult
    suspend fun getConversationHistory(conversationId: String): List<Message>
    
    // For sites that change frequently
    fun getVersion(): String  // "1.2.0"
    fun getLastUpdated(): Long // Timestamp
}
```

### Dynamic Protocol Updates
**Problem**: ChatGPT updates their API → web source breaks
**Solution**: Protocol versioning + auto-update system

1. Each `ChatProtocol` has a version
2. On startup, check remote catalog for new protocol versions
3. If newer version exists, show user: "ChatGPT Web Protocol v1.3.0 available — Update?"
4. Download new protocol APK (Phase 3 extension)
5. Hot-reload without app restart

**Remote Manifest**:
```json
{
  "sources": [
    {
      "id": "chatgpt-web",
      "currentVersion": "1.3.0",
      "changelog": "Fixed new conversation creation",
      "protocol": {
        "version": "1.3.0",
        "downloadUrl": "...",
        "checksum": "sha256:..."
      }
    }
  ]
}
```

### Bundled Web Sources (Launch)
- `chatgpt-web` (ChatGPT.com Web)
- `claude-web` (Claude.ai Web)
- `gemini-web` (Google Gemini Web)
- `perplexity-web` (Perplexity.ai Web)
- `zai-web` (Z.AI Web)
- `kimi-web` (Kimi Web)
- `deepseek-web` (DeepSeek Web)

Each bundled as extension APK inside app resources.

---

## Message Flow & Soul System Integration

### User sends message:
```
1. User types message in UI
2. Optional: Select target provider (or auto-route)
3. Load latest "soul" (memory.md)
4. Inject soul into system prompt:
   - "User's past context: [compressed facts]"
5. Send to selected provider
6. Receive response
7. Extract new facts from conversation
8. Update soul: compress, deduplicate, store
9. Display in conversation
10. Auto-backup soul to selected storage path (/storage/OmniHub/memory.md)
```

### Soul File Structure
```markdown
# OmniHub Soul
**Generated**: 2024-09-05
**Version**: 1.0
**Storage Path**: /storage/OmniHub/memories

## Metadata
- Last Updated: 2024-09-05T14:32:00Z
- Providers Used: 5 (OpenAI, Anthropic, Gemini, Groq, Perplexity)
- Total Conversations: 42
- Compressed Size: 12 KB

## Memory Units

### Unit 1: Python Async
**Date**: 2024-09-01
**Provider**: OpenAI (gpt-4o)
**Topic Tags**: #python #async #fastapi
**Summary**: User prefers async/await patterns, familiar with FastAPI, debugged SQLAlchemy pooling
**Key Facts**:
- Prefers async over callbacks
- Using SQLAlchemy with async driver
- FastAPI project in production
**Conversation ID**: conv-001

### Unit 2: Kubernetes Deployment
...
```

---

## Storage & File System Integration

### User-Selected Storage Path
**Setup**: User configures storage path during onboarding
- Default: `/storage/Omni/`
- Custom: `/sdcard/MyDocs/` etc.

### Directory Structure
```
/storage/OmniHub/
├── soul/
│   ├── memory.md           # Active soul
│   ├── memory.backup.md    # Daily backup
│   └── memory-history/     # Versioned archives
├── conversations/
│   ├── conv-2024-09-05/
│   │   ├── meta.json       # Metadata
│   │   ├── messages.jsonl  # JSONL format
│   │   └── attachments/
│   └── conv-2024-09-04/
├── sources/
│   ├── configs.json        # API keys, creds (encrypted)
│   └── extensions/         # Downloaded extension APKs
└── exports/
    ├── export-2024-09-05.json  # Full export
    └── chat-backup-2024-09.tar.gz
```

### AI Direct File Write
**Feature**: AI can write files to configured storage path

**Use Cases**:
- Export conversation to markdown
- Save code snippets
- Generate reports
- Create project scaffolds

**Implementation**:
```kotlin
// AiSource.kt extension
interface AiSource {
    // ...
    var storagePathAccess: StoragePathAccess?  // User-configured path
}

// In response:
data class ChatResponse(
    val message: String,
    val fileWrites: List<FileWrite> = emptyList()  // New field
)

data class FileWrite(
    val path: String,           // Relative to storagePath: "exports/code.kt"
    val content: String,
    val overwrite: Boolean = false,
    val mimetype: String = "text/plain"
)
```

**Usage in Soul Prompt**:
```
You can write files to the user's selected storage path using:
fileWrite("exports/my-report.md", content)

Don't write without asking the user first.
```

---

## Release & Marketplace (Post-MVP)

### GitHub Release Page Integration
- Auto-generate release notes from commits
- APK file hosting
- Version changelog (auto-generated)
- Provider update log

### Marketplace Features (v1.2.0+)
1. **Skill Store**: Users share custom prompts/instructions
2. **MCP Server Catalog**: Community-built integrations
3. **Source Extensions**: User-built web scrapers (signed APKs)
4. **Theme Marketplace**: Custom UI skins

**Governance**:
- All sources/extensions reviewed before listing
- Signature verification mandatory
- Auto-expire old versions (12+ months)
- Community ratings & reviews

---

## Version Roadmap

| Version | Phase | Features | ETA |
|---------|-------|----------|-----|
| **1.0.2** | 1 | Built-in sources, soul system, new conversation fix | Week 2 |
| **1.1.0** | 2 | Remote catalog, source discovery | Week 4 |
| **1.1.1** | 3 | APK extensions, trust prompts, ExtensionLoader | Week 6 |
| **1.1.2** | 4 | Web sources (7 bundled), protocol updates, dynamic routing | Week 8 |
| **1.2.0** | Marketplace | Skill store, MCP catalog, community extensions | Week 12 |

---

## Security Considerations

### Phase 1
- ✅ Encrypted storage (existing Android Keystore)
- ✅ Soul compression to reduce exposure window
- 🔧 Add certificate pinning for all API calls

### Phase 2
- ✅ Verify catalog HTTPS
- ✅ Checksum validation for downloaded descriptors
- 🔧 Sign index.min.json with GitHub release key

### Phase 3
- ✅ APK signature verification
- ✅ Trust prompts for new extensions
- ✅ Sandboxed class loading
- 🔧 Revocation list for compromised dev keys

### Phase 4
- ✅ Cookie/token isolation per web source
- ✅ WebView sandbox
- 🔧 Monitor for MitM attacks (cert pinning)
- 🔧 Rate limit web scraping

---

## Next Steps
1. Implement Phase 1 core interfaces
2. Refactor existing providers → `AiSource` implementations
3. Build soul compression system
4. Test fallback routing
5. Review Phase 2 remote catalog design
