# Signing secrets (OmniHub + OmniHub-Sources)

Add under **Settings → Secrets and variables → Actions**.

| Secret | How to create |
|--------|----------------|
| `KEYSTORE_BASE64` | `base64 -w0 your-release.jks` (macOS: `base64 -i your-release.jks`) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

### Generate keystore once

```bash
keytool -genkeypair -v -keystore omnihub-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias omnihub
base64 -w0 omnihub-release.jks > keystore.b64
# paste keystore.b64 into KEYSTORE_BASE64 secret
```

Without secrets, CI still builds an **unsigned** release APK and attaches a GitHub Release.
