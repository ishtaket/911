package com.pca.assistant.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
)

private val Context.dataStore by preferencesDataStore("pca_settings")

@Singleton
class AppSettings @Inject constructor(
    private val context: Context,
) {

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            providerMode = enumValueOf(p[K_PROVIDER] ?: ProviderMode.MOCK.name),
            bridgeUrl = p[K_BRIDGE_URL].orEmpty(),
            windowMinutes = (p[K_WINDOW_MIN] ?: 5).coerceIn(1, 30),
            language = enumValueOf(p[K_LANG] ?: LanguageChoice.SYSTEM.name),
            sttModel = enumValueOf(p[K_STT_MODEL] ?: SttModelChoice.ANDROID_BUILT_IN.name),
            geofencePause = p[K_GEOFENCE_PAUSE] == true,
            onboardingDone = p[K_ONBOARDING] == true,
            listeningEnabled = p[K_LISTENING] != false,
        )
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

    suspend fun wipe() = context.dataStore.edit { it.clear() }

    private companion object Keys {
        val K_PROVIDER: Preferences.Key<String> = stringPreferencesKey("provider")
        val K_BRIDGE_URL: Preferences.Key<String> = stringPreferencesKey("bridge_url")
        val K_WINDOW_MIN: Preferences.Key<Int> = intPreferencesKey("window_min")
        val K_LANG: Preferences.Key<String> = stringPreferencesKey("language")
        val K_STT_MODEL: Preferences.Key<String> = stringPreferencesKey("stt_model")
        val K_GEOFENCE_PAUSE: Preferences.Key<Boolean> = booleanPreferencesKey("geofence_pause")
        val K_ONBOARDING: Preferences.Key<Boolean> = booleanPreferencesKey("onboarding_done")
        val K_LISTENING: Preferences.Key<Boolean> = booleanPreferencesKey("listening_enabled")
    }
}
