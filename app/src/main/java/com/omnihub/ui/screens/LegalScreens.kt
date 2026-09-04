package com.omnihub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class LegalDoc { PRIVACY, TERMS, COMMUNITY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(doc: LegalDoc, onBack: () -> Unit) {
    val title = when (doc) {
        LegalDoc.PRIVACY -> "Privacy Policy"
        LegalDoc.TERMS -> "Terms of Service"
        LegalDoc.COMMUNITY -> "Community Guidelines"
    }
    val body = when (doc) {
        LegalDoc.PRIVACY -> PRIVACY_POLICY
        LegalDoc.TERMS -> TERMS_OF_SERVICE
        LegalDoc.COMMUNITY -> COMMUNITY_GUIDELINES
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))
        }
    }
}

private val PRIVACY_POLICY = """
Last updated: September 2026

OmniHub ("we", "the app") respects your privacy.

1. Data we process
• Profile details you enter at setup (name, optional age/bio) — stored only on your device.
• API keys and web session cookies you add — stored encrypted on device using the Android Keystore (EncryptedSharedPreferences).
• Conversation history — stored locally on your device unless you export it.
• Device permissions you grant (e.g. microphone for voice) — used only for the stated feature.

2. What we do not do by default
• We do not operate a mandatory OmniHub cloud account that uploads your chats.
• We do not sell your personal data.
• We do not require an OmniHub login to use local features.

3. Third-party AI providers
When you send a message using a provider (OpenAI, Anthropic, Google, etc.), the content of that request is transmitted to that provider under their terms and privacy policy. You control which providers and keys you configure.

4. Storage and security
Secrets are encrypted at rest with Android Keystore–backed storage. Cleartext HTTP is disabled. Release builds use code shrinking/obfuscation.

5. Your rights
You may clear app data from system settings to delete local profile, history, and secrets. You may remove individual API keys or sessions in Settings.

6. Children
OmniHub is not directed at children under 13. Do not use the app if you are under the minimum age required in your region.

7. Changes
We may update this policy. Continued use after an in-app notice or version update constitutes acceptance of the revised policy where permitted by law.
""".trimIndent()

private val TERMS_OF_SERVICE = """
Last updated: September 2026

By using OmniHub you agree to these Terms of Service.

1. The service
OmniHub is a client application that helps you connect to third-party AI providers and optional MCP tools using credentials or sessions you supply.

2. Your responsibilities
• Comply with applicable laws and the terms of every AI provider and MCP server you connect.
• You are solely responsible for API usage, costs, rate limits, and content you submit to third parties.
• Do not use OmniHub for illegal content, malware, spam, harassment, or to violate others' rights.

3. No warranty
OmniHub is provided "AS IS". AI outputs may be inaccurate or unsafe. Verify important information independently.

4. Limitation of liability
To the maximum extent permitted by law, authors and publishers are not liable for indirect or consequential damages arising from use of the app or third-party AI services.

5. Acceptable use
Prohibited: compromising security; reverse engineering in violation of the LICENSE; automated abuse; unauthorized redistribution.

6. Proprietary software
OmniHub is proprietary. See the LICENSE file. No modification or redistribution without written permission.

7. Termination
You may stop using the app by uninstalling and clearing data.

8. Contact
For terms questions, contact the publisher via the repository or store listing.
""".trimIndent()

private val COMMUNITY_GUIDELINES = """
Last updated: September 2026

1. Be lawful
Do not use OmniHub for criminal activity or illegal content in your jurisdiction.

2. Respect others
No harassment, threats, stalking, doxxing, or non-consensual intimate imagery. No child sexual abuse material (including fictional depictions of minors).

3. AI safety
Do not intentionally bypass provider safety systems for harm. Treat AI outputs as untrusted until verified.

4. Shared tools and MCP
Respect each server's rules. Do not spam or abuse public MCP servers.

5. Security
Report vulnerabilities privately (see SECURITY.md). Do not exploit the app or connected services.

6. Enforcement
Violations may result in refusal of support. Serious illegal activity may be reported to authorities.
""".trimIndent()
