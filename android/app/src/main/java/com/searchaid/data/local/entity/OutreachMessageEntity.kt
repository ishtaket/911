package com.searchaid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.searchaid.domain.model.OutreachMessage
import com.searchaid.domain.model.OutreachStatus

@Entity(tableName = "outreach_messages")
data class OutreachMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: Long,
    val channel: String,
    val recipient: String,
    val messageText: String,
    val sentAt: Long?,
    val status: String,
) {
    fun toDomain() = OutreachMessage(
        id, caseId, channel, recipient, messageText,
        sentAt, OutreachStatus.valueOf(status),
    )

    companion object {
        fun fromDomain(d: OutreachMessage) = OutreachMessageEntity(
            d.id, d.caseId, d.channel, d.recipient,
            d.messageText, d.sentAt, d.status.name,
        )
    }
}
