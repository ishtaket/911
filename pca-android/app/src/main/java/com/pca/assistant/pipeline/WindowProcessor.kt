package com.pca.assistant.pipeline

import com.pca.assistant.data.db.dao.InterventionDao
import com.pca.assistant.data.db.dao.OpenThreadDao
import com.pca.assistant.data.db.dao.WindowDao
import com.pca.assistant.data.db.entity.InterventionEntity
import com.pca.assistant.data.db.entity.OpenThreadEntity
import com.pca.assistant.llm.ProviderRouter
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.settings.LanguageChoice
import com.pca.assistant.ui.notification.AdviceNotifier
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
    private val windowDao: WindowDao,
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
        val (providerId, decision) = router.decide(request)
        val latency = System.currentTimeMillis() - start

        val windowId = aggregator.persistWindow(slice, providerId = providerId, sent = true, latencyMs = latency)

        // Persist the LLM response for transparent audit (spec §7.2).
        val stored = windowDao.byId(windowId)
        if (stored != null) {
            windowDao.update(
                stored.copy(
                    llmResponseJson = json.encodeToString(
                        com.pca.assistant.llm.contract.LlmDecision.serializer(),
                        decision
                    )
                )
            )
        }

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

        if (decision.intervene && !decision.advice.isNullOrBlank() && decision.urgency >= 1) {
            val id = interventionDao.insert(
                InterventionEntity(
                    windowId = windowId,
                    ts = System.currentTimeMillis(),
                    advice = decision.advice,
                    urgency = decision.urgency,
                    reason = decision.reason,
                    userFeedback = null,
                    shownAt = System.currentTimeMillis(),
                )
            )
            notifier.show(id, decision.advice, decision.urgency)
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
