package com.pca.assistant.anonymizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymizerTest {

    private val anon = Anonymizer()

    @Test fun `phones are tokenised`() {
        val r = anon.anonymize("Call me at +972 50-123-4567 or 0501234567.")
        assertFalse(r.anonymized.contains("0501234567"))
        assertFalse(r.anonymized.contains("+972 50-123-4567"))
        assertTrue(r.anonymized.contains("[PHONE_"))
    }

    @Test fun `email is tokenised`() {
        val r = anon.anonymize("Mail: alice@example.com please")
        assertFalse(r.anonymized.contains("alice@example.com"))
        assertTrue(r.anonymized.contains("[EMAIL_1]"))
        assertEquals("alice@example.com", r.mapping["[EMAIL_1]"])
    }

    @Test fun `same value reuses same token`() {
        val r = anon.anonymize("alice@example.com talked to alice@example.com again")
        val occurrences = "\\[EMAIL_1]".toRegex().findAll(r.anonymized).count()
        assertEquals(2, occurrences)
        assertEquals(1, r.mapping.size)
    }

    @Test fun `de-anonymize restores originals`() {
        val r = anon.anonymize("Pay to IBAN GB82WEST12345698765432 by phone +44 20 7946 0958.")
        val restored = r.deAnonymize(r.anonymized)
        assertTrue(restored.contains("GB82WEST12345698765432"))
        assertTrue(restored.contains("+44 20 7946 0958"))
    }

    @Test fun `coords are bucketed`() {
        val r = anon.anonymize("I'm at 32.0853, 34.7818 right now")
        assertFalse(r.anonymized.contains("32.0853"))
        assertTrue(r.anonymized.contains("[GEO_1]"))
    }

    @Test fun `mapping never persisted (only returned)`() {
        // Anonymizer is stateless; new instance has empty map until anonymize runs.
        val fresh = Anonymizer()
        val r = fresh.anonymize("nothing here")
        assertTrue(r.mapping.isEmpty())
    }
}
