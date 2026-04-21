package com.searchaid.domain.model

data class WitnessReport(
    val id: Long = 0,
    val caseId: Long,
    val sourceName: String?,
    val sourceType: String?,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val possibleLocationName: String?,
    val lat: Double?,
    val lon: Double?,
    val confidence: Float = 0f,
    val status: ReportStatus = ReportStatus.NEW,
)

enum class ReportStatus {
    NEW,
    VERIFIED,
    REJECTED,
}
