package com.pca.assistant.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight, in-memory capture/STT counters surfaced on the dashboard so a
 * phone-only user (no logcat) can see WHERE the pipeline drops audio when every
 * window is skipped as no_speech:
 *
 *   chunks    — PCM chunks pulled from AudioRecord (0 ⇒ capture not running)
 *   peak      — last chunk peak amplitude (≈0 ⇒ mic yields silence)
 *   speech    — chunks the VAD accepted as speech (0 with high peak ⇒ VAD too strict)
 *   recognize — STT calls made on speech chunks
 *   nonEmpty  — STT calls that returned text (0 with speech>0 ⇒ Whisper returns empty)
 *
 * Counters are process-lifetime and reset on app restart; this is a diagnostic,
 * not persisted state. Nothing here leaves the device.
 */
@Singleton
class CaptureDiag @Inject constructor() {

    data class Snap(
        val chunks: Long = 0,
        val speech: Long = 0,
        val recognize: Long = 0,
        val nonEmpty: Long = 0,
        val lastPeak: Int = 0,
        val lastErr: String = "",
    )

    private val _flow = MutableStateFlow(Snap())
    val flow: StateFlow<Snap> = _flow.asStateFlow()

    fun onChunk(peak: Int) = _flow.update { it.copy(chunks = it.chunks + 1, lastPeak = peak) }
    fun onSpeech() = _flow.update { it.copy(speech = it.speech + 1) }
    fun onRecognize(text: String) = _flow.update {
        it.copy(
            recognize = it.recognize + 1,
            nonEmpty = if (text.isNotBlank()) it.nonEmpty + 1 else it.nonEmpty,
        )
    }
    fun onError(msg: String) = _flow.update { it.copy(lastErr = msg.take(120)) }
}
