package com.searchaid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.WitnessReport

@Entity(tableName = "witness_reports")
data class WitnessReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: Long,
    val sourceName: String?,
    val sourceType: String?,
    val text: String,
    val timestamp: Long,
    val possibleLocationName: String?,
    val lat: Double?,
    val lon: Double?,
    val confidence: Float,
    val status: String,
) {
    fun toDomain() = WitnessReport(
        id, caseId, sourceName, sourceType, text, timestamp,
        possibleLocationName, lat, lon, confidence, ReportStatus.valueOf(status),
    )

    companion object {
        fun fromDomain(d: WitnessReport) = WitnessReportEntity(
            d.id, d.caseId, d.sourceName, d.sourceType, d.text, d.timestamp,
            d.possibleLocationName, d.lat, d.lon, d.confidence, d.status.name,
        )
    }
}
