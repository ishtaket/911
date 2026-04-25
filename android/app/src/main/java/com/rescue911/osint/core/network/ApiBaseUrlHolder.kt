package com.rescue911.osint.core.network

import com.rescue911.osint.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the *current* backend base URL for the URL-rewriting interceptor.
 *
 * Retrofit is built once with a placeholder; the interceptor swaps the
 * scheme/host/port on every request to whatever the holder reports.
 * That lets the user change the API base URL in Settings without a
 * full app restart or Retrofit rebuild.
 */
@Singleton
class ApiBaseUrlHolder @Inject constructor(
    prefs: AppPreferences,
) {
    @Volatile
    private var _current: HttpUrl = DEFAULT

    val current: HttpUrl get() = _current

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            prefs.apiBaseUrl.distinctUntilChanged().collect { raw ->
                raw.toHttpUrlOrNull()?.let { _current = it }
            }
        }
    }

    companion object {
        val DEFAULT: HttpUrl = AppPreferences.DEFAULT_API_BASE.toHttpUrl()
    }
}
