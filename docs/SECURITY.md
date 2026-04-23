# Security Notes

## API Key Protection

Google CSE API keys are embedded in BuildConfig and passed as query parameters.
This is a known limitation of client-only architecture.

### Current mitigations
- Keys default to empty string — app works without them (graceful degradation)
- HTTP logging disabled in release builds
- R8 minification and obfuscation enabled
- Network security config enforces HTTPS only

### Required before production
1. **Restrict API keys in Google Cloud Console**:
   - Set Android app restriction (package name + SHA-256 fingerprint)
   - Set API restriction to Custom Search API only
   - Set daily quota limits
2. **Long-term**: Implement a backend proxy that holds the API key server-side.
   Client authenticates to the proxy, proxy calls Google CSE.

## Database Encryption

Room database is currently unencrypted. For production with real PII:
- Integrate SQLCipher via `net.zetetic:android-database-sqlcipher`
- Store encryption key in Android Keystore
- Consider field-level encryption for most sensitive columns (medical conditions, locations)

## Authentication

Current implementation uses `OfflineAuthRepository` (local-only stub).
Before multi-user production:
- Enable Firebase Auth (DI binding swap in RepositoryModule)
- Implement role-based access control
- Add session timeout and re-authentication

## Audit Checklist (MASVS L2)

- [x] allowBackup disabled
- [x] Network security config with cleartext disabled
- [x] HTTP logging disabled in release
- [x] Error messages sanitized (no stack traces to UI)
- [x] No hardcoded secrets in source code
- [x] Keystore and local.properties in .gitignore
- [x] R8 minification enabled
- [x] All dependencies at current versions
- [x] No WebView, clipboard, or file storage exposure
- [x] Parameterized Room queries (no SQL injection)
- [ ] API key restricted in Google Cloud Console
- [ ] SQLCipher database encryption
- [ ] Certificate pinning for API domains
- [ ] Firebase Auth enabled
- [ ] Role-based access control
