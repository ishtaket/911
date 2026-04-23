package com.searchaid.domain.usecase

import com.searchaid.domain.model.IdentityPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateSearchQueriesUseCaseTest {

    private val useCase = GenerateSearchQueriesUseCase()

    private fun pack(
        primaryName: String = "Иванов Иван",
        nameVariants: List<String> = listOf("Иванов Иван", "Иван Иванов", "Ivanov Ivan"),
        aliases: List<String> = emptyList(),
        handles: List<String> = emptyList(),
        emails: List<String> = emptyList(),
        phones: List<String> = emptyList(),
        age: Int? = null,
        region: String? = null,
    ) = IdentityPack(
        personId = 1L, primaryName = primaryName,
        nameVariants = nameVariants, aliases = aliases,
        handles = handles, emails = emails, phones = phones,
        age = age, region = region,
    )

    @Test
    fun `generates quoted primary name query`() {
        val queries = useCase(pack())
        assertTrue(queries.contains("\"Иванов Иван\""))
    }

    @Test
    fun `primary name with region is first query`() {
        val queries = useCase(pack(region = "Москва"))
        assertEquals("\"Иванов Иван\" Москва", queries[0])
    }

    @Test
    fun `includes age context query`() {
        val queries = useCase(pack(age = 72))
        assertTrue(queries.contains("Иванов Иван 72 лет"))
    }

    @Test
    fun `includes name variant queries`() {
        val queries = useCase(pack())
        assertTrue(queries.contains("\"Иван Иванов\""))
        assertTrue(queries.contains("\"Ivanov Ivan\""))
    }

    @Test
    fun `skips single-word name variants`() {
        val queries = useCase(pack(
            nameVariants = listOf("Иванов Иван", "Иванов", "Иван"),
        ))
        // Single words should not appear as separate queries
        assertTrue(queries.none { it == "\"Иванов\"" })
        assertTrue(queries.none { it == "\"Иван\"" })
    }

    @Test
    fun `includes alias queries`() {
        val queries = useCase(pack(aliases = listOf("Ваня", "Дед Иван")))
        assertTrue(queries.contains("\"Ваня\""))
        assertTrue(queries.contains("\"Дед Иван\""))
    }

    @Test
    fun `includes handle queries unquoted`() {
        val queries = useCase(pack(handles = listOf("ivan_petrov")))
        assertTrue(queries.contains("ivan_petrov"))
    }

    @Test
    fun `includes email and phone queries quoted`() {
        val queries = useCase(pack(
            emails = listOf("ivan@mail.ru"),
            phones = listOf("+79161234567"),
        ))
        assertTrue(queries.contains("\"ivan@mail.ru\""))
        assertTrue(queries.contains("\"+79161234567\""))
    }

    @Test
    fun `queries are deduplicated`() {
        val queries = useCase(pack(
            primaryName = "Test",
            nameVariants = listOf("Test"),
            aliases = listOf("Test"),
        ))
        val quotedTestCount = queries.count { it == "\"Test\"" }
        assertEquals("Should not have duplicate queries", 1, quotedTestCount)
    }

    @Test
    fun `empty pack produces minimal queries`() {
        val queries = useCase(pack(
            nameVariants = listOf("Иван"),
            aliases = emptyList(), handles = emptyList(),
        ))
        assertTrue(queries.isNotEmpty())
        assertTrue(queries.contains("\"Иванов Иван\""))
    }
}
