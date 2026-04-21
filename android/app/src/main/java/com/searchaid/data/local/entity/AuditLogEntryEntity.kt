package com.searchaid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.searchaid.domain.model.AuditLogEntry

@Entity(tableName = "audit_log")
data class AuditLogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: Long?,
    val action: String,
    val details: String?,
    val operatorId: String?,
    val timestamp: Long,
) {
    fun toDomain() = AuditLogEntry(id, caseId, action, details, operatorId, timestamp)

    companion object {
        fun fromDomain(d: AuditLogEntry) = AuditLogEntryEntity(
            d.id, d.caseId, d.action, d.details, d.operatorId, d.timestamp,
        )
    }
}
