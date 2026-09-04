# Security Policy

## Reporting a Vulnerability

Report security issues privately to the repository owner. Do not open public issues for vulnerabilities.

## Data Handling

- API keys and web session tokens use **EncryptedSharedPreferences** (Android Keystore, AES-256-GCM).
- Conversation history is stored **locally on device** by default.
- Cleartext HTTP is **blocked** via network security config.
- Release builds use R8 minification/obfuscation.

## Scope

This is proprietary software. Unauthorized access, reverse engineering, or redistribution is not permitted under the LICENSE.
