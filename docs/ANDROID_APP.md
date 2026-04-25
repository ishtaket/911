# Android App

Package: `com.rescue911.osint`. Kotlin + Jetpack Compose, Material 3, Coroutines, Flow, Hilt, Room, DataStore, Retrofit, MapLibre Android (planned).

## Build

- `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34`
- AGP `8.5.2`, Kotlin `2.0.20`, JDK 17
- Gradle wrapper bootstrapped on first run (see `scripts/dev_android_build.ps1`)

## Layout

```
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml      # Version catalog
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/rescue911/osint/
│       │   │   ├── MainActivity.kt
│       │   │   ├── Rescue911App.kt          (Hilt application)
│       │   │   ├── core/di/AppModule.kt
│       │   │   ├── data/
│       │   │   │   ├── mock/MockData.kt
│       │   │   │   ├── repository/Rescue911Repository.kt
│       │   │   │   ├── remote/Rescue911Api.kt
│       │   │   │   └── preferences/AppPreferences.kt
│       │   │   ├── domain/model/Models.kt
│       │   │   ├── feature/<area>/<Screen>.kt   (16 screens)
│       │   │   ├── navigation/Rescue911Nav.kt
│       │   │   └── ui/
│       │   │       ├── theme/Color.kt + Theme.kt
│       │   │       └── components/Components.kt
│       │   └── res/
│       │       ├── values/         strings.xml, themes.xml, colors.xml
│       │       ├── values-iw/      strings.xml (Hebrew, RTL)
│       │       ├── values-ru/      strings.xml (Russian)
│       │       ├── drawable/       ic_launcher_foreground.xml
│       │       ├── mipmap-anydpi-v26/  ic_launcher.xml + ic_launcher_round.xml
│       │       └── xml/            backup_rules.xml, data_extraction_rules.xml
│       ├── test/                  JVM unit tests
│       └── androidTest/           Compose UI tests
└── README.md
```

## Screens (16)

1. CaseListScreen
2. CreateCaseScreen
3. CaseDetailScreen
4. PersonProfileScreen
5. UploadMediaScreen
6. SearchDashboardScreen
7. EvidenceInboxScreen
8. HypothesisBoardScreen
9. GeoIntMapScreen
10. TimelineScreen *(placeholder)*
11. SocialGraphScreen *(placeholder)*
12. ArchiveFindingsScreen
13. ManualReviewQueueScreen
14. AuditLogScreen
15. SettingsScreen
16. ProviderStatusScreen

## Theme

Dark operational palette: emergency red `#E63946`, deep navy `#0B1A2A`, gold `#F1B24A`, surface `#1B2738`, on-dark text `#E6EDF7`. Marvel-rescue inspired but professional.

## i18n

- `values/strings.xml` — English
- `values-iw/strings.xml` — Hebrew (Android historical code for Hebrew is `iw`)
- `values-ru/strings.xml` — Russian

`android:supportsRtl="true"` — Compose handles mirroring for `iw`. Test with system language switch.

## API client

`Rescue911Api` (Retrofit) talks to the backend only. Base URL is read from DataStore (`AppPreferences.apiBaseUrl`, default `http://10.0.2.2:8011/` for emulator — Rescue911 dedicated local port). API keys never live on the device.

## Local cache (Room/DataStore — milestone 2)

Planned entities (limited cache for assigned cases + pending uploads):
- `LocalCaseEntity`, `LocalPersonEntity`, `LocalEvidenceSummaryEntity`,
  `LocalHypothesisEntity`, `PendingUploadEntity`, `LocalAuditSummaryEntity`.
- Encryption: TODO — wrap with SQLCipher.
- DataStore stores API base URL, language, mock-mode, operator display settings.

## Tests

- JVM: `app/src/test/java/.../ValidationTest.kt`, `MockDataTest.kt`.
- Compose UI: `app/src/androidTest/java/.../ComponentsUiTest.kt` (validation badge, risk chip).
- Smoke (ADB): `scripts/dev_android_smoke.ps1` builds + installs + launches + scans logcat.
