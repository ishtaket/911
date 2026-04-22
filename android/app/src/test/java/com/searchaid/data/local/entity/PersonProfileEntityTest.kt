package com.searchaid.data.local.entity

import com.searchaid.domain.model.PersonProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonProfileEntityTest {

    @Test
    fun `fromDomain and toDomain roundtrip preserves all fields`() {
        val profile = PersonProfile(
            id = 42,
            name = "Ivan Petrov",
            age = 78,
            photoUri = "content://photo/1",
            condition = "Alzheimer's",
            distinguishingFeatures = "Scar on left hand",
            habits = "Walks to the park every morning",
            knownLocations = "Central Park, Home",
            aliases = listOf("Vanya", "Petrov I."),
            nicknames = listOf("grandpa_ivan"),
            emails = listOf("ivan@test.com"),
            phones = listOf("+71234567890"),
            familyNotes = "Prefers the east entrance",
            createdAt = 1000L,
            updatedAt = 2000L,
        )

        val entity = PersonProfileEntity.fromDomain(profile)
        val restored = entity.toDomain()

        assertEquals(profile, restored)
    }

    @Test
    fun `fromDomain handles nulls and empty lists`() {
        val profile = PersonProfile(
            id = 0,
            name = "Test",
            age = null,
            photoUri = null,
            condition = null,
            distinguishingFeatures = null,
            habits = null,
            knownLocations = null,
            aliases = emptyList(),
            nicknames = emptyList(),
            emails = emptyList(),
            phones = emptyList(),
            familyNotes = null,
            createdAt = 0,
            updatedAt = 0,
        )

        val entity = PersonProfileEntity.fromDomain(profile)
        val restored = entity.toDomain()

        assertEquals(profile, restored)
    }
}
