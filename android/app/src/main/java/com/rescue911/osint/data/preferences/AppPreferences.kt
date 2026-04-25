package com.rescue911.osint.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "rescue911_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyApiBase = stringPreferencesKey("api_base_url")
    private val keyLanguage = stringPreferencesKey("language")
    private val keyMockMode = booleanPreferencesKey("mock_mode")

    val apiBaseUrl: Flow<String> = context.dataStore.data.map { it[keyApiBase] ?: DEFAULT_API_BASE }
    val language: Flow<String> = context.dataStore.data.map { it[keyLanguage] ?: "en" }
    val mockMode: Flow<Boolean> = context.dataStore.data.map { it[keyMockMode] ?: true }

    suspend fun setApiBaseUrl(value: String) = context.dataStore.edit { it[keyApiBase] = value }
    suspend fun setLanguage(value: String) = context.dataStore.edit { it[keyLanguage] = value }
    suspend fun setMockMode(value: Boolean) = context.dataStore.edit { it[keyMockMode] = value }

    companion object {
        const val DEFAULT_API_BASE = "http://10.0.2.2:8000/" // Android emulator → host loopback
    }
}
