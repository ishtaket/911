package com.searchaid.domain.model

data class PersonProfile(
    val id: Long = 0,
    val name: String,
    val age: Int?,
    val photoUri: String?,
    val condition: String?,
    val distinguishingFeatures: String?,
    val habits: String?,
    val knownLocations: String?,
    val aliases: List<String> = emptyList(),
    val nicknames: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val familyNotes: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
