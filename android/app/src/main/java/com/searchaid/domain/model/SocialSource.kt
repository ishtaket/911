package com.searchaid.domain.model

data class SocialSource(
    val id: Long = 0,
    val personId: Long,
    val platform: String,
    val sourceType: String,
    val title: String?,
    val handleOrAlias: String?,
    val region: String?,
    val url: String?,
    val visibility: String?,
    val enabled: Boolean = true,
)
