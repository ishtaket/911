package com.pca.assistant.pipeline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pca.assistant.data.db.dao.DaySummaryDao
import com.pca.assistant.data.db.dao.HourSummaryDao
import com.pca.assistant.data.db.dao.OwnerDao
import com.pca.assistant.data.db.dao.TranscriptDao
import com.pca.assistant.data.db.dao.WindowDao
import com.pca.assistant.data.db.entity.DaySummaryEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Hierarchical memory rollup (spec §2.3, §3.3 memory_note).
 *
 *  L0 (window)  — rows in `windows`, retention 24 h raw → then summarised
 *  L1 (hour)    — [HourRollupWorker] runs once per hour; collapses 12 windows
 *                 into a single summary string (memory_note concatenation).
 *  L2 (day)     — [DayRollupWorker] runs once per day; collapses 24 hours
 *                 into a day narrative.
 *  L3 (profile) — [ProfileRollupWorker] runs weekly; refreshes the owner's
 *                 long-term profile (≤ ~1K tokens).
 *
 * Each worker performs a deterministic local summarisation appropriate for an
 * MVP. When the user wires up a real LLM bridge, swap in an LLM-backed
 * summariser without touching the schedule.
 */

@HiltWorker
class HourRollupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val windowDao: WindowDao,
    private val hourDao: HourSummaryDao,
    private val transcriptDao: TranscriptDao,
    private val json: Json,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val (hourStart, hourEnd) = lastClosedHour(now)

        val transcripts = transcriptDao.between(hourStart, hourEnd)
        // Pull windows in the closed hour directly via the range query
        // (B-8 fix) — `observeRecent(200).first()` could miss the target
        // hour entirely on a busy day.
        val windowsInHour = windowDao.between(hourStart, hourEnd).filter { it.sentToLlm }

        val summary = MemoryRollup.aggregateHour(
            hourStart = hourStart,
            hourEnd = hourEnd,
            transcripts = transcripts,
            windows = windowsInHour,
            json = json,
        )
        if (summary != null) hourDao.upsert(summary)

        // Spec §2.3 retention (B-27 fix): L0 windows + raw transcripts both
        // age out at 24 h once they've contributed to an hour summary; hour
        // summaries themselves last 7 days; day summaries 30 (DayRollupWorker).
        val twentyFourHoursAgo = now - 24 * 60 * 60_000L
        transcriptDao.purgeOlderThan(twentyFourHoursAgo)
        windowDao.purgeOlderThan(twentyFourHoursAgo)
        hourDao.purgeOlderThan(now - 7L * 24 * 60 * 60_000L)
        return Result.success()
    }

    private fun lastClosedHour(now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val end = cal.timeInMillis
        cal.add(Calendar.HOUR_OF_DAY, -1)
        val start = cal.timeInMillis
        return start to end
    }
}

@HiltWorker
class DayRollupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val hourDao: HourSummaryDao,
    private val daySummaryDao: DaySummaryDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStart = cal.timeInMillis
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(yesterdayStart))

        val hours = hourDao.between(yesterdayStart, todayStart)
        if (hours.isEmpty()) return Result.success()
        val narrative = hours.joinToString("\n") { it.summary }
        daySummaryDao.upsert(
            DaySummaryEntity(
                date = date,
                narrative = narrative,
                eventsJson = "[]",
                peopleJson = "[]",
                placesJson = "[]",
                openQuestionsJson = "[]",
                mood = null,
                updatedAt = System.currentTimeMillis(),
            )
        )
        // Retention: day summaries 30 days.
        val thirtyDaysAgo = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
            .format(Date(now - 30L * 24 * 60 * 60_000L))
        daySummaryDao.purgeOlderThan(thirtyDaysAgo)
        return Result.success()
    }
}

@HiltWorker
class ProfileRollupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val daySummaryDao: DaySummaryDao,
    private val ownerDao: OwnerDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val owner = ownerDao.get() ?: return Result.success()
        // Pull the last 7 days; collapse to ≤ ~1K characters as a cheap proxy
        // for the spec's ≤1K tokens budget. Real implementation will pipe this
        // through the LLM and ask for a stable rewrite.
        val days = daySummaryDao.observeRecent(7).first()
        val merged = days.joinToString("\n") { "${it.date}: ${it.narrative.take(180)}" }
        val truncated = if (merged.length > 1024) merged.substring(0, 1024) else merged
        ownerDao.upsert(owner.copy(l3Summary = truncated, updatedAt = System.currentTimeMillis()))
        return Result.success()
    }
}

object MemoryScheduler {

    private const val HOURLY = "pca_hour_rollup"
    private const val DAILY = "pca_day_rollup"
    private const val WEEKLY = "pca_profile_rollup"

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        val noNet = Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()

        wm.enqueueUniquePeriodicWork(
            HOURLY,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<HourRollupWorker>(1, TimeUnit.HOURS)
                .setConstraints(noNet).build()
        )
        wm.enqueueUniquePeriodicWork(
            DAILY,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DayRollupWorker>(1, TimeUnit.DAYS)
                .setConstraints(noNet).build()
        )
        wm.enqueueUniquePeriodicWork(
            WEEKLY,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ProfileRollupWorker>(7, TimeUnit.DAYS)
                .setConstraints(noNet).build()
        )
    }
}
