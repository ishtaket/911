package com.searchaid.domain.model

data class SocialSearchResult(
    val id: Long = 0,
    val caseId: Long,
    val platform: String,
    val profileName: String,
    val profileUrl: String,
    val handle: String?,
    val snippet: String?,
    val avatarUrl: String?,
    val matchType: SocialMatchType,
    val relevanceScore: Float = 0f,
    val status: SearchResultStatus = SearchResultStatus.NEW,
    val foundAt: Long = System.currentTimeMillis(),
)

enum class SocialMatchType {
    HANDLE_EXACT,
    HANDLE_PARTIAL,
    NAME_EXACT,
    NAME_PARTIAL,
    GROUP_MENTION,
}
