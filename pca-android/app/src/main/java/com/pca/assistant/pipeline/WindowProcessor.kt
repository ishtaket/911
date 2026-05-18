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

        // Threads bookkeeping.
        if (decision.openThreadsUpdate.closed.isNotEmpty()) {
            openThreadDao.close(decision.openThreadsUpdate.closed)
        }
        if (decision.openThreadsUpdate.new.isNotEmpty()) {
            val now = System.currentTimeMillis()
            openThreadDao.upsertAll(
                decision.openThreadsUpdate.new.map { n ->
                    OpenThreadEntity(
                        id = n.id,
                        topic = n.topic,
                        openedAt = now,
                        openedInWindowId = windowId,
                        context = n.context,
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
        if (decision.intervene && !decision.advice.isNullOrBlank() && safeUrgency >= 1) {
            val id = interventionDao.insert(
                InterventionEntity(
                    windowId = windowId,
                    ts = System.currentTimeMillis(),
                    advice = decision.advice,
                    urgency = safeUrgency,
                    reason = decision.reason,
                    userFeedback = null,
                    shownAt = System.currentTimeMillis(),
                )
            )
            notifier.show(id, decision.advice, safeUrgency)
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
}
