package com.rescue911.osint.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun `default validation state is not corroborated and not human confirmed`() {
        val s = ValidationState()
        assertFalse(s.isCorroborated)
        assertFalse(s.isHumanConfirmed)
    }

    @Test
    fun `level1 passes only when all checks are true`() {
        val partial = ValidationLevel1(schemaValid = true, urlOrSourceValid = true)
        assertFalse(partial.isPassed)

        val full = ValidationLevel1(
            schemaValid = true,
            urlOrSourceValid = true,
            timestampValid = true,
            providerResponseValid = true,
            contentHashStored = true,
            duplicateCheckPassed = true,
            legalSource = true,
        )
        assertTrue(full.isPassed)
    }

    @Test
    fun `human confirmed requires L3 confirm action`() {
        val s = ValidationState(
            level1 = ValidationLevel1(
                schemaValid = true,
                urlOrSourceValid = true,
                timestampValid = true,
                providerResponseValid = true,
                contentHashStored = true,
                duplicateCheckPassed = true,
                legalSource = true,
            ),
            level2 = ValidationLevel2(independentSources = 2, isPassed = true),
        )
        assertTrue(s.isCorroborated)
        assertFalse(s.isHumanConfirmed)

        val confirmed = s.copy(level3 = ValidationLevel3(action = L3Action.CONFIRM))
        assertTrue(confirmed.isHumanConfirmed)
    }
}
