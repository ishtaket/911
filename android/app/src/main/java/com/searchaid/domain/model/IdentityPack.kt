package com.searchaid.domain.model

/**
 * Aggregated identity information for a missing person, built from
 * PersonProfile + SocialSources. Used by search modules to generate
 * web and social network queries.
 */
data class IdentityPack(
    val personId: Long,
    val primaryName: String,
    val nameVariants: List<String>,
    val aliases: List<String>,
    val handles: List<String>,
    val emails: List<String>,
    val phones: List<String>,
    val age: Int?,
    val region: String?,
    val searchQueries: List<String> = emptyList(),
)
