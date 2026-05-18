package com.pca.assistant

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pca.assistant.pipeline.MemoryScheduler
import com.pca.assistant.service.ListeningService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PcaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

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
    }
}
