package com.searchaid.domain.model

data class WebSearchResult(
    val id: Long = 0,
    val caseId: Long,
    val query: String,
    val title: String,
    val snippet: String,
    val url: String,
    val source: String,
    val relevanceScore: Float = 0f,
    val status: SearchResultStatus = SearchResultStatus.NEW,
    val foundAt: Long = System.currentTimeMillis(),
)

enum class SearchResultStatus {
    NEW,
    REVIEWED,
    PROMOTED_TO_LEAD,
    DISMISSED,
}
