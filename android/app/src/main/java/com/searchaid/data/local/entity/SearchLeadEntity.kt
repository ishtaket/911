package com.searchaid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.SearchLead

@Entity(tableName = "search_leads")
data class SearchLeadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: Long,
    val type: String,
    val platform: String?,
    val matchedValue: String?,
    val textSnippet: String?,
    val possibleLocationName: String?,
    val lat: Double?,
    val lon: Double?,
    val timestamp: Long?,
    val confidence: Float,
    val status: String,
) {
    fun toDomain() = SearchLead(
        id, caseId, LeadType.valueOf(type), platform, matchedValue,
        textSnippet, possibleLocationName, lat, lon, timestamp,
        confidence, LeadStatus.valueOf(status),
    )

    companion object {
        fun fromDomain(d: SearchLead) = SearchLeadEntity(
            d.id, d.caseId, d.type.name, d.platform, d.matchedValue,
            d.textSnippet, d.possibleLocationName, d.lat, d.lon,
            d.timestamp, d.confidence, d.status.name,
        )
    }
}
