package com.pca.assistant.pipeline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pca.assistant.models.ModelDownloader
import com.pca.assistant.models.ModelRegistry
import com.pca.assistant.models.ModelSpec
import com.pca.assistant.settings.AppSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Auto-downloads the production models (Whisper ggml + ECAPA ONNX) on first
 * successful network attachment after install / onboarding.
 *
 * Per spec §12.7 the APK does NOT ship with the weights bundled — Whisper
 * Large-v3 turbo alone is ~800 MB. Instead this worker is enqueued from
 * [com.pca.assistant.PcaApplication.onCreate] as unique-work `pca_model_bootstrap`;
 * WorkManager waits for the constraint (Wi-Fi by default) and runs once,
 * retrying with exponential backoff on transient failures.
 *
 * Constraints:
 *   - Default = UNMETERED (Wi-Fi). [com.pca.assistant.settings.Settings.allowCellularDownloads]
 *     flips to CONNECTED if the user explicitly opts into cellular.
 *   - Battery: not required to be charging, but WorkManager respects Doze
 *     and may defer.
 *
 * Progress is reported via [CoroutineWorker.setProgress] using:
 *   - KEY_MODEL_ID  — the currently-downloading [ModelSpec.id]
 *   - KEY_PERCENT   — 0..100
 *   - KEY_DOWNLOADED_MB / KEY_TOTAL_MB
 * Dashboard observes the WorkInfo via WorkManager.getWorkInfosForUniqueWorkFlow.
 */
@HiltWorker
class ModelBootstrapWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val registry: ModelRegistry,
    private val downloader: ModelDownloader,
    private val settings: AppSettings,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val cfg = settings.flow.first()
        // Bootstrap targets: the spec-recommended Whisper (per current sttModel
        // pick) + ECAPA for owner identification. Optional Ivrit booster is
        // NOT auto-downloaded — it's an extra GB on top and only useful for
        // primarily-Hebrew users, so left as an explicit Settings download.
        val targets = listOfNotNull(
            registry.whisperFor(cfg.sttModel),
            ModelRegistry.ECAPA_TDNN_ONNX,
        )

        for ((index, spec) in targets.withIndex()) {
            if (registry.isReady(spec)) continue
            val outcome = downloadOne(spec, index + 1, targets.size)
            if (outcome != null) return outcome
        }
        return Result.success()
    }

    /** Returns null on success; non-null Result on retry/failure. */
    private suspend fun downloadOne(spec: ModelSpec, ordinal: Int, total: Int): Result? {
        var lastFailure: String? = null
        downloader.download(spec).collect { p ->
            when (p) {
                is ModelDownloader.Progress.Running -> {
                    setProgress(
                        workDataOf(
                            KEY_MODEL_ID to spec.id,
                            KEY_MODEL_ORDINAL to ordinal,
                            KEY_MODEL_TOTAL to total,
                            KEY_PERCENT to p.percent.coerceAtLeast(0),
                            KEY_DOWNLOADED_MB to (p.downloaded / (1024 * 1024)).toInt(),
                            KEY_TOTAL_MB to (if (p.total > 0) p.total / (1024 * 1024) else spec.approxMb.toLong()).toInt(),
                        )
                    )
                }
                is ModelDownloader.Progress.Done -> Unit
                is ModelDownloader.Progress.Failed -> { lastFailure = p.reason }
            }
        }
        return when {
            lastFailure == null -> null
            // "refused" comes from the URL-policy gate (e.g. http to public host).
            // Retrying won't help — that's a permanent config issue.
            lastFailure!!.startsWith("refused") -> Result.failure(
                workDataOf(KEY_FAIL_REASON to "refused: $lastFailure")
            )
            // sha256 mismatch is also non-retryable.
            lastFailure!!.contains("sha256 mismatch") -> Result.failure(
                workDataOf(KEY_FAIL_REASON to lastFailure)
            )
            // Everything else (network drop, 5xx, partial write) → retry with backoff.
            else -> Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "pca_model_bootstrap"

        const val KEY_MODEL_ID = "model_id"
        const val KEY_MODEL_ORDINAL = "model_ordinal"
        const val KEY_MODEL_TOTAL = "model_total"
        const val KEY_PERCENT = "percent"
        const val KEY_DOWNLOADED_MB = "downloaded_mb"
        const val KEY_TOTAL_MB = "total_mb"
        const val KEY_FAIL_REASON = "fail_reason"
    }
}

object ModelBootstrap {

    /**
     * Enqueue the bootstrap worker as unique work. Safe to call repeatedly:
     * [ExistingWorkPolicy.KEEP] makes the second call a no-op while the
     * worker is already scheduled/running. Always-call from
     * [com.pca.assistant.PcaApplication.onCreate] — the worker itself
     * checks which files are missing so a fully-installed app skips work
     * cheaply.
     */
    fun schedule(context: Context, allowMetered: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .build()
        val req = OneTimeWorkRequestBuilder<ModelBootstrapWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ModelBootstrapWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            req,
        )
    }

    /**
     * Cancel any in-flight bootstrap and re-enqueue with the new constraints.
     * Used when the user flips the "allow cellular" toggle so the existing
     * Wi-Fi-blocked job switches to "any connected" immediately.
     */
    fun reschedule(context: Context, allowMetered: Boolean) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(ModelBootstrapWorker.UNIQUE_NAME)
        schedule(context, allowMetered)
    }

    private const val BACKOFF_SECONDS = 30L
}

/** Read helper for UI — typed view over the WorkInfo progress / output data. */
data class BootstrapProgress(
    val running: Boolean,
    val enqueued: Boolean,
    val failed: Boolean,
    val succeeded: Boolean,
    val modelOrdinal: Int,
    val modelTotal: Int,
    val percent: Int,
    val downloadedMb: Int,
    val totalMb: Int,
    val failReason: String?,
) {
    companion object {
        fun from(d: Data, state: androidx.work.WorkInfo.State): BootstrapProgress = BootstrapProgress(
            running = state == androidx.work.WorkInfo.State.RUNNING,
            enqueued = state == androidx.work.WorkInfo.State.ENQUEUED || state == androidx.work.WorkInfo.State.BLOCKED,
            failed = state == androidx.work.WorkInfo.State.FAILED,
            succeeded = state == androidx.work.WorkInfo.State.SUCCEEDED,
            modelOrdinal = d.getInt(ModelBootstrapWorker.KEY_MODEL_ORDINAL, 0),
            modelTotal = d.getInt(ModelBootstrapWorker.KEY_MODEL_TOTAL, 0),
            percent = d.getInt(ModelBootstrapWorker.KEY_PERCENT, 0),
            downloadedMb = d.getInt(ModelBootstrapWorker.KEY_DOWNLOADED_MB, 0),
            totalMb = d.getInt(ModelBootstrapWorker.KEY_TOTAL_MB, 0),
            failReason = d.getString(ModelBootstrapWorker.KEY_FAIL_REASON),
        )
    }
}
