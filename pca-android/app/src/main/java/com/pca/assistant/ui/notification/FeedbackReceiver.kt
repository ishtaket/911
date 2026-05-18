package com.pca.assistant.ui.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pca.assistant.data.db.dao.InterventionDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FeedbackReceiver : BroadcastReceiver() {

    @Inject lateinit var interventionDao: InterventionDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FEEDBACK) return
        val id = intent.getLongExtra(EXTRA_INTERVENTION_ID, -1L)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val row = interventionDao.byId(id) ?: return@launch
                interventionDao.update(row.copy(userFeedback = kind))
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.cancel(AdviceNotifier.NOTIF_ID_BASE + id.toInt())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FEEDBACK = "com.pca.assistant.FEEDBACK"
        const val EXTRA_INTERVENTION_ID = "intervention_id"
        const val EXTRA_KIND = "kind"
        const val FEEDBACK_USEFUL = "useful"
        const val FEEDBACK_NOT_USEFUL = "no"
        const val FEEDBACK_NOT_NOW = "not_now"
    }
}
