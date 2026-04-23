package com.searchaid.domain.model

data class ArchiveResult(
    val originalUrl: String,
    val archiveUrl: String,
    val timestamp: String,
    val platform: String?,
    val status: SearchResultStatus = SearchResultStatus.NEW,
)
