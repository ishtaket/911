package com.pca.assistant.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pca.assistant.data.db.dao.OwnerDao
import com.pca.assistant.data.db.entity.OwnerProfileEntity
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.speaker.SpeakerIdentifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import javax.inject.Inject

enum class OnbStep { WELCOME, CONSENT, PERMS, ENROLL, FINISH }

data class OnbState(
    val step: OnbStep = OnbStep.WELCOME,
    val biometricConfirmed: Boolean = false,
    val phrasesRecorded: Int = 0,
    val embeddings: List<FloatArray> = emptyList(),
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val ownerDao: OwnerDao,
    private val speakerId: SpeakerIdentifier,
    private val settings: AppSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(OnbState())
    val state: StateFlow<OnbState> = _state

    fun next() {
        val current = _state.value.step
        _state.value = _state.value.copy(
            step = when (current) {
                OnbStep.WELCOME -> OnbStep.CONSENT
                OnbStep.CONSENT -> OnbStep.PERMS
                OnbStep.PERMS -> OnbStep.ENROLL
                OnbStep.ENROLL -> OnbStep.FINISH
                OnbStep.FINISH -> OnbStep.FINISH
            }
        )
    }

    fun back() {
        val current = _state.value.step
        _state.value = _state.value.copy(
            step = when (current) {
                OnbStep.WELCOME -> OnbStep.WELCOME
                OnbStep.CONSENT -> OnbStep.WELCOME
                OnbStep.PERMS -> OnbStep.CONSENT
                OnbStep.ENROLL -> OnbStep.PERMS
                OnbStep.FINISH -> OnbStep.ENROLL
            }
        )
    }

    fun confirmBiometric() {
        _state.value = _state.value.copy(biometricConfirmed = true)
    }

    fun addPhraseEmbedding(pcm: ShortArray) {
        val emb = speakerId.embedding(pcm)
        val next = _state.value.embeddings + emb
        _state.value = _state.value.copy(
            embeddings = next,
            phrasesRecorded = next.size
        )
    }

    fun finish(name: String, languageHint: String?) {
        viewModelScope.launch {
            val averaged = averageEmbeddings(_state.value.embeddings)
            val bytes = averaged.toLittleEndianBytes()
            ownerDao.upsert(
                OwnerProfileEntity(
                    id = 1,
                    name = name.ifBlank { "Owner" },
                    voiceEmbedding = bytes,
                    preferencesJson = "{}",
                    l3Summary = "",
                    preferredLanguage = (languageHint ?: Locale.getDefault().language).ifBlank { "en" },
                    updatedAt = System.currentTimeMillis(),
                )
            )
            settings.setOnboardingDone(true)
            settings.setListeningEnabled(true)
        }
    }

    private fun averageEmbeddings(list: List<FloatArray>): FloatArray {
        if (list.isEmpty()) return FloatArray(SpeakerIdentifier.EMBEDDING_SIZE)
        val out = FloatArray(SpeakerIdentifier.EMBEDDING_SIZE)
        for (e in list) {
            for (i in out.indices) out[i] += e[i]
        }
        val k = list.size.toFloat()
        for (i in out.indices) out[i] /= k
        // Re-L2-normalize for stable cosine
        var n = 0.0
        for (x in out) n += x.toDouble() * x
        val norm = kotlin.math.sqrt(n).toFloat()
        if (norm > 0f) for (i in out.indices) out[i] /= norm
        return out
    }

    private fun FloatArray.toLittleEndianBytes(): ByteArray {
        val bb = ByteBuffer.allocate(this.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in this) bb.putFloat(v)
        return bb.array()
    }
}
