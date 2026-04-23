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

Room database is encrypted via SQLCipher (`net.zetetic:android-database-sqlcipher:4.5.4`).
- 256-bit AES encryption of the entire database file
- Encryption passphrase generated via `SecureRandom` (32 bytes)
- Passphrase encrypted with AES-GCM using Android Keystore hardware-backed key
- Encrypted passphrase stored in app-private file (`db_passphrase.enc`)
- Key manager: `core/security/DatabaseKeyManager.kt`

## Authentication

Current implementation uses `OfflineAuthRepository` (local-only stub).
`FirebaseAuthRepository` is implemented and ready to swap.

To enable Firebase Auth:
1. Create Firebase project at console.firebase.google.com
2. Enable Authentication (Anonymous + Email/Password)
3. Download `google-services.json` to `android/app/`
4. In `RepositoryModule.kt`, change:
   ```kotlin
   // FROM:
   abstract fun bindAuthRepository(impl: OfflineAuthRepository): AuthRepository
   // TO:
   abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
   ```
5. Build and test

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
- [x] API key restricted in Google Cloud Console (Android app + Custom Search API only)
- [x] SQLCipher database encryption (AES-256, Android Keystore)
- [x] Certificate pinning for API domains (GTS Root R1 + GlobalSign)
- [x] Firebase Auth implemented (ready for DI swap — needs google-services.json)
- [ ] Role-based access control (post-launch)
