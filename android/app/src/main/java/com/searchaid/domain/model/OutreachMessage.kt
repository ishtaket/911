package com.searchaid.domain.model

data class OutreachMessage(
    val id: Long = 0,
    val caseId: Long,
    val channel: String,
    val recipient: String,
    val messageText: String,
    val sentAt: Long? = null,
    val status: OutreachStatus = OutreachStatus.DRAFT,
)

enum class OutreachStatus {
    DRAFT,
    SENT,
    RESPONDED,
}
