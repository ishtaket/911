package com.pca.assistant.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema follows the spec §4. SQLCipher encryption is applied at the DB-instance
 * level (see [com.pca.assistant.data.db.PcaDatabase]). Token→original mappings
 * for anonymization are NEVER stored: they live only in RAM during the LLM
 * request (see [com.pca.assistant.anonymizer.Anonymizer]).
 */

@Entity(tableName = "owner_profile")
data class OwnerProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val voiceEmbedding: ByteArray?,
    val preferencesJson: String,
    val l3Summary: String,
    val preferredLanguage: String,
    val updatedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OwnerProfileEntity) return false
        return id == other.id && name == other.name && updatedAt == other.updatedAt
    }
    override fun hashCode(): Int = id
}

@Entity(
    tableName = "transcripts",
    indices = [Index("ts"), Index("isOwner")]
)
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val text: String,
    val textAnonymized: String,
    val speakerId: String?,
    val speakerScore: Float,
    val isOwner: Boolean,
    val locationLat: Double?,
    val locationLng: Double?,
    val placeLabel: String?,
    val confidence: Float,
    val language: String?,
)

@Entity(
    tableName = "windows",
    indices = [Index("startTs")]
)
data class WindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTs: Long,
    val endTs: Long,
    val rawContextJson: String,
    val anonymizedContextJson: String,
    val sentToLlm: Boolean,
    val llmProvider: String?,
    val llmResponseJson: String?,
    val latencyMs: Long?,
    val skipped: Boolean = false,
    val skipReason: String? = null,
)

@Entity(
    tableName = "interventions",
    indices = [Index("windowId"), Index("ts")]
)
data class InterventionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val windowId: Long,
    val ts: Long,
    val advice: String,
    val urgency: Int,
    val reason: String,
    /** "useful" | "no" | "not_now" | null */
    val userFeedback: String?,
    val shownAt: Long?,
)

@Entity(
    tableName = "open_threads",
    indices = [Index("status"), Index("openedAt")]
)
data class OpenThreadEntity(
    @PrimaryKey val id: String,
    val topic: String,
    val openedAt: Long,
    val openedInWindowId: Long,
    val context: String,
    /** Unix millis or null */
    val due: Long?,
    /** open | closed | snoozed */
    val status: String,
    val lastMentionedAt: Long,
    val relatedPeopleJson: String,
    val relatedLocationsJson: String,
)

@Entity(
    tableName = "hour_summaries",
    indices = [Index("hourStart", unique = true)]
)
data class HourSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hourStart: Long,
    val hourEnd: Long,
    val summary: String,
    val memoryNotesJson: String,
    val locationsJson: String,
    val peopleJson: String,
)

@Entity(
    tableName = "day_summaries",
    indices = [Index("date", unique = true)]
)
data class DaySummaryEntity(
    /** YYYY-MM-DD */
    @PrimaryKey val date: String,
    val narrative: String,
    val eventsJson: String,
    val peopleJson: String,
    val placesJson: String,
    val openQuestionsJson: String,
    val mood: String?,
    val updatedAt: Long,
)

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val lat: Double,
    val lng: Double,
    /** metres */
    val radius: Float,
    /** "home" | "work" | "gym" | ... */
    val semanticTag: String,
    val pauseRecording: Boolean = false,
)

@Entity(
    tableName = "llm_health",
    indices = [Index("ts"), Index("provider")]
)
data class LlmHealthEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val provider: String,
    val success: Boolean,
    val errorCode: String?,
    val latencyMs: Long,
)
