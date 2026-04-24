package com.searchaid.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.searchaid.domain.model.SearchToolConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "search_tool_prefs")

@Singleton
class SearchToolPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val GOOGLE_WEB_ENABLED = booleanPreferencesKey("google_web_enabled")
        val GOOGLE_IMAGES_ENABLED = booleanPreferencesKey("google_images_enabled")
        val FACEBOOK_ENABLED = booleanPreferencesKey("facebook_enabled")
        val INSTAGRAM_ENABLED = booleanPreferencesKey("instagram_enabled")
        val TIKTOK_ENABLED = booleanPreferencesKey("tiktok_enabled")
        val ARCHIVES_ENABLED = booleanPreferencesKey("archives_enabled")
        val GOOGLE_API_KEY = stringPreferencesKey("google_api_key")
        val GOOGLE_CX = stringPreferencesKey("google_cx")
    }

    val config: Flow<SearchToolConfig> = context.dataStore.data.map { prefs ->
        SearchToolConfig(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            googleWebEnabled = prefs[Keys.GOOGLE_WEB_ENABLED] ?: true,
            googleImagesEnabled = prefs[Keys.GOOGLE_IMAGES_ENABLED] ?: true,
            facebookEnabled = prefs[Keys.FACEBOOK_ENABLED] ?: true,
            instagramEnabled = prefs[Keys.INSTAGRAM_ENABLED] ?: true,
            tiktokEnabled = prefs[Keys.TIKTOK_ENABLED] ?: true,
            archivesEnabled = prefs[Keys.ARCHIVES_ENABLED] ?: true,
            googleApiKey = prefs[Keys.GOOGLE_API_KEY] ?: "",
            googleCx = prefs[Keys.GOOGLE_CX] ?: "",
        )
    }

    suspend fun updateConfig(config: SearchToolConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = config.onboardingCompleted
            prefs[Keys.GOOGLE_WEB_ENABLED] = config.googleWebEnabled
            prefs[Keys.GOOGLE_IMAGES_ENABLED] = config.googleImagesEnabled
            prefs[Keys.FACEBOOK_ENABLED] = config.facebookEnabled
            prefs[Keys.INSTAGRAM_ENABLED] = config.instagramEnabled
            prefs[Keys.TIKTOK_ENABLED] = config.tiktokEnabled
            prefs[Keys.ARCHIVES_ENABLED] = config.archivesEnabled
            prefs[Keys.GOOGLE_API_KEY] = config.googleApiKey
            prefs[Keys.GOOGLE_CX] = config.googleCx
        }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }
}
