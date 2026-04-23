# SearchAid v1.0.0 — Release Checklist

## Code & Build (all done)
- [x] All 12 screens functional
- [x] 364 tests passing (L1 + L2 + L3)
- [x] Release APK builds with R8 minification
- [x] Release signing with dedicated keystore
- [x] Lint clean
- [x] ProGuard rules configured and tightened

## Security (all code-level done)
- [x] SQLCipher database encryption (AES-256)
- [x] Android Keystore for key management
- [x] Certificate pinning (googleapis.com, web.archive.org)
- [x] Network security config (HTTPS-only)
- [x] allowBackup disabled
- [x] HTTP logging disabled in release
- [x] Error messages sanitized
- [x] No secrets in source code
- [x] Restrict Google CSE API key in Cloud Console (Android app restriction)

## Play Store Submission
- [x] Privacy policy written (docs/PRIVACY_POLICY.md)
- [x] App version set (1.0.0, versionCode 1)
- [ ] Host privacy policy at a public URL
- [ ] Create Play Console developer account ($25 one-time fee)
- [ ] Upload signed AAB (run `./gradlew bundleRelease`)
- [ ] Write store listing (title, short description, full description)
- [ ] Prepare screenshots (phone + tablet, at least 2 per form factor)
- [ ] Create feature graphic (1024x500 px)
- [ ] Set content rating (IARC questionnaire)
- [ ] Set target audience and content (not for children)
- [ ] Data safety section (matches privacy policy)
- [ ] Select app category: Tools or Social
- [ ] Set pricing: Free

## Post-Release
- [ ] Set up Firebase Crashlytics for crash reporting
- [ ] Enable Firebase Auth (add google-services.json + swap 3 bindings in RepositoryModule)
- [x] Set up CI/CD (GitHub Actions — .github/workflows/ci.yml)
- [ ] Monitor API quota usage in Google Cloud Console
- [ ] Plan Phase 3 features (see PRODUCT.md)

## Build Commands
```bash
# Debug APK
cd android && ./gradlew assembleDebug

# Release APK
cd android && ./gradlew assembleRelease

# Release AAB (for Play Store upload)
cd android && ./gradlew bundleRelease

# Run tests
cd android && ./gradlew testDebugUnitTest

# Lint
cd android && ./gradlew lint
```

## Release APK Location
```
android/app/build/outputs/apk/release/app-release.apk
```

## Release AAB Location
```
android/app/build/outputs/bundle/release/app-release.aab
```
