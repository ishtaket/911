package com.searchaid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.searchaid.domain.model.SearchZone

@Entity(tableName = "search_zones")
data class SearchZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: Long,
    val lat: Double,
    val lon: Double,
    val radiusMeters: Double,
    val score: Float,
    val reason: String?,
    val checked: Boolean,
) {
    fun toDomain() = SearchZone(id, caseId, lat, lon, radiusMeters, score, reason, checked)

    companion object {
        fun fromDomain(d: SearchZone) = SearchZoneEntity(
            d.id, d.caseId, d.lat, d.lon, d.radiusMeters, d.score, d.reason, d.checked,
        )
    }
}
