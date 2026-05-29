package com.pca.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
// Notification.VISIBILITY_PRIVATE / NotificationCompat.VISIBILITY_PRIVATE — both used.
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.pca.assistant.R
import com.pca.assistant.audio.AudioCapture
import com.pca.assistant.audio.VoiceActivityDetector
import com.pca.assistant.data.db.dao.OwnerDao
import com.pca.assistant.data.db.dao.TranscriptDao
import com.pca.assistant.data.db.entity.TranscriptEntity
import com.pca.assistant.location.LocationProvider
import com.pca.assistant.pipeline.WindowProcessor
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.speaker.SpeakerIdentifier
import com.pca.assistant.stt.SpeechRecognizer
import com.pca.assistant.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ListeningService : LifecycleService() {

    @Inject lateinit var audioCapture: AudioCapture
    @Inject lateinit var captureDiag: com.pca.assistant.audio.CaptureDiag
    @Inject lateinit var vad: VoiceActivityDetector
    @Inject lateinit var stt: SpeechRecognizer
    @Inject lateinit var speakerId: SpeakerIdentifier
    @Inject lateinit var transcriptDao: TranscriptDao
    @Inject lateinit var ownerDao: OwnerDao
    @Inject lateinit var location: LocationProvider
    @Inject lateinit var windowProcessor: WindowProcessor
    @Inject lateinit var settings: AppSettings
    @Inject lateinit var serviceState: ServiceState

    private var captureJob: Job? = null
    private var tickerJob: Job? = null
    private val capturePcm = ArrayDeque<ShortArray>()
    private val pcmLock = Any()

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        // Android 14 enforces that a microphone-type FGS only starts when
        // RECORD_AUDIO is granted; otherwise the platform throws
        // ForegroundServiceTypeSecurityException. Bail cleanly if we lost it.
        if (!audioCapturePermissionGranted()) {
            stopSelf()
            return
        }
        // The manifest declares foregroundServiceType="microphone|location|dataSync".
        // On Android 14, EACH declared type's permission gate is enforced at
        // startForeground time — i.e. if location is declared but
        // ACCESS_FINE/COARSE_LOCATION is missing, the call throws
        // ForegroundServiceTypeSecurityException and the process dies. Compute
        // the runtime type set so we only ask the system to grant types we
        // actually have permission for; location capability degrades gracefully
        // when the user hasn't granted it yet.
        val types = computeFgsTypes()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID_LISTENING, buildListeningNotification(active = true), types)
        } else {
            startForeground(NOTIF_ID_LISTENING, buildListeningNotification(active = true))
        }
        serviceState.update(ListeningState.LISTENING)
    }

    private fun computeFgsTypes(): Int {
        var t = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (locationPermissionGranted()) {
            t = t or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        t = t or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        return t
    }

    private fun locationPermissionGranted(): Boolean {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun audioCapturePermissionGranted(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PAUSE -> applyState(ListeningState.PAUSED)
            ACTION_RESUME -> applyState(ListeningState.LISTENING)
            ACTION_STOP -> {
                applyState(ListeningState.STOPPED)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> applyState(ListeningState.LISTENING)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        captureJob?.cancel()
        tickerJob?.cancel()
        runCatching { stt.release() }
        serviceState.update(ListeningState.STOPPED)
        super.onDestroy()
    }

    private fun applyState(target: ListeningState) {
        when (target) {
            ListeningState.LISTENING -> {
                ensureCapture()
                ensureTicker()
                serviceState.update(ListeningState.LISTENING)
                refreshNotification(active = true)
            }
            ListeningState.PAUSED -> {
                captureJob?.cancel(); captureJob = null
                synchronized(pcmLock) { capturePcm.clear() }
                serviceState.update(ListeningState.PAUSED)
                refreshNotification(active = false)
            }
            ListeningState.STOPPED -> {
                captureJob?.cancel(); captureJob = null
                tickerJob?.cancel(); tickerJob = null
                synchronized(pcmLock) { capturePcm.clear() }
                serviceState.update(ListeningState.STOPPED)
            }
        }
    }

    private fun ensureCapture() {
        if (captureJob?.isActive == true) return
        captureJob = lifecycleScope.launch(SupervisorJob() + Dispatchers.IO) {
            if (!audioCapture.hasMicPermission()) return@launch
            audioCapture.stream().collect { pcm ->
                var peak = 0
                for (s in pcm) { val a = if (s < 0) -s.toInt() else s.toInt(); if (a > peak) peak = a }
                captureDiag.onChunk(peak)
                val speech = vad.isSpeech(pcm)
                if (speech) {
                    captureDiag.onSpeech()
                    synchronized(pcmLock) {
                        capturePcm.addLast(pcm)
                        // bound history: 60 s
                        while (capturePcm.size > MAX_CHUNKS) capturePcm.removeFirst()
                    }
                    runCatching { onSpeechChunk(pcm) }
                        .onFailure { captureDiag.onError("${it.javaClass.simpleName}: ${it.message}") }
                }
            }
        }
    }

    private suspend fun onSpeechChunk(pcm: ShortArray) {
        val hint = inferSttLanguageHint()
        val result = stt.recognize(pcm, hint)
        captureDiag.onRecognize(result.text)
        if (result.text.isBlank()) return
        val loc = location.current()
        val owner = ownerDao.get()
        val embedding = speakerId.embedding(pcm)
        val score: Float = owner?.voiceEmbedding?.let { ref ->
            speakerId.cosineSimilarity(embedding, byteArrayToFloatArrayLittleEndian(ref))
        } ?: 0f
        val isOwner = speakerId.isOwner(score)

        // SECURITY (PCA-S-1): per-chunk anonymisation produced incoherent
        // tokens across chunks (every chunk restarted at [EMAIL_1] for
        // different originals). We now anonymise at window-build time
        // inside WindowAggregator, with a single coherent token map for
        // the whole 5-min window. The per-chunk `textAnonymized` column
        // is best-effort and intentionally a copy of the raw text — its
        // presence is preserved for schema stability but downstream
        // consumers must re-anonymise via WindowAggregator.
        transcriptDao.insert(
            TranscriptEntity(
                ts = System.currentTimeMillis(),
                text = result.text,
                textAnonymized = result.text,
                speakerId = if (isOwner) "owner" else "other",
                speakerScore = score,
                isOwner = isOwner,
                locationLat = loc.lat,
                locationLng = loc.lng,
                placeLabel = loc.label,
                confidence = result.confidence,
                language = result.detectedLanguage,
            )
        )

        // Geofence pause: if the matched place is flagged as "pause here", stop listening.
        val cfg = settings.flow.first()
        if (cfg.geofencePause && loc.pauseFlag) {
            applyState(ListeningState.PAUSED)
        }
    }

    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = lifecycleScope.launch(SupervisorJob() + Dispatchers.Default) {
            while (isActive) {
                val cfg = settings.flow.first()
                val periodMs = cfg.windowMinutes * 60_000L
                delay(periodMs)
                val end = System.currentTimeMillis()
                val start = end - periodMs
                runCatching {
                    withContext(Dispatchers.IO) { windowProcessor.process(start, end) }
                }
            }
        }
    }

    private suspend fun inferSttLanguageHint(): String? {
        val cfg = settings.flow.first()
        return when (cfg.language) {
            com.pca.assistant.settings.LanguageChoice.RU -> "ru-RU"
            com.pca.assistant.settings.LanguageChoice.HE -> "iw-IL"
            com.pca.assistant.settings.LanguageChoice.EN -> "en-US"
            com.pca.assistant.settings.LanguageChoice.SYSTEM -> null
        }
    }

    private fun refreshNotification(active: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_LISTENING, buildListeningNotification(active))
    }

    private fun buildListeningNotification(active: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggleAction = if (active) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                getString(R.string.action_pause),
                togglePendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                getString(R.string.action_resume),
                togglePendingIntent(ACTION_RESUME)
            )
        }
        val stopAction = NotificationCompat.Action(
            R.drawable.ic_stop,
            getString(R.string.action_stop),
            togglePendingIntent(ACTION_STOP)
        )
        return NotificationCompat.Builder(this, CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.notif_listening_title))
            .setContentText(
                getString(
                    if (active) R.string.notif_listening_text_active
                    else R.string.notif_listening_text_paused
                )
            )
            .setContentIntent(contentIntent)
            .addAction(toggleAction)
            .addAction(stopAction)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // SECURITY (PCA-S-4): also pin per-notification visibility — the
            // channel default already covers Android 8+, but PRIVATE here
            // documents intent and protects older notification shades.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    private fun togglePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, ToggleReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val NOTIF_ID_LISTENING = 1001
        const val CHANNEL_LISTENING = "pca_listening"
        const val CHANNEL_ADVICE = "pca_advice"

        const val ACTION_PAUSE = "com.pca.assistant.ACTION_PAUSE"
        const val ACTION_RESUME = "com.pca.assistant.ACTION_RESUME"
        const val ACTION_STOP = "com.pca.assistant.ACTION_STOP"

        const val MAX_CHUNKS = 60

        fun start(context: Context) {
            // Android 14 enforces "started FGS must call startForeground()
            // within 5 s; otherwise ForegroundServiceDidNotStartInTimeException
            // kills the app". Our onCreate stopSelf-bails when RECORD_AUDIO
            // isn't granted yet — but the system timer is already armed by
            // the time onCreate runs, and on OneUI 6 the timeout still fires
            // even after stopSelf(). Refuse to start the service in the first
            // place when the permission is missing; the foreground service
            // is intentionally only useful with mic access anyway.
            if (!hasMicPermission(context)) return
            val intent = Intent(context, ListeningService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendAction(context: Context, action: String) {
            // Same Android-14 FGS contract as [start]. A PAUSE/RESUME/STOP
            // intent delivered to a not-yet-created service triggers
            // onCreate → stopSelf, the timer fires anyway, and the process
            // crashes. Drop the action silently when we don't yet have the
            // permission.
            if (!hasMicPermission(context)) return
            val intent = Intent(context, ListeningService::class.java).apply { this.action = action }
            ContextCompat.startForegroundService(context, intent)
        }

        private fun hasMicPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                val listening = NotificationChannel(
                    CHANNEL_LISTENING,
                    context.getString(R.string.notif_channel_listening),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notif_channel_listening_desc)
                    setShowBadge(false)
                    // SECURITY (PCA-S-4): hide "recording in progress" from
                    // a locked screen — public visibility leaks state to
                    // anyone glancing at the phone.
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
                val advice = NotificationChannel(
                    CHANNEL_ADVICE,
                    context.getString(R.string.notif_channel_advice),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notif_channel_advice_desc)
                    // SECURITY (PCA-S-4): advice can contain anonymisation
                    // tokens, location labels, etc. Lockscreen sees a
                    // generic "PCA notification" placeholder; the full
                    // text only appears once the user unlocks.
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
                nm.createNotificationChannel(listening)
                nm.createNotificationChannel(advice)
            }
        }

    }
}

internal fun byteArrayToFloatArrayLittleEndian(bytes: ByteArray): FloatArray {
    val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    val out = FloatArray(bytes.size / 4)
    for (i in out.indices) out[i] = bb.float
    return out
}
