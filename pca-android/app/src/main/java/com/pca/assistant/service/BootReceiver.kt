package com.pca.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pca.assistant.settings.AppSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: AppSettings

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        val cfg = settings.flow.first()
                        if (cfg.onboardingDone && cfg.listeningEnabled) {
                            ListeningService.start(context)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
