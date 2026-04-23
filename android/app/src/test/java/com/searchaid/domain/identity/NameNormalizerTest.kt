package com.searchaid.domain.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameNormalizerTest {

    private val normalizer = NameNormalizer()

    @Test
    fun `normalize returns original name`() {
        val result = normalizer.normalize("Иванов Иван")
        assertTrue(result.contains("Иванов Иван"))
    }

    @Test
    fun `normalize trims whitespace`() {
        val result = normalizer.normalize("  Иванов Иван  ")
        assertTrue(result.contains("Иванов Иван"))
    }

    @Test
    fun `normalize returns empty for blank input`() {
        assertTrue(normalizer.normalize("").isEmpty())
        assertTrue(normalizer.normalize("   ").isEmpty())
    }

    @Test
    fun `normalize generates reversed name order`() {
        val result = normalizer.normalize("Иванов Иван")
        assertTrue("Should contain reversed order", result.contains("Иван Иванов"))
    }

    @Test
    fun `normalize generates initials`() {
        val result = normalizer.normalize("Иванов Иван Петрович")
        assertTrue("Should contain initials variant", result.contains("Иванов И.П."))
    }

    @Test
    fun `normalize includes individual name parts`() {
        val result = normalizer.normalize("Иванов Иван")
        assertTrue(result.contains("Иванов"))
        assertTrue(result.contains("Иван"))
    }

    @Test
    fun `normalize generates Latin transliteration`() {
        val result = normalizer.normalize("Иванов Иван")
        assertTrue("Should contain transliterated variant",
            result.any { it.contains("Ivanov") })
    }

    @Test
    fun `transliterateCyrToLat converts basic Cyrillic`() {
        assertEquals("Ivan", normalizer.transliterateCyrToLat("Иван"))
        assertEquals("Ivanov", normalizer.transliterateCyrToLat("Иванов"))
    }

    @Test
    fun `transliterateCyrToLat handles complex chars`() {
        assertEquals("Zhukova", normalizer.transliterateCyrToLat("Жукова"))
        assertEquals("Shcherbakov", normalizer.transliterateCyrToLat("Щербаков"))
        assertEquals("Chernyshev", normalizer.transliterateCyrToLat("Чернышев"))
    }

    @Test
    fun `transliterateLatToCyr converts basic Latin`() {
        assertEquals("Иван", normalizer.transliterateLatToCyr("Ivan"))
    }

    @Test
    fun `transliterateLatToCyr handles multi-char mappings`() {
        assertEquals("Жуков", normalizer.transliterateLatToCyr("Zhukov"))
        assertEquals("Чернов", normalizer.transliterateLatToCyr("Chernov"))
    }

    @Test
    fun `normalize single name produces transliteration`() {
        val result = normalizer.normalize("Алексей")
        assertTrue(result.contains("Алексей"))
        assertTrue("Should have transliterated variant",
            result.any { !it.contains("А") && it.isNotBlank() })
    }

    @Test
    fun `transliterateCyrToLat removes soft and hard signs`() {
        assertEquals("Igor", normalizer.transliterateCyrToLat("Игорь"))
        assertEquals("Olga", normalizer.transliterateCyrToLat("Ольга"))
    }
}
