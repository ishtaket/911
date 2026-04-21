package com.searchaid.domain.model

data class HistoricalPlace(
    val id: Long = 0,
    val personId: Long,
    val title: String,
    val lat: Double,
    val lon: Double,
    val source: String?,
    val note: String?,
)
