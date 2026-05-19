package com.pca.assistant.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §3.1, layer 1 — `AudioRecord` 16 kHz mono PCM, 30-second buffers.
 *
 * No raw PCM is ever persisted (privacy rule from §7.2). Buffers are forwarded
 * to a VAD pre-filter and then dropped.
 */
@Singleton
class AudioCapture @Inject constructor(
    private val context: Context,
) {
    fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun stream(): Flow<ShortArray> = callbackFlow {
        check(hasMicPermission()) { "RECORD_AUDIO permission missing" }
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(BUFFER_BYTES)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            close(IllegalStateException("AudioRecord init failed (state=${record.state})"))
            return@callbackFlow
        }
        record.startRecording()
        try {
            val tmp = ShortArray(CHUNK_SAMPLES)
            while (!isClosedForSend) {
                val read = record.read(tmp, 0, tmp.size)
                if (read > 0) {
                    val out = tmp.copyOf(read)
                    val res = trySend(out)
                    if (res.isFailure) break
                } else if (read < 0) {
                    close(IllegalStateException("AudioRecord.read err=$read"))
                    break
                }
            }
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
        }
        awaitClose { /* released in finally */ }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SAMPLE_RATE_HZ: Int = 16_000
        /** Roughly 1 s of mono 16-bit audio. */
        const val CHUNK_SAMPLES: Int = 16_000
        const val BUFFER_BYTES: Int = CHUNK_SAMPLES * 2 * 4
    }
}
