package com.searchaid.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * Response models for Google Custom Search JSON API.
 * https://developers.google.com/custom-search/v1/reference/rest/v1/cse/list
 */
data class GoogleSearchResponse(
    val items: List<GoogleSearchItem>?,
    val searchInformation: SearchInformation?,
    val queries: Queries?,
)

data class GoogleSearchItem(
    val title: String?,
    val link: String?,
    val snippet: String?,
    val displayLink: String?,
    val pagemap: PageMap?,
    val image: ImageInfo?,
    val mime: String?,
)

data class PageMap(
    @SerializedName("cse_thumbnail")
    val cseThumbnail: List<Thumbnail>?,
    @SerializedName("cse_image")
    val cseImage: List<CseImage>?,
    val metatags: List<Map<String, String>>?,
)

data class Thumbnail(
    val src: String?,
    val width: String?,
    val height: String?,
)

data class CseImage(
    val src: String?,
)

data class ImageInfo(
    val contextLink: String?,
    val thumbnailLink: String?,
    val thumbnailHeight: Int?,
    val thumbnailWidth: Int?,
)

data class SearchInformation(
    val totalResults: String?,
    val searchTime: Double?,
)

data class Queries(
    val request: List<QueryInfo>?,
    val nextPage: List<QueryInfo>?,
)

data class QueryInfo(
    val totalResults: String?,
    val count: Int?,
    val startIndex: Int?,
)
