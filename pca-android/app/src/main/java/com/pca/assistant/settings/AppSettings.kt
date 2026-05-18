package com.pca.assistant.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class ProviderMode { MOCK, BRIDGE }

enum class SttModelChoice {
    ANDROID_BUILT_IN, WHISPER_SMALL_Q5, WHISPER_TURBO_Q5, WHISPER_TURBO_PLUS_IVRIT
}

enum class LanguageChoice { SYSTEM, EN, RU, HE }

data class Settings(
    val providerMode: ProviderMode,
    val bridgeUrl: String,
    val windowMinutes: Int,
    val language: LanguageChoice,
    val sttModel: SttModelChoice,
    val geofencePause: Boolean,
    val onboardingDone: Boolean,
    val listeningEnabled: Boolean,
    val allowCellularDownloads: Boolean,
)

private val Context.dataStore by preferencesDataStore("pca_settings")

@Singleton
class AppSettings @Inject constructor(
    private val context: Context,
) {

    val flow: Flow<Settings> = context.dataStore.data
        // B-11: DataStore documents that any IOException during read must
        // be caught at the flow-collection boundary, otherwise it propagates
        // up to every collector and crashes the UI. The recovery is to emit
        // a blank preferences instance — the next field-access path below
        // falls through to defaults, so the user sees first-run state until
        // their next setting change re-creates the file.
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "DataStore read failed (using defaults): ${e.message}")
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { p ->
            // Safe-parse enums: a stale or hand-edited value from a downgrade
            // shouldn't crash the settings flow (and through it the whole UI).
            Settings(
                // Spec §3.4 — "Primary: codex CLI. Fallback: Gemini CLI."
                // BRIDGE is therefore the documented default. MockLocal stays
                // as a deep-fallback INSIDE the bridge path (health-check
                // breaker), and as a manual choice in Settings for users
                // who explicitly want fully-offline behavior.
                providerMode = parseEnum(p[K_PROVIDER], ProviderMode.BRIDGE),
                bridgeUrl = p[K_BRIDGE_URL].orEmpty(),
                windowMinutes = (p[K_WINDOW_MIN] ?: 5).coerceIn(1, 30),
                language = parseEnum(p[K_LANG], LanguageChoice.SYSTEM),
                sttModel = parseEnum(p[K_STT_MODEL], SttModelChoice.ANDROID_BUILT_IN),
                geofencePause = p[K_GEOFENCE_PAUSE] == true,
                onboardingDone = p[K_ONBOARDING] == true,
                listeningEnabled = p[K_LISTENING] != false,
                // Default OFF — Whisper turbo alone is ~800 MB and we don't
                // want to blow through a user's cellular cap silently. The
                // bootstrap worker waits for Wi-Fi until the user flips this.
                allowCellularDownloads = p[K_ALLOW_CELLULAR] == true,
            )
        }

    private inline fun <reified E : Enum<E>> parseEnum(stored: String?, default: E): E {
        if (stored.isNullOrBlank()) return default
        return try {
            enumValueOf<E>(stored)
        } catch (_: IllegalArgumentException) {
            default
        }
    }

    suspend fun setProviderMode(mode: ProviderMode) =
        context.dataStore.edit { it[K_PROVIDER] = mode.name }

    suspend fun setBridgeUrl(url: String) =
        context.dataStore.edit { it[K_BRIDGE_URL] = url.trim() }

    suspend fun setWindowMinutes(min: Int) =
        context.dataStore.edit { it[K_WINDOW_MIN] = min.coerceIn(1, 30) }

    suspend fun setLanguage(lang: LanguageChoice) =
        context.dataStore.edit { it[K_LANG] = lang.name }

    suspend fun setSttModel(model: SttModelChoice) =
        context.dataStore.edit { it[K_STT_MODEL] = model.name }

    suspend fun setGeofencePause(enabled: Boolean) =
        context.dataStore.edit { it[K_GEOFENCE_PAUSE] = enabled }

    suspend fun setOnboardingDone(done: Boolean) =
        context.dataStore.edit { it[K_ONBOARDING] = done }

    suspend fun setListeningEnabled(enabled: Boolean) =
        context.dataStore.edit { it[K_LISTENING] = enabled }

    suspend fun setAllowCellularDownloads(enabled: Boolean) =
        context.dataStore.edit { it[K_ALLOW_CELLULAR] = enabled }

    suspend fun wipe() = context.dataStore.edit { it.clear() }

    private companion object Keys {
        const val TAG = "AppSettings"
        val K_PROVIDER: Preferences.Key<String> = stringPreferencesKey("provider")
        val K_BRIDGE_URL: Preferences.Key<String> = stringPreferencesKey("bridge_url")
        val K_WINDOW_MIN: Preferences.Key<Int> = intPreferencesKey("window_min")
        val K_LANG: Preferences.Key<String> = stringPreferencesKey("language")
        val K_STT_MODEL: Preferences.Key<String> = stringPreferencesKey("stt_model")
        val K_GEOFENCE_PAUSE: Preferences.Key<Boolean> = booleanPreferencesKey("geofence_pause")
        val K_ONBOARDING: Preferences.Key<Boolean> = booleanPreferencesKey("onboarding_done")
        val K_LISTENING: Preferences.Key<Boolean> = booleanPreferencesKey("listening_enabled")
        val K_ALLOW_CELLULAR: Preferences.Key<Boolean> = booleanPreferencesKey("allow_cellular_downloads")
    }
}
