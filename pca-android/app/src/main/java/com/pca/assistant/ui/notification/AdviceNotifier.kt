package com.pca.assistant.ui.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
        // Android 13+ requires POST_NOTIFICATIONS to be granted at runtime;
        // without it, NotificationManager.notify silently no-ops and the
        // advice is lost without trace. Log a single warning per posted
        // notification so the issue is visible in adb logcat (B-10).
        if (!notificationPermissionGranted()) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — advice id=$interventionId dropped: ${advice.take(80)}")
            return
        }
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
            // SECURITY (PCA-S-4): hide advice body on lockscreen — it can
            // carry residual context (location label, anonymisation tokens,
            // open-thread topics) the owner doesn't want on display.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
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
        // B-25: use a stable small offset per kind instead of String.hashCode(),
        // which (a) varies between JVM implementations and (b) is large enough
        // that interventionId * 10 + hash overflows Int and collides across
        // interventions. Android's PendingIntent equality uses request code +
        // Intent.filterEquals (which IGNORES extras), so unique request codes
        // are the only thing that disambiguate the three feedback actions.
        val offset = when (kind) {
            FeedbackReceiver.FEEDBACK_USEFUL -> 1
            FeedbackReceiver.FEEDBACK_NOT_USEFUL -> 2
            FeedbackReceiver.FEEDBACK_NOT_NOW -> 3
            else -> 0
        }
        return PendingIntent.getBroadcast(
            context,
            interventionId.toInt() * KIND_BUCKET + offset,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notificationPermissionGranted(): Boolean {
        // POST_NOTIFICATIONS only exists on API 33+; older Android grants
        // notification access implicitly.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val NOTIF_ID_BASE = 5000
        private const val TAG = "AdviceNotifier"
        /** Reserve 4 request-code slots per intervention (3 feedback kinds + 1 spare). */
        private const val KIND_BUCKET = 4
    }
}
