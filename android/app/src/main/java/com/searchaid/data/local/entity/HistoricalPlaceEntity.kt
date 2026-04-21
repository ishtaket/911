package com.searchaid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.searchaid.domain.model.HistoricalPlace

@Entity(tableName = "historical_places")
data class HistoricalPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val title: String,
    val lat: Double,
    val lon: Double,
    val source: String?,
    val note: String?,
) {
    fun toDomain() = HistoricalPlace(id, personId, title, lat, lon, source, note)

    companion object {
        fun fromDomain(d: HistoricalPlace) = HistoricalPlaceEntity(
            d.id, d.personId, d.title, d.lat, d.lon, d.source, d.note,
        )
    }
}
