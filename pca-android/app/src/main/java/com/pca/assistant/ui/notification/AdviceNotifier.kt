package com.pca.assistant.ui.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pca.assistant.R
import com.pca.assistant.service.ListeningService
import com.pca.assistant.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §3.6: urgency 0 → silent log; 1 → regular; 2 → heads-up; 3 → sound+vibration.
 * Feedback buttons (useful / no / not_now) are wired through [FeedbackReceiver].
 */
@Singleton
class AdviceNotifier @Inject constructor(
    private val context: Context,
) {

    fun show(interventionId: Long, advice: String, urgency: Int) {
        ListeningService.ensureChannels(context)
        val pri = when (urgency) {
            3 -> NotificationCompat.PRIORITY_MAX
            2 -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
        val tap = PendingIntent.getActivity(
            context, interventionId.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(context, ListeningService.CHANNEL_ADVICE)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(context.getString(R.string.app_short))
            .setContentText(advice)
            .setStyle(NotificationCompat.BigTextStyle().bigText(advice))
            .setPriority(pri)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .addAction(
                R.drawable.ic_mic,
                context.getString(R.string.feedback_useful),
                feedbackPi(interventionId, FeedbackReceiver.FEEDBACK_USEFUL)
            )
            .addAction(
                R.drawable.ic_stop,
                context.getString(R.string.feedback_not_useful),
                feedbackPi(interventionId, FeedbackReceiver.FEEDBACK_NOT_USEFUL)
            )
            .addAction(
                R.drawable.ic_pause,
                context.getString(R.string.feedback_not_now),
                feedbackPi(interventionId, FeedbackReceiver.FEEDBACK_NOT_NOW)
            )
        if (urgency >= 3) builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_BASE + interventionId.toInt(), builder.build())
    }

    private fun feedbackPi(interventionId: Long, kind: String): PendingIntent {
        val intent = Intent(context, FeedbackReceiver::class.java).apply {
            action = FeedbackReceiver.ACTION_FEEDBACK
            putExtra(FeedbackReceiver.EXTRA_INTERVENTION_ID, interventionId)
            putExtra(FeedbackReceiver.EXTRA_KIND, kind)
        }
        return PendingIntent.getBroadcast(
            context,
            interventionId.toInt() * 10 + kind.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val NOTIF_ID_BASE = 5000
    }
}
