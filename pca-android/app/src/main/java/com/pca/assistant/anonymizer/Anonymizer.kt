package com.pca.assistant.anonymizer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §3.5. Token→original mapping NEVER touches disk: it lives only inside
 * an [AnonymizationResult] object that the calling code is expected to keep
 * on the stack for the duration of a single LLM request and then drop.
 *
 * For the MVP this is a regex-only NER (phones, emails, cards, IBANs, URLs,
 * numeric IDs). The spec asks for ML Kit Entity Extraction; that swaps in
 * later behind the same [Anonymizer] facade without callers caring.
 */
@Singleton
class Anonymizer @Inject constructor() {

    data class AnonymizationResult(
        val anonymized: String,
        /** token (e.g. "[PHONE_1]") → original string */
        val mapping: Map<String, String>,
    ) {
        /** Replace tokens with their original values — used when surfacing LLM advice to the owner. */
        fun deAnonymize(text: String): String {
            if (mapping.isEmpty()) return text
            var out = text
            for ((token, original) in mapping) {
                out = out.replace(token, original)
            }
            return out
        }
    }

    fun anonymize(text: String): AnonymizationResult {
        if (text.isBlank()) return AnonymizationResult(text, emptyMap())
        val mapping = LinkedHashMap<String, String>()
        var out = text
        for (rule in RULES) {
            out = rule.regex.replace(out) { m ->
                val original = m.value
                val token = mapping.entries.firstOrNull { it.value == original }?.key
                    ?: nextToken(mapping, rule.tag).also { mapping[it] = original }
                token
            }
        }
        return AnonymizationResult(out, mapping)
    }

    private fun nextToken(existing: Map<String, String>, tag: String): String {
        val n = existing.keys.count { it.startsWith("[$tag" + "_") } + 1
        return "[$tag" + "_$n]"
    }

    private data class Rule(val tag: String, val regex: Regex)

    private companion object {
        // Order matters: more specific patterns first so they win over generic numeric ones.
        val RULES = listOf(
            // Email
            Rule("EMAIL", Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")),
            // URL
            Rule("URL", Regex("https?://[\\w.\\-/%?=&#:+]+")),
            // IBAN (rough — letters+digits, 15–34 chars)
            Rule("IBAN", Regex("\\b[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}\\b")),
            // Credit card (13–19 digits, optional dashes/spaces, with Luhn-friendly grouping)
            Rule("CARD", Regex("\\b(?:\\d[ \\-]?){13,19}\\b")),
            // International phone, e.g. +972 50-123-4567, +7 (495) 123-45-67
            Rule("PHONE", Regex("\\+\\d[\\d \\-()]{6,20}\\d")),
            // Local 9–11 digit phones with separators
            Rule("PHONE", Regex("\\b0\\d{1,2}[ \\-]?\\d{3}[ \\-]?\\d{3,4}\\b")),
            // GPS coordinates lat,lng
            Rule("GEO", Regex("-?\\d{1,2}\\.\\d{2,6}\\s*,\\s*-?\\d{1,3}\\.\\d{2,6}")),
            // Long numeric IDs (10+ digits) that aren't already matched
            Rule("ID", Regex("\\b\\d{10,}\\b")),
        )
    }
}
