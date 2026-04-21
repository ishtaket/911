package com.searchaid.domain.model

data class MissingCase(
    val id: Long = 0,
    val personId: Long,
    val status: CaseStatus = CaseStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeenTime: Long?,
    val lastSeenLocationName: String?,
    val lastSeenLat: Double?,
    val lastSeenLon: Double?,
    val clothesDescription: String?,
    val notes: String?,
    val operatorId: String?,
)

enum class CaseStatus {
    ACTIVE,
    FOUND,
    CLOSED,
}
