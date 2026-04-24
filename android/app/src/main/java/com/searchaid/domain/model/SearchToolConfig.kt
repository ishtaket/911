package com.searchaid.domain.model

/**
 * User preferences for which search tools are enabled and API configuration.
 */
data class SearchToolConfig(
    val onboardingCompleted: Boolean = false,
    val googleWebEnabled: Boolean = true,
    val googleImagesEnabled: Boolean = true,
    val facebookEnabled: Boolean = true,
    val instagramEnabled: Boolean = true,
    val tiktokEnabled: Boolean = true,
    val archivesEnabled: Boolean = true,
    val googleApiKey: String = "",
    val googleCx: String = "",
) {
    val isApiConfigured: Boolean
        get() = googleApiKey.isNotBlank() && googleCx.isNotBlank()

    val enabledPlatforms: List<String>
        get() = buildList {
            if (facebookEnabled) add("Facebook")
            if (instagramEnabled) add("Instagram")
            if (tiktokEnabled) add("TikTok")
        }

    /** Platforms explicitly disabled by user. Unknown platforms are always allowed. */
    val disabledPlatforms: Set<String>
        get() = buildSet {
            if (!facebookEnabled) add("Facebook")
            if (!instagramEnabled) add("Instagram")
            if (!tiktokEnabled) add("TikTok")
        }

    fun isPlatformEnabled(platform: String): Boolean =
        platform !in disabledPlatforms
}
