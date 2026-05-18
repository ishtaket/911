package com.pca.assistant.anonymizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymizerExtraTest {

    private val a = Anonymizer()

    @Test fun `blank input returns empty mapping`() {
        val r = a.anonymize("")
        assertEquals("", r.anonymized)
        assertTrue(r.mapping.isEmpty())
    }

    @Test fun `pure whitespace input passes through`() {
        val r = a.anonymize("   \n\t  ")
        assertTrue(r.mapping.isEmpty())
    }

    @Test fun `URL is replaced and round-trips`() {
        val r = a.anonymize("see https://example.com/path?q=1#frag for details")
        assertFalse(r.anonymized.contains("https://"))
        assertTrue(r.anonymized.contains("[URL_1]"))
        assertEquals("https://example.com/path?q=1#frag", r.mapping["[URL_1]"])
        assertTrue(r.deAnonymize(r.anonymized).contains("https://example.com/path?q=1#frag"))
    }

    @Test fun `multiple distinct emails get distinct tokens`() {
        val r = a.anonymize("a@x.com talked to b@x.com about c@x.com")
        assertEquals(3, r.mapping.size)
        val tokens = listOf("[EMAIL_1]", "[EMAIL_2]", "[EMAIL_3]")
        for (t in tokens) assertTrue("missing $t", r.anonymized.contains(t))
    }

    @Test fun `coordinates and phones coexist in the same string`() {
        val r = a.anonymize("Meet at 32.0853,34.7818 then call +972 50-123-4567 if late")
        assertTrue(r.anonymized.contains("[GEO_1]"))
        assertTrue(r.anonymized.contains("[PHONE_1]"))
        val restored = r.deAnonymize(r.anonymized)
        assertTrue(restored.contains("32.0853,34.7818") || restored.contains("32.0853, 34.7818"))
        assertTrue(restored.contains("+972 50-123-4567"))
    }

    @Test fun `unrelated digits below 10 chars are NOT misclassified as IDs`() {
        val r = a.anonymize("there were 42 people and 100 chairs")
        assertFalse(r.anonymized.contains("[ID_"))
        assertTrue(r.mapping.isEmpty())
    }

    @Test fun `long numeric ID is tokenised`() {
        val r = a.anonymize("ticket 1234567890123 was created")
        assertTrue(r.anonymized.contains("[ID_1]"))
        assertEquals("1234567890123", r.mapping["[ID_1]"])
    }

    @Test fun `de-anonymize on text with no tokens is a no-op`() {
        val r = a.anonymize("nothing here to anonymise")
        assertEquals("plain output", r.deAnonymize("plain output"))
    }

    @Test fun `IBAN regex catches GB-style account numbers`() {
        val r = a.anonymize("transfer to GB82WEST12345698765432 by friday")
        assertTrue(r.anonymized.contains("[IBAN_1]"))
        assertFalse(r.anonymized.contains("GB82WEST12345698765432"))
    }

    @Test fun `mixed RU + HE + EN text tokenises every PII type once`() {
        val r = a.anonymize("Звони +7 495 123 45 67 או alice@example.com about meeting at 32.0853,34.7818")
        assertTrue(r.anonymized.contains("[PHONE_1]") || r.anonymized.contains("[ID_1]"))
        assertTrue(r.anonymized.contains("[EMAIL_1]"))
        assertTrue(r.anonymized.contains("[GEO_1]"))
    }
}
