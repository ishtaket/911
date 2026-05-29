package com.pca.assistant.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.pca.assistant.data.db.dao.InterventionDao
import com.pca.assistant.data.db.dao.OpenThreadDao
import com.pca.assistant.data.db.dao.WindowDao
import com.pca.assistant.data.db.entity.InterventionEntity
import com.pca.assistant.data.db.entity.OpenThreadEntity
import com.pca.assistant.data.db.entity.WindowEntity
import com.pca.assistant.pipeline.BootstrapProgress
import com.pca.assistant.pipeline.ModelBootstrapWorker
import com.pca.assistant.service.ListeningState
import com.pca.assistant.service.ServiceState
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.settings.ProviderMode
import com.pca.assistant.settings.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

data class DashboardState(
    val listening: ListeningState,
    val provider: String,
    /**
     * True when the user is on BRIDGE provider mode (default) but hasn't
     * configured a URL. Every LLM call silently falls back to the offline
     * mock — surfaces a tertiary-colored warning on the dashboard.
     */
    val bridgeUrlMissing: Boolean,
    val bootstrap: BootstrapProgress?,
    val windowsToday: Int,
    val interventionsToday: Int,
    val openThreadsCount: Int,
    val openThreads: List<OpenThreadEntity>,
    val recentWindows: List<WindowEntity>,
    val recentInterventions: List<InterventionEntity>,
    /**
     * Compact STT/audio diagnostic shown on the dashboard so a phone-only
     * user (no logcat) can see why windows are skipped as no_speech:
     * which engine is active, whether the native lib loaded, and whether
     * mic permission is actually granted to the capture loop.
     */
    val sttDiag: String,
    /** Per-stage capture counters: chunks/peak/VAD-speech/recognize/non-empty. */
    val capDiag: String,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    context: Context,
    settings: AppSettings,
    serviceState: ServiceState,
    windowDao: WindowDao,
    interventionDao: InterventionDao,
    openThreadDao: OpenThreadDao,
    private val whisper: com.pca.assistant.stt.WhisperJniRecognizer,
    private val audioCapture: com.pca.assistant.audio.AudioCapture,
    private val captureDiag: com.pca.assistant.audio.CaptureDiag,
) : ViewModel() {

    private fun sttDiag(): String {
        val engine = when (whisper.readyState()) {
            com.pca.assistant.stt.WhisperJniRecognizer.Ready.Loaded -> "whisper-ready"
            com.pca.assistant.stt.WhisperJniRecognizer.Ready.NoModel -> "no-model"
            com.pca.assistant.stt.WhisperJniRecognizer.Ready.NativeMissing -> "native-missing"
        }
        val native = com.pca.assistant.stt.WhisperJniRecognizer.nativeAvailable
        val mic = audioCapture.hasMicPermission()
        return "STT: $engine · native=$native · mic=$mic"
    }

    val state: StateFlow<DashboardState>

    init {
        val sinceMidnight = midnightTodayMs()
        val counts = combine(
            windowDao.countSince(sinceMidnight),
            interventionDao.countSince(sinceMidnight),
        ) { w, i -> w to i }

        val lists = combine(
            openThreadDao.observeOpen(),
            windowDao.observeRecent(20),
            interventionDao.observeRecent(20),
        ) { threads, windows, ints -> Triple(threads, windows, ints) }

        val headers = combine(
            settings.flow,
            serviceState.state,
        ) { s: Settings, ls: ListeningState -> s to ls }

        // Surface the model-bootstrap worker's status (Wi-Fi-waiting, percent,
        // succeeded, failed) so the dashboard can show a download progress
        // chip without forcing the user into Settings → Model downloads.
        val bootstrapFlow = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(ModelBootstrapWorker.UNIQUE_NAME)
            .map { infos ->
                val info = infos.firstOrNull() ?: return@map null
                // Worker emits Progress while RUNNING; final state has empty
                // progress data but a known state we still want to surface.
                val data = if (info.progress.keyValueMap.isEmpty()) info.outputData else info.progress
                BootstrapProgress.from(data, info.state)
            }

        state = combine(headers, counts, lists, bootstrapFlow, captureDiag.flow) { hdr, cnt, lst, boot, cap ->
            val (cfg, ls) = hdr
            val (windowsToday, intToday) = cnt
            val (threads, windows, ints) = lst
            DashboardState(
                listening = ls,
                provider = when (cfg.providerMode) {
                    ProviderMode.MOCK -> "Local mock"
                    ProviderMode.BRIDGE -> "HTTP bridge"
                },
                bridgeUrlMissing = cfg.providerMode == ProviderMode.BRIDGE && cfg.bridgeUrl.isBlank(),
                // Hide the chip on SUCCEEDED — bootstrap is done, no need to
                // keep nagging the user.
                bootstrap = boot?.takeUnless { it.succeeded },
                windowsToday = windowsToday,
                interventionsToday = intToday,
                openThreadsCount = threads.size,
                openThreads = threads,
                recentWindows = windows,
                recentInterventions = ints,
                sttDiag = sttDiag(),
                capDiag = "cap=${cap.chunks} peak=${cap.lastPeak} vad=${cap.speech} rec=${cap.recognize} txt=${cap.nonEmpty}",
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardState(
                listening = ListeningState.STOPPED,
                provider = "—",
                bridgeUrlMissing = false,
                bootstrap = null,
                windowsToday = 0,
                interventionsToday = 0,
                openThreadsCount = 0,
                openThreads = emptyList(),
                recentWindows = emptyList(),
                recentInterventions = emptyList(),
                sttDiag = "STT: …",
                capDiag = "cap=… peak=… vad=… rec=… txt=…",
            )
        )
    }

    private fun midnightTodayMs(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
