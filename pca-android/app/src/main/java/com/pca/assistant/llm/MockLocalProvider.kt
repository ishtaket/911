package com.pca.assistant.llm

import com.pca.assistant.llm.contract.LlmDecision
import com.pca.assistant.llm.contract.LlmRequest
import com.pca.assistant.llm.contract.NewThread
import com.pca.assistant.llm.contract.OpenThreadsUpdate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * A deterministic, fully offline provider. This is not a "fake" — it is what
 * makes the MVP usable on day zero, before the user wires up a codex / Gemini
 * bridge. It implements the spec's silence-by-default rule (§5):
 *
 *   - Stays silent unless the window contains an explicit trigger ("hey
 *     assistant", "напомни", "תזכיר") or a forward-looking promise.
 *   - When it does intervene, the advice text is produced in the requested
 *     reply language.
 *   - Closes any open thread whose topic is mentioned in the new window.
 *   - Always emits a non-empty memory_note (the spec requires one per window).
 */
@Singleton
class MockLocalProvider @Inject constructor() : LlmProvider {

    override val id: String = "mock-local"

    override suspend fun decide(request: LlmRequest): LlmDecision {
        val text = request.window.transcript.lowercase(Locale.ROOT).trim()
        val lang = request.replyLanguage.lowercase().take(2)

        // 1. Identify open threads whose topic is mentioned in this window.
        val closedNow = request.openThreads
            .filter { th -> th.topic.isNotBlank() && text.contains(th.topic.lowercase(Locale.ROOT)) }
            .map { it.id }

        // 2. Explicit invocation by owner.
        val explicit = EXPLICIT_TRIGGERS.any { text.contains(it) }

        // 3. Forward promise pattern.
        val promiseMatch = PROMISE_PATTERNS.firstOrNull { it.containsMatchIn(text) }

        val intervene = (explicit || promiseMatch != null) && request.window.isOwnerPresent
        val urgency = when {
            explicit -> 2
            promiseMatch != null -> 1
            else -> 0
        }

        val advice = when {
            !intervene -> null
            explicit -> reply(lang, "Listening — what should I do?", "Слушаю — что сделать?", "מאזין — מה לעשות?")
            promiseMatch != null -> reply(
                lang,
                "Heard a forward commitment — saved to open threads so you don't forget.",
                "Услышал обещание на будущее — запомнил, чтобы не забыли.",
                "שמעתי התחייבות עתידית — שמרתי כדי שלא תשכח."
            )
            else -> null
        }

        val newThreads = if (promiseMatch != null) {
            listOf(
                NewThread(
                    id = "thr-${abs(text.hashCode())}-${request.window.windowId}",
                    topic = "promise",
                    context = request.window.transcript.take(160),
                )
            )
        } else emptyList()

        val stillOpen = request.openThreads.map { it.id }.filter { it !in closedNow }

        val memoryNote = when {
            text.isBlank() -> "Quiet window; no notable activity."
            else -> "Speech captured (${request.window.transcript.length} chars); " +
                "owner=${request.window.isOwnerPresent}; " +
                "location=${request.window.locationLabel ?: "unknown"}"
        }

        return LlmDecision(
            windowUnderstanding = buildUnderstanding(request),
            linksToHistory = emptyList(),
            openThreadsUpdate = OpenThreadsUpdate(
                closed = closedNow,
                new = newThreads,
                stillOpen = stillOpen,
            ),
            intervene = intervene,
            advice = advice,
            urgency = urgency,
            reason = if (intervene) {
                if (explicit) "Explicit invocation by owner" else "Detected forward commitment"
            } else "Routine window — staying silent per default policy",
            memoryNote = memoryNote,
        )
    }

    private fun buildUnderstanding(request: LlmRequest): String {
        val w = request.window
        val len = w.transcript.length
        return "Window ${w.windowId} (${'$'}len chars) " +
            "at ${w.locationLabel ?: "unknown location"}; " +
            "owner ${if (w.isOwnerPresent) "present" else "absent"}; " +
            "open_threads=${request.openThreads.size}"
    }

    private fun reply(lang: String, en: String, ru: String, he: String): String = when (lang) {
        "ru" -> ru
        "iw", "he" -> he
        else -> en
    }

    private companion object {
        val EXPLICIT_TRIGGERS = listOf(
            "hey assistant", "hey, assistant",
            "эй ассистент", "ассистент,",
            "היי עוזר", "עוזר,"
        )
        // Android's ICU regex (API 33+) rejects the (?U) embedded-flag form
        // that the Java OpenJDK accepts on the desktop — kspDebug unit tests
        // green, but on a real phone the MockLocalProvider class init throws
        // PatternSyntaxException → ExceptionInInitializerError → Hilt fails
        // to construct the LLM graph → ListeningService.onCreate dies. Use
        // explicit Unicode-letter lookbehind/lookahead instead: `\p{L}` is a
        // Unicode property supported by both engines, and this also makes
        // the RU + HE word boundaries explicit instead of relying on
        // engine-specific defaults for `\b`.
        val PROMISE_PATTERNS = listOf(
            Regex("(?<!\\p{L})i (?:will|gotta|need to|have to|must|should) \\p{L}+"),
            Regex("(?<!\\p{L})remind me(?!\\p{L})"),
            Regex("(?<!\\p{L})(?:я |мне )?(?:надо|нужно|должен|должна|обещаю|напомни)(?!\\p{L})"),
            Regex("(?<!\\p{L})(?:צריך|חייב|מבטיח|תזכיר)(?!\\p{L})"),
        )
    }
}
