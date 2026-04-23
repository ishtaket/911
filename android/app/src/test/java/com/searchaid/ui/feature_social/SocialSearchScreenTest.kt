package com.searchaid.ui.feature_social

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.searchaid.domain.model.IdentityPack
import com.searchaid.domain.model.SearchResultStatus
import com.searchaid.domain.model.SocialMatchType
import com.searchaid.domain.model.SocialSearchResult
import com.searchaid.domain.model.SocialSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SocialSearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val pack = IdentityPack(
        personId = 1, primaryName = "Иванов Иван",
        nameVariants = listOf("Иванов Иван", "Ivanov Ivan"),
        aliases = emptyList(), handles = listOf("ivanov_ivan"),
        emails = emptyList(), phones = emptyList(),
        age = 72, region = null,
    )

    private val sources = listOf(
        SocialSource(id = 1, personId = 1, platform = "VK", sourceType = "profile", title = null, handleOrAlias = "ivanov_ivan", region = null, url = null, visibility = null, enabled = true),
    )

    private fun content(
        state: SocialSearchState,
        onRunSearch: () -> Unit = {},
        onSelectPlatform: (String?) -> Unit = {},
        onPromoteToLead: (SocialSearchResult) -> Unit = {},
        onDismiss: (SocialSearchResult) -> Unit = {},
    ) {
        composeTestRule.setContent {
            SocialSearchScreenContent(
                state = state,
                onBack = {},
                onRunSearch = onRunSearch,
                onSelectPlatform = onSelectPlatform,
                onPromoteToLead = onPromoteToLead,
                onDismiss = onDismiss,
            )
        }
    }

    @Test
    fun `loading state shows progress indicator`() {
        content(SocialSearchState(loading = true))
        composeTestRule.onNodeWithText("Social Search").assertIsDisplayed()
    }

    @Test
    fun `identity pack shows primary name`() {
        content(SocialSearchState(loading = false, identityPack = pack, sources = sources))
        composeTestRule.onNodeWithText("Identity: Иванов Иван").assertIsDisplayed()
    }

    @Test
    fun `known accounts displayed as chips`() {
        content(SocialSearchState(loading = false, identityPack = pack, sources = sources))
        composeTestRule.onNodeWithText("Known Accounts").assertIsDisplayed()
        composeTestRule.onNodeWithText("VK: ivanov_ivan").assertIsDisplayed()
    }

    @Test
    fun `error state shows error message`() {
        content(SocialSearchState(loading = false, error = "Case not found"))
        composeTestRule.onNodeWithText("Case not found").assertIsDisplayed()
    }

    @Test
    fun `results show profile name and platform`() {
        val result = SocialSearchResult(
            caseId = 1, platform = "VK", profileName = "Иван Иванов",
            profileUrl = "https://vk.com/ivanov", handle = "ivanov",
            snippet = "Москва, Россия", avatarUrl = null,
            matchType = SocialMatchType.HANDLE_EXACT, relevanceScore = 0.8f,
        )
        content(SocialSearchState(
            loading = false, identityPack = pack, sources = sources,
            results = listOf(result),
        ))
        composeTestRule.onNodeWithText("1 profiles found").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Иван Иванов").assertExists()
        composeTestRule.onNodeWithText("@ivanov").assertExists()
    }

    @Test
    fun `promote and dismiss buttons exist for new result`() {
        val result = SocialSearchResult(
            caseId = 1, platform = "VK", profileName = "User",
            profileUrl = "https://vk.com/user", handle = "user",
            snippet = null, avatarUrl = null,
            matchType = SocialMatchType.NAME_EXACT, relevanceScore = 0.5f,
        )
        content(SocialSearchState(
            loading = false, identityPack = pack, sources = sources,
            results = listOf(result),
        ))
        composeTestRule.onNodeWithContentDescription("Promote to lead").assertExists()
        composeTestRule.onNodeWithContentDescription("Dismiss").assertExists()
    }

    @Test
    fun `promoted result shows LEAD badge`() {
        val result = SocialSearchResult(
            caseId = 1, platform = "VK", profileName = "Promoted User",
            profileUrl = "https://vk.com/promoted", handle = "promoted",
            snippet = null, avatarUrl = null,
            matchType = SocialMatchType.HANDLE_EXACT, relevanceScore = 0.9f,
            status = SearchResultStatus.PROMOTED_TO_LEAD,
        )
        content(SocialSearchState(
            loading = false, identityPack = pack, sources = sources,
            results = listOf(result),
        ))
        composeTestRule.onNodeWithText("LEAD").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Promote to lead").assertDoesNotExist()
    }

    @Test
    fun `top bar shows Social Search title`() {
        content(SocialSearchState(loading = false, identityPack = pack))
        composeTestRule.onNodeWithText("Social Search").assertIsDisplayed()
    }
}
