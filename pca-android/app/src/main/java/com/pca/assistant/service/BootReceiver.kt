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
        // SECURITY (PCA-S-6): exported=true is mandatory for ACTION_BOOT_COMPLETED
        // (the system sends it), so any app on the device can also fire intents
        // at this receiver. Refuse anything that isn't one of our three
        // documented actions before doing any work.
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

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

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
