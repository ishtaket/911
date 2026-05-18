package com.pca.assistant.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidStt
import com.pca.assistant.audio.AudioCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * MVP STT — wraps Android's system [android.speech.SpeechRecognizer].
 *
 * Limitations of this approach (documented honestly, so the future Whisper swap
 * is well-motivated):
 *   - It expects to drive the mic itself; it cannot consume already-recorded
 *     PCM buffers. So when the foreground service has already buffered a
 *     speech window via [AudioCapture] for VAD, we currently re-open the mic
 *     for a short follow-up recognise call. This is acceptable for the MVP and
 *     gets you working speech-to-text on the device with zero downloads.
 *   - Language must be hinted; auto-detect works only on Pixel-class devices.
 *
 * The interface is identical to a future Whisper recogniser, so the upgrade
 * is a constructor swap.
 */
@Singleton
class AndroidSpeechRecognizerImpl @Inject constructor(
    private val context: Context,
) : SpeechRecognizer {

    override val id: String = "android-speech-recognizer"

    override suspend fun recognize(pcm: ShortArray, hintLanguage: String?): SttResult =
        withContext(Dispatchers.Main) {
            if (!AndroidStt.isRecognitionAvailable(context)) {
                return@withContext SttResult("", 0f, null)
            }
            suspendCancellableCoroutine { cont ->
                val recogniser = AndroidStt.createSpeechRecognizer(context)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    if (!hintLanguage.isNullOrBlank()) {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, hintLanguage)
                    }
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                val listener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onResults(results: Bundle?) {
                        val list = results?.getStringArrayList(AndroidStt.RESULTS_RECOGNITION)
                        val scores = results?.getFloatArray(AndroidStt.CONFIDENCE_SCORES)
                        val text = list?.firstOrNull().orEmpty()
                        val conf = scores?.firstOrNull() ?: 0.5f
                        runCatching { recogniser.destroy() }
                        if (!cont.isCompleted) cont.resume(SttResult(text, conf, hintLanguage))
                    }

                    override fun onError(error: Int) {
                        runCatching { recogniser.destroy() }
                        // Treat all errors as "no usable text"; the pipeline tolerates empties.
                        if (!cont.isCompleted) cont.resume(SttResult("", 0f, hintLanguage))
                    }
                }
                recogniser.setRecognitionListener(listener)
                runCatching { recogniser.startListening(intent) }
                cont.invokeOnCancellation { runCatching { recogniser.destroy() } }
            }
        }
}
