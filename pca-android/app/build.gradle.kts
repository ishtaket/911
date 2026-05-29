plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pca.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pca.assistant"
        // Spec 12.6: Android 11+ (API 30) for stable Foreground Service + NNAPI
        minSdk = 30
        targetSdk = 34
        versionCode = 3
        versionName = "0.2.1-mvp"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Target SM-G998B/DS = Exynos 2100 = arm64-v8a
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release",
                )
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
            }
        }

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true"
                )
            }
        }

        vectorDrawables { useSupportLibrary = true }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // NDK r26+ ships CMake 3.22.1 and clang-17 — required for whisper.cpp v1.7.1.
    ndkVersion = "26.1.10909125"

    signingConfigs {
        // Stable debug key committed to the repo (PCA install-update fix).
        // Android refuses to update an installed app when the new APK is signed
        // with a different key. The default auto-generated ~/.android/debug.keystore
        // is regenerated on every fresh CI runner, so each rolling pca-latest APK
        // was signed differently — sideload updates failed ("App not installed")
        // and the phone silently stayed on the first build ever installed.
        // A committed debug keystore (well-known password "android", not a secret)
        // gives every build — local and CI — the same signature, so updates
        // install over each other.
        create("debugStable") {
            storeFile = file("pca-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("releaseLocal") {
            // Default debug-keystore-style local signing for sideload to SM-G998B/DS.
            // Override via gradle properties for production.
            val ksPath = project.findProperty("PCA_KEYSTORE_PATH") as String?
            val ksPass = project.findProperty("PCA_KEYSTORE_PASSWORD") as String?
            val keyAlias = project.findProperty("PCA_KEY_ALIAS") as String?
            val keyPass = project.findProperty("PCA_KEY_PASSWORD") as String?
            if (ksPath != null && ksPass != null && keyAlias != null && keyPass != null) {
                storeFile = file(ksPath)
                storePassword = ksPass
                this.keyAlias = keyAlias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debugStable")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with releaseLocal only if user has configured keystore properties.
            val releaseCfg = signingConfigs.getByName("releaseLocal")
            if (releaseCfg.storeFile != null) signingConfig = releaseCfg
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.androidx)

    implementation(libs.datastore.preferences)
    implementation(libs.biometric)
    implementation(libs.fragment.ktx)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.play.services.location)

    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)
    implementation(libs.onnxruntime.android)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    // kotlin-reflect — needed by reflection-based invariant tests
    // (e.g. LlmPayloadPrivacyTest asserts WindowPayload has no lat/lng).
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:2.0.0")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
}
