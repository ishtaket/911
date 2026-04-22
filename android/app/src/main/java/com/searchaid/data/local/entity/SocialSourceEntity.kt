package com.searchaid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.searchaid.domain.model.SocialSource

@Entity(tableName = "social_sources")
data class SocialSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val platform: String,
    val sourceType: String,
    val title: String?,
    val handleOrAlias: String?,
    val region: String?,
    val url: String?,
    val visibility: String?,
    val enabled: Boolean,
) {
    fun toDomain() = SocialSource(
        id, personId, platform, sourceType, title,
        handleOrAlias, region, url, visibility, enabled,
    )

    companion object {
        fun fromDomain(d: SocialSource) = SocialSourceEntity(
            d.id, d.personId, d.platform, d.sourceType, d.title,
            d.handleOrAlias, d.region, d.url, d.visibility, d.enabled,
        )
    }
}
