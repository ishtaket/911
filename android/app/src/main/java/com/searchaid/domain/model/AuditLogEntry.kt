package com.searchaid.domain.model

data class AuditLogEntry(
    val id: Long = 0,
    val caseId: Long?,
    val action: String,
    val details: String?,
    val operatorId: String?,
    val timestamp: Long = System.currentTimeMillis(),
)
