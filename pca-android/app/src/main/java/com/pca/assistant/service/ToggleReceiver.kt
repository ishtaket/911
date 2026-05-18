package com.pca.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ListeningService.ACTION_PAUSE,
            ListeningService.ACTION_RESUME,
            ListeningService.ACTION_STOP -> ListeningService.sendAction(context, intent.action!!)
        }
    }
}
