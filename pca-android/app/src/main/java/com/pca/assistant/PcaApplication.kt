package com.pca.assistant

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pca.assistant.pipeline.MemoryScheduler
import com.pca.assistant.pipeline.ModelBootstrap
import com.pca.assistant.service.ListeningService
import com.pca.assistant.settings.AppSettings
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PcaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: AppSettings

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Spec §2.1 / §3.6 — notification channels must exist before any
        // foreground service is started or any advice notification fires.
        ListeningService.ensureChannels(this)
        // B-9 fix — schedule the L1/L2/L3 rollup workers at process start,
        // not in MainActivity.onCreate. Otherwise a user who toggles the
        // service from the notification shade and never opens the app
        // would never get the memory hierarchy populated.
        MemoryScheduler.schedule(this)
        // Spec §12.7 — APK ships without weights (~30 MB) and the bootstrap
        // worker pulls Whisper + ECAPA on the first qualifying network event
        // (Wi-Fi by default; user can flip to "cellular OK" in Settings).
        // Always-enqueue is safe — the worker is unique-by-name and
        // checks `registry.isReady(spec)` so a fully-installed app does nothing.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val allowCellular = settings.flow.first().allowCellularDownloads
            ModelBootstrap.schedule(this@PcaApplication, allowMetered = allowCellular)
        }
    }
}
