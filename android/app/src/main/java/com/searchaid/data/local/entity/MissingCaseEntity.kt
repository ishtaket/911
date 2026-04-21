package com.searchaid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase

@Entity(tableName = "missing_cases")
data class MissingCaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val status: String,
    val createdAt: Long,
    val lastSeenTime: Long?,
    val lastSeenLocationName: String?,
    val lastSeenLat: Double?,
    val lastSeenLon: Double?,
    val clothesDescription: String?,
    val notes: String?,
    val operatorId: String?,
) {
    fun toDomain() = MissingCase(
        id = id, personId = personId,
        status = CaseStatus.valueOf(status),
        createdAt = createdAt, lastSeenTime = lastSeenTime,
        lastSeenLocationName = lastSeenLocationName,
        lastSeenLat = lastSeenLat, lastSeenLon = lastSeenLon,
        clothesDescription = clothesDescription, notes = notes,
        operatorId = operatorId,
    )

    companion object {
        fun fromDomain(d: MissingCase) = MissingCaseEntity(
            id = d.id, personId = d.personId, status = d.status.name,
            createdAt = d.createdAt, lastSeenTime = d.lastSeenTime,
            lastSeenLocationName = d.lastSeenLocationName,
            lastSeenLat = d.lastSeenLat, lastSeenLon = d.lastSeenLon,
            clothesDescription = d.clothesDescription, notes = d.notes,
            operatorId = d.operatorId,
        )
    }
}
