package com.pca.assistant.pipeline

import com.pca.assistant.data.db.dao.InterventionDao
import com.pca.assistant.data.db.dao.OpenThreadDao
import com.pca.assistant.data.db.entity.InterventionEntity
import com.pca.assistant.data.db.entity.OpenThreadEntity
import com.pca.assistant.llm.ProviderRouter
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.settings.LanguageChoice
import com.pca.assistant.ui.notification.AdviceNotifier
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The end-to-end 5-minute cycle (spec §3.3).
 *
 *   1. Collect transcripts for the [from..to) window from the DB.
 *   2. Skip empty windows; log a skipped row.
 *   3. Hand the assembled payload to [ProviderRouter] (codex bridge / mock).
 *   4. Persist the decision; create an intervention + notification if asked.
 *   5. Update open_threads (close / open / keep) from the decision.
 *   6. Append the memory_note to today's hour summary (rolled by the worker).
 */
@Singleton
class WindowProcessor @Inject constructor(
    private val aggregator: WindowAggregator,
    private val router: ProviderRouter,
    private val interventionDao: InterventionDao,
    private val openThreadDao: OpenThreadDao,
    private val settings: AppSettings,
    private val notifier: AdviceNotifier,
    private val json: Json,
) {

    suspend fun process(from: Long, to: Long) {
        val slice = aggregator.collectSlice(from, to)
        if (!slice.hadSpeech) {
            // No speech: still persist a skipped window row so the dashboard sees the tick.
            aggregator.persistWindow(slice, providerId = null, sent = false, latencyMs = null)
            return
        }

        val replyLang = inferReplyLanguage()
        val request = aggregator.buildRequest(slice.copy(windowId = 0L), replyLang)

        val start = System.currentTimeMillis()
        val routed = try {
            router.decide(request)
        } catch (t: Throwable) {
            // Should be unreachable — the router falls back to mock and mock
            // never throws — but if something pathological happens (OOM,
            // serializer collapse) we record the window as queued-but-not-sent
            // so the ticker keeps running and the dashboard sees it.
            Log.e("WindowProcessor", "router.decide failed: ${t.message}", t)
            aggregator.persistWindow(slice, providerId = null, sent = false, latencyMs = null)
            return
        }
        val (providerId, decision) = routed
        val latency = System.currentTimeMillis() - start

        // B-22 fix: persist the LLM response in the SAME insert as the window
        // row, instead of insert → byId → update. The previous sequence could
        // lose the response on a racing delete and was a 2× DB write per
        // window for no benefit. Spec §7.2 audit requirement is satisfied.
        val responseJson = json.encodeToString(
            com.pca.assistant.llm.contract.LlmDecision.serializer(),
            decision,
        )
        val windowId = aggregator.persistWindow(
            slice = slice,
            providerId = providerId,
            sent = true,
            latencyMs = latency,
            llmResponseJson = responseJson,
        )

        // Threads bookkeeping with DEFENSIVE BOUNDS against a misbehaving
        // bridge (PCA-S-33 / S-34):
        //   - close-list chunked under SQLite's 999-parameter `IN (...)` limit
        //   - new-list capped at MAX_NEW_THREADS_PER_WINDOW so a runaway LLM
        //     can't insert 10k rows in one tick.
        if (decision.openThreadsUpdate.closed.isNotEmpty()) {
            decision.openThreadsUpdate.closed
                .chunked(SQLITE_IN_LIMIT)
                .forEach { openThreadDao.close(it) }
        }
        if (decision.openThreadsUpdate.new.isNotEmpty()) {
            val now = System.currentTimeMillis()
            openThreadDao.upsertAll(
                decision.openThreadsUpdate.new
                    .take(MAX_NEW_THREADS_PER_WINDOW)
                    .map { n ->
                        OpenThreadEntity(
                            id = n.id.take(MAX_THREAD_ID_LEN),
                            topic = n.topic.take(MAX_THREAD_TOPIC_LEN),
                            openedAt = now,
                            openedInWindowId = windowId,
                            context = n.context.take(MAX_THREAD_CONTEXT_LEN),
                            due = n.due,
                            status = "open",
                            lastMentionedAt = now,
                            relatedPeopleJson = "[]",
                            relatedLocationsJson = "[]",
                        )
                    }
            )
        }

        // Spec §3.6 defines urgency 0..3. Defensively clamp here (B-7) — a
        // misbehaving bridge could return 99, which would otherwise pass
        // through to NotificationCompat.PRIORITY_MAX or worse.
        val safeUrgency = decision.urgency.coerceIn(0, 3)
        // Spec §3.6: "urgency 0 — silent log only; 1 — regular; 2 — heads-up;
        // 3 — sound + vibration". So urgency-0 interventions DO get logged
        // into the DB (B-36 fix), they just don't fire a notification. The
        // user can still see them via the history surface.
        if (decision.intervene && !decision.advice.isNullOrBlank()) {
            // PCA-S-35: cap advice + reason so a runaway LLM can't fill the
            // DB with 100k-char rows. Notification truncates anyway; the
            // history view is fine with 4 KB of text per row.
            val advice = decision.advice.take(MAX_ADVICE_LEN)
            val reason = decision.reason.take(MAX_REASON_LEN)
            val id = interventionDao.insert(
                InterventionEntity(
                    windowId = windowId,
                    ts = System.currentTimeMillis(),
                    advice = advice,
                    urgency = safeUrgency,
                    reason = reason,
                    userFeedback = null,
                    shownAt = if (safeUrgency >= 1) System.currentTimeMillis() else null,
                )
            )
            if (safeUrgency >= 1) notifier.show(id, advice, safeUrgency)
        }
    }

    private suspend fun inferReplyLanguage(): String {
        val pref = settings.flow.first().language
        return when (pref) {
            LanguageChoice.RU -> "ru"
            LanguageChoice.HE -> "iw"
            LanguageChoice.EN -> "en"
            LanguageChoice.SYSTEM -> Locale.getDefault().language.ifBlank { "en" }
        }
    }

    private companion object {
        // Defensive bounds — see PCA-S-33 / S-34 / S-35.
        const val SQLITE_IN_LIMIT = 900               // SQLite default cap is 999
        const val MAX_NEW_THREADS_PER_WINDOW = 32
        const val MAX_THREAD_ID_LEN = 128
        const val MAX_THREAD_TOPIC_LEN = 256
        const val MAX_THREAD_CONTEXT_LEN = 1024
        const val MAX_ADVICE_LEN = 4096
        const val MAX_REASON_LEN = 1024
    }
}
