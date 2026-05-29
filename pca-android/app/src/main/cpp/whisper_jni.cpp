// JNI bridge for whisper.cpp on Android.
//
// Lifecycle:
//   long  nativeInit(modelPath, useGpu, nThreads)         — loads a ggml model
//   String nativeRecognize(ctxPtr, pcm16k, language?)      — runs inference
//   void  nativeRelease(ctxPtr)                            — frees the context
//
// PCM contract: 16-bit signed mono at 16 000 Hz. We convert to float32 in
// [-1, 1] inside the bridge because whisper.cpp's full() entry point expects
// float samples. No raw audio crosses the JNI boundary back to Kotlin.

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cstring>
#include <cstdio>
#include <cmath>

#include "whisper.h"

#define LOG_TAG "PcaWhisperJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::string jstring_to_utf8(JNIEnv* env, jstring s) {
    if (s == nullptr) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_pca_assistant_stt_WhisperJniRecognizer_nativeInit(
    JNIEnv* env, jobject /* this */,
    jstring jModelPath,
    jboolean useGpu,
    jint nThreads
) {
    std::string modelPath = jstring_to_utf8(env, jModelPath);
    if (modelPath.empty()) {
        LOGE("nativeInit: empty model path");
        return 0;
    }

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = static_cast<bool>(useGpu);
    // flash_attn off by default — on Exynos 2100 CPU it's a wash and adds
    // memory pressure; revisit once we have NNAPI/Mali off-loading.
    cparams.flash_attn = false;

    LOGI("loading model: %s (gpu=%d threads=%d)", modelPath.c_str(), useGpu, nThreads);
    whisper_context* ctx = whisper_init_from_file_with_params(modelPath.c_str(), cparams);
    if (ctx == nullptr) {
        LOGE("whisper_init_from_file_with_params failed");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pca_assistant_stt_WhisperJniRecognizer_nativeRecognize(
    JNIEnv* env, jobject /* this */,
    jlong ctxPtr,
    jshortArray jPcm,
    jstring jLanguage,
    jint nThreads
) {
    if (ctxPtr == 0 || jPcm == nullptr) {
        return env->NewStringUTF("");
    }

    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    const jsize len = env->GetArrayLength(jPcm);
    if (len <= 0) return env->NewStringUTF("");

    // whisper.cpp needs >= 1 s of audio; right at 16000 it can error or emit
    // nothing. Pad the working buffer to a comfortable minimum so short
    // utterances still decode.
    const size_t MIN_SAMPLES = 16000 * 2; // 2 s
    const size_t inLen = static_cast<size_t>(len);
    const size_t bufLen = inLen < MIN_SAMPLES ? MIN_SAMPLES : inLen;
    std::vector<float> samples(bufLen, 0.0f);
    double sumSq = 0.0;
    {
        jshort* raw = env->GetShortArrayElements(jPcm, nullptr);
        if (!raw) return env->NewStringUTF("");
        float maxAbs = 0.0f;
        for (jsize i = 0; i < len; ++i) {
            float v = static_cast<float>(raw[i]) / 32768.0f;
            samples[i] = v;
            float a = v < 0 ? -v : v;
            if (a > maxAbs) maxAbs = a;
            sumSq += static_cast<double>(v) * v;
        }
        env->ReleaseShortArrayElements(jPcm, raw, JNI_ABORT);
        // Peak-normalize quiet far-field audio so a soft but real utterance
        // isn't decoded as silence. Cap the gain so pure noise isn't blown up.
        if (maxAbs > 1e-4f) {
            float gain = 0.95f / maxAbs;
            if (gain > 20.0f) gain = 20.0f;
            if (gain > 1.0f) {
                for (jsize i = 0; i < len; ++i) samples[i] *= gain;
            }
        }
    }
    const double rms = len > 0 ? std::sqrt(sumSq / static_cast<double>(len)) : 0.0;

    whisper_full_params fparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    fparams.print_realtime   = false;
    fparams.print_progress   = false;
    fparams.print_timestamps = false;
    fparams.print_special    = false;
    fparams.translate        = false;
    fparams.no_context       = true;
    fparams.single_segment   = false;
    fparams.suppress_blank             = false;
    fparams.suppress_non_speech_tokens = false;
    fparams.n_threads        = nThreads > 0 ? nThreads : 4;
    // Greedy at temp 0 can decode straight into an end-of-text on short
    // phone clips and emit nothing; enabling temperature fallback lets the
    // decoder retry at higher temps instead of returning empty.
    fparams.temperature      = 0.0f;
    fparams.temperature_inc  = 0.2f;
    // Disable the no-speech / logprob gates that were silently dropping
    // quiet far-field utterances as "silence".
    fparams.no_speech_thold  = 1.0f;
    fparams.logprob_thold    = -10.0f;

    std::string langStr = jstring_to_utf8(env, jLanguage);
    // Empty / "auto" — whisper.cpp will run language detection itself.
    if (!langStr.empty() && langStr != "auto") {
        fparams.language       = langStr.c_str();
        fparams.detect_language = false;
    } else {
        fparams.language       = "auto";
        fparams.detect_language = true;
    }

    const int ret = whisper_full(ctx, fparams, samples.data(), static_cast<int>(samples.size()));
    char dbg[96];
    if (ret != 0) {
        LOGW("whisper_full ret=%d", ret);
        snprintf(dbg, sizeof(dbg), "\x01rms=%.3f buf=%zu ret=%d", rms, bufLen, ret);
        return env->NewStringUTF(dbg);
    }

    std::string out;
    const int n = whisper_full_n_segments(ctx);
    out.reserve(static_cast<size_t>(n) * 32);
    for (int i = 0; i < n; ++i) {
        const char* seg = whisper_full_get_segment_text(ctx, i);
        if (seg) out.append(seg);
    }
    // When decoding yields nothing, return a diagnostic marker (prefixed with
    // \x01 so the Kotlin side can tell it apart from real transcript and show
    // it as a note instead of treating it as recognized speech).
    if (out.empty()) {
        snprintf(dbg, sizeof(dbg), "\x01rms=%.3f buf=%zu n=%d empty", rms, bufLen, n);
        return env->NewStringUTF(dbg);
    }
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_pca_assistant_stt_WhisperJniRecognizer_nativeRelease(
    JNIEnv* /* env */, jobject /* this */,
    jlong ctxPtr
) {
    if (ctxPtr == 0) return;
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    whisper_free(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pca_assistant_stt_WhisperJniRecognizer_nativeSystemInfo(
    JNIEnv* env, jobject /* this */
) {
    const char* info = whisper_print_system_info();
    return env->NewStringUTF(info ? info : "");
}
