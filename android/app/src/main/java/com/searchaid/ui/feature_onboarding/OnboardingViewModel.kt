package com.searchaid.ui.feature_onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.data.preferences.SearchToolPreferences
import com.searchaid.domain.model.SearchToolConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val googleWebEnabled: Boolean = true,
    val googleImagesEnabled: Boolean = true,
    val facebookEnabled: Boolean = true,
    val instagramEnabled: Boolean = true,
    val tiktokEnabled: Boolean = true,
    val archivesEnabled: Boolean = true,
    val googleApiKey: String = "",
    val googleCx: String = "",
    val saved: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: SearchToolPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    fun onGoogleWebToggle(v: Boolean) = _state.update { it.copy(googleWebEnabled = v) }
    fun onGoogleImagesToggle(v: Boolean) = _state.update { it.copy(googleImagesEnabled = v) }
    fun onFacebookToggle(v: Boolean) = _state.update { it.copy(facebookEnabled = v) }
    fun onInstagramToggle(v: Boolean) = _state.update { it.copy(instagramEnabled = v) }
    fun onTiktokToggle(v: Boolean) = _state.update { it.copy(tiktokEnabled = v) }
    fun onArchivesToggle(v: Boolean) = _state.update { it.copy(archivesEnabled = v) }
    fun onApiKeyChange(v: String) = _state.update { it.copy(googleApiKey = v) }
    fun onCxChange(v: String) = _state.update { it.copy(googleCx = v) }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            preferences.updateConfig(
                SearchToolConfig(
                    onboardingCompleted = true,
                    googleWebEnabled = s.googleWebEnabled,
                    googleImagesEnabled = s.googleImagesEnabled,
                    facebookEnabled = s.facebookEnabled,
                    instagramEnabled = s.instagramEnabled,
                    tiktokEnabled = s.tiktokEnabled,
                    archivesEnabled = s.archivesEnabled,
                    googleApiKey = s.googleApiKey,
                    googleCx = s.googleCx,
                )
            )
            _state.update { it.copy(saved = true) }
        }
    }
}
