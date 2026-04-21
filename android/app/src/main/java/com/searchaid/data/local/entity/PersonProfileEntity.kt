package com.searchaid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.searchaid.domain.model.PersonProfile

@Entity(tableName = "person_profiles")
data class PersonProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val age: Int?,
    val photoUri: String?,
    val condition: String?,
    val distinguishingFeatures: String?,
    val habits: String?,
    val knownLocations: String?,
    val aliases: List<String>,
    val nicknames: List<String>,
    val emails: List<String>,
    val phones: List<String>,
    val familyNotes: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toDomain() = PersonProfile(
        id = id, name = name, age = age, photoUri = photoUri,
        condition = condition, distinguishingFeatures = distinguishingFeatures,
        habits = habits, knownLocations = knownLocations,
        aliases = aliases, nicknames = nicknames, emails = emails, phones = phones,
        familyNotes = familyNotes, createdAt = createdAt, updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(d: PersonProfile) = PersonProfileEntity(
            id = d.id, name = d.name, age = d.age, photoUri = d.photoUri,
            condition = d.condition, distinguishingFeatures = d.distinguishingFeatures,
            habits = d.habits, knownLocations = d.knownLocations,
            aliases = d.aliases, nicknames = d.nicknames, emails = d.emails, phones = d.phones,
            familyNotes = d.familyNotes, createdAt = d.createdAt, updatedAt = d.updatedAt,
        )
    }
}
