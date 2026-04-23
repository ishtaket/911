package com.searchaid.domain.usecase

import com.searchaid.domain.identity.NameNormalizer
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.model.SocialSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildIdentityPackUseCaseTest {

    private val useCase = BuildIdentityPackUseCase(NameNormalizer())

    private fun profile(
        name: String = "Иванов Иван Петрович",
        aliases: List<String> = emptyList(),
        nicknames: List<String> = emptyList(),
        emails: List<String> = emptyList(),
        phones: List<String> = emptyList(),
        age: Int? = 72,
    ) = PersonProfile(
        id = 1L, name = name, age = age, photoUri = null,
        condition = "Alzheimer's", distinguishingFeatures = null,
        habits = null, knownLocations = null,
        aliases = aliases, nicknames = nicknames,
        emails = emails, phones = phones, familyNotes = null,
    )

    @Test
    fun `builds pack with primary name and variants`() {
        val pack = useCase(profile())
        assertEquals("Иванов Иван Петрович", pack.primaryName)
        assertTrue(pack.nameVariants.isNotEmpty())
        assertTrue(pack.nameVariants.contains("Иванов Иван Петрович"))
    }

    @Test
    fun `includes transliterated name variants`() {
        val pack = useCase(profile())
        assertTrue("Should contain Latin transliteration",
            pack.nameVariants.any { it.contains("Ivanov") })
    }

    @Test
    fun `includes aliases and nicknames`() {
        val pack = useCase(profile(
            aliases = listOf("Ваня"),
            nicknames = listOf("Дед Иван"),
        ))
        assertTrue(pack.aliases.contains("Ваня"))
        assertTrue(pack.aliases.contains("Дед Иван"))
    }

    @Test
    fun `normalizes aliases into name variants`() {
        val pack = useCase(profile(aliases = listOf("Ваня Иванов")))
        assertTrue("Alias should be normalized into variants",
            pack.nameVariants.contains("Ваня Иванов"))
        assertTrue("Reversed alias should appear",
            pack.nameVariants.contains("Иванов Ваня"))
    }

    @Test
    fun `collects handles from enabled social sources`() {
        val sources = listOf(
            SocialSource(1, 1, "VK", "profile", null, "ivan_petrov", "Moscow", null, null, true),
            SocialSource(2, 1, "OK", "profile", null, "ivan72", null, null, null, true),
            SocialSource(3, 1, "FB", "profile", null, "disabled_handle", null, null, null, false),
        )
        val pack = useCase(profile(), sources)
        assertEquals(2, pack.handles.size)
        assertTrue(pack.handles.contains("ivan_petrov"))
        assertTrue(pack.handles.contains("ivan72"))
    }

    @Test
    fun `filters blank emails and phones`() {
        val pack = useCase(profile(
            emails = listOf("ivan@mail.ru", "", "  "),
            phones = listOf("+79161234567", ""),
        ))
        assertEquals(1, pack.emails.size)
        assertEquals("ivan@mail.ru", pack.emails[0])
        assertEquals(1, pack.phones.size)
    }

    @Test
    fun `sets region from first social source`() {
        val sources = listOf(
            SocialSource(1, 1, "VK", "profile", null, "ivan", "Москва", null, null, true),
        )
        val pack = useCase(profile(), sources)
        assertEquals("Москва", pack.region)
    }

    @Test
    fun `pack has correct personId and age`() {
        val pack = useCase(profile(age = 72))
        assertEquals(1L, pack.personId)
        assertEquals(72, pack.age)
    }

    @Test
    fun `empty profile produces minimal pack`() {
        val pack = useCase(profile(name = "Иван", aliases = emptyList(), age = null))
        assertEquals("Иван", pack.primaryName)
        assertTrue(pack.nameVariants.isNotEmpty())
        assertTrue(pack.handles.isEmpty())
        assertTrue(pack.emails.isEmpty())
    }
}
