package com.searchaid.domain.model

data class SearchLead(
    val id: Long = 0,
    val caseId: Long,
    val type: LeadType,
    val platform: String?,
    val matchedValue: String?,
    val textSnippet: String?,
    val possibleLocationName: String?,
    val lat: Double?,
    val lon: Double?,
    val timestamp: Long?,
    val confidence: Float = 0f,
    val status: LeadStatus = LeadStatus.NEW,
)

enum class LeadType {
    WEB,
    SOCIAL,
    MESSENGER,
    WITNESS,
    MANUAL,
}

enum class LeadStatus {
    NEW,
    CONFIRMED,
    REJECTED,
    ARCHIVED,
}
