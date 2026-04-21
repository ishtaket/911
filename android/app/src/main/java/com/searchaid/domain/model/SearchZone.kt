package com.searchaid.domain.model

data class SearchZone(
    val id: Long = 0,
    val caseId: Long,
    val lat: Double,
    val lon: Double,
    val radiusMeters: Double,
    val score: Float = 0f,
    val reason: String?,
    val checked: Boolean = false,
)
