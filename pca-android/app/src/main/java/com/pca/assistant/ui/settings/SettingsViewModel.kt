package com.pca.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pca.assistant.data.db.PcaDatabase
import com.pca.assistant.data.security.DbPassphrase
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.settings.LanguageChoice
import com.pca.assistant.settings.ProviderMode
import com.pca.assistant.settings.Settings
import com.pca.assistant.settings.SttModelChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    private val db: PcaDatabase,
    private val passphrase: DbPassphrase,
) : ViewModel() {

    val state: StateFlow<Settings?> = settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setProvider(mode: ProviderMode) = viewModelScope.launch { settings.setProviderMode(mode) }
    fun setBridgeUrl(url: String) = viewModelScope.launch { settings.setBridgeUrl(url) }
    fun setWindowMinutes(m: Int) = viewModelScope.launch { settings.setWindowMinutes(m) }
    fun setLanguage(l: LanguageChoice) = viewModelScope.launch { settings.setLanguage(l) }
    fun setSttModel(m: SttModelChoice) = viewModelScope.launch { settings.setSttModel(m) }
    fun setGeofencePause(on: Boolean) = viewModelScope.launch { settings.setGeofencePause(on) }

    fun wipeEverything() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            db.ownerDao().wipe()
            db.transcriptDao().wipe()
            db.windowDao().wipe()
            db.interventionDao().wipe()
            db.openThreadDao().wipe()
            db.hourSummaryDao().wipe()
            db.daySummaryDao().wipe()
            db.placeDao().wipe()
        }
        settings.wipe()
        passphrase.wipe()
    }
}
