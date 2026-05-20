package com.pca.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.pca.assistant.data.db.PcaDatabase
import com.pca.assistant.data.security.DbPassphrase
import com.pca.assistant.models.ModelDownloader
import com.pca.assistant.models.ModelRegistry
import com.pca.assistant.models.ModelSpec
import com.pca.assistant.pipeline.ModelBootstrap
import com.pca.assistant.service.ListeningService
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.speaker.OnnxEcapaIdentifier
import com.pca.assistant.stt.WhisperJniRecognizer
import com.pca.assistant.settings.LanguageChoice
import com.pca.assistant.settings.ProviderMode
import com.pca.assistant.settings.Settings
import com.pca.assistant.settings.SttModelChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DownloadStatus(
    val specId: String,
    val percent: Int,
    val downloadedMb: Long,
    val totalMb: Long,
    val done: Boolean,
    val failed: String?,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val context: Context,
    private val settings: AppSettings,
    private val db: PcaDatabase,
    private val passphrase: DbPassphrase,
    private val registry: ModelRegistry,
    private val downloader: ModelDownloader,
    private val whisper: WhisperJniRecognizer,
    private val ecapa: OnnxEcapaIdentifier,
) : ViewModel() {

    val state: StateFlow<Settings?> = settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _downloads = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadStatus>> = _downloads.asStateFlow()

    private val _modelsReady = MutableStateFlow(snapshotReady())
    val modelsReady: StateFlow<Map<String, Boolean>> = _modelsReady.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    fun setProvider(mode: ProviderMode) = viewModelScope.launch { settings.setProviderMode(mode) }
    fun setBridgeUrl(url: String) = viewModelScope.launch { settings.setBridgeUrl(url) }
    fun setBridgeToken(token: String) = viewModelScope.launch { settings.setBridgeToken(token) }
    fun setWindowMinutes(m: Int) = viewModelScope.launch { settings.setWindowMinutes(m) }
    fun setLanguage(l: LanguageChoice) = viewModelScope.launch { settings.setLanguage(l) }
    fun setSttModel(m: SttModelChoice) = viewModelScope.launch { settings.setSttModel(m) }
    fun setGeofencePause(on: Boolean) = viewModelScope.launch { settings.setGeofencePause(on) }

    fun setAllowCellularDownloads(on: Boolean) = viewModelScope.launch {
        settings.setAllowCellularDownloads(on)
        // Re-schedule the bootstrap worker so an already-blocked Wi-Fi-wait
        // job picks up the new constraint immediately instead of waiting
        // for Wi-Fi to appear.
        ModelBootstrap.reschedule(context, allowMetered = on)
    }

    fun download(spec: ModelSpec, overrideUrl: String? = null) {
        if (activeJobs[spec.id]?.isActive == true) return
        activeJobs[spec.id] = viewModelScope.launch {
            downloader.download(spec, overrideUrl).collect { p ->
                val status = when (p) {
                    is ModelDownloader.Progress.Running -> DownloadStatus(
                        specId = spec.id,
                        percent = p.percent,
                        downloadedMb = p.downloaded / (1024 * 1024),
                        totalMb = if (p.total > 0) p.total / (1024 * 1024) else 0,
                        done = false,
                        failed = null,
                    )
                    is ModelDownloader.Progress.Done -> {
                        DownloadStatus(spec.id, 100, p.file.length() / (1024 * 1024), p.file.length() / (1024 * 1024), true, null)
                    }
                    is ModelDownloader.Progress.Failed -> DownloadStatus(spec.id, 0, 0, 0, false, p.reason)
                }
                _downloads.value = _downloads.value + (spec.id to status)
                if (status.done) _modelsReady.value = snapshotReady()
            }
        }
    }

    fun deleteModel(spec: ModelSpec) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { registry.fileFor(spec).delete() }
            _modelsReady.value = snapshotReady()
            _downloads.value = _downloads.value - spec.id
        }
    }

    private fun snapshotReady(): Map<String, Boolean> = mapOf(
        ModelRegistry.WHISPER_SMALL_Q5.id to registry.isReady(ModelRegistry.WHISPER_SMALL_Q5),
        ModelRegistry.WHISPER_TURBO_Q5.id to registry.isReady(ModelRegistry.WHISPER_TURBO_Q5),
        ModelRegistry.IVRIT_TURBO_Q5.id to registry.isReady(ModelRegistry.IVRIT_TURBO_Q5),
        ModelRegistry.ECAPA_TDNN_ONNX.id to registry.isReady(ModelRegistry.ECAPA_TDNN_ONNX),
    )

    /**
     * Spec §7.2 "full wipe" — factory-reset everything. This is necessarily
     * destructive and somewhat racy: the foreground service is holding open
     * Room handles, the dashboard is observing DAO flows, and the native
     * model contexts have file descriptors. We sequence the wipe so all of
     * those drop their references before we close + delete the DB file,
     * then kill the process so nothing has a chance to crash on a closed
     * connection. The user re-opens the app and lands in onboarding.
     *
     * [onWiped] runs after the storage layer is gone but BEFORE the kill,
     * so the UI can show a "PCA will close now" toast or just no-op.
     */
    fun wipeEverything(onWiped: () -> Unit = {}) = viewModelScope.launch {
        // 1. Tell the foreground service to stop — releases the mic, cancels
        //    the ticker, lets the recogniser drop its native handle.
        runCatching { ListeningService.sendAction(context, ListeningService.ACTION_STOP) }
        runCatching { whisper.release() }
        runCatching { ecapa.release() }

        withContext(Dispatchers.IO) {
            // 2. Best-effort table wipe — failures are ignored, the file is
            //    going away anyway.
            runCatching { db.ownerDao().wipe() }
            runCatching { db.transcriptDao().wipe() }
            runCatching { db.windowDao().wipe() }
            runCatching { db.interventionDao().wipe() }
            runCatching { db.openThreadDao().wipe() }
            runCatching { db.hourSummaryDao().wipe() }
            runCatching { db.daySummaryDao().wipe() }
            runCatching { db.placeDao().wipe() }
            // 3. Close + delete the SQLCipher file. Without this, the next
            //    process start generates a new Keystore key and Room tries
            //    to open the now-unreadable ciphertext file → crash loop.
            runCatching { db.close() }
            runCatching { context.deleteDatabase(PcaDatabase.DB_NAME) }
            // 4. Drop the downloaded model files — clean factory reset.
            runCatching {
                registry.modelsDir().listFiles()?.forEach { it.delete() }
            }
        }
        // 5. Settings → DataStore (so onboarding_done is false on relaunch),
        //    Keystore wrapping key (so old ciphertext is unrecoverable).
        runCatching { settings.wipe() }
        runCatching { passphrase.wipe() }

        onWiped()

        // 6. Kill the process. The user re-opens the app and lands in
        //    onboarding. This is the only safe escape from "DB closed but
        //    DAOs still injected into live ViewModels" state.
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
