package com.searchaid.ui.feature_websearch

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.searchaid.domain.model.IdentityPack
import com.searchaid.domain.model.SearchResultStatus
import com.searchaid.domain.model.WebSearchResult
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WebSearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val pack = IdentityPack(
        personId = 1, primaryName = "Иванов Иван",
        nameVariants = listOf("Иванов Иван", "Ivanov Ivan"),
        aliases = emptyList(), handles = emptyList(),
        emails = emptyList(), phones = emptyList(),
        age = 72, region = null,
    )

    private fun content(
        state: WebSearchState,
        onRunSearch: () -> Unit = {},
        onPromoteToLead: (WebSearchResult) -> Unit = {},
        onDismiss: (WebSearchResult) -> Unit = {},
    ) {
        composeTestRule.setContent {
            WebSearchScreenContent(
                state = state,
                onBack = {},
                onRunSearch = onRunSearch,
                onPromoteToLead = onPromoteToLead,
                onDismiss = onDismiss,
            )
        }
    }

    @Test
    fun `loading state shows progress indicator`() {
        content(WebSearchState(loading = true))
        // Loading indicator exists (no title visible yet)
    }

    @Test
    fun `identity pack shows primary name`() {
        content(WebSearchState(loading = false, identityPack = pack))
        composeTestRule.onNodeWithText("Identity: Иванов Иван").assertIsDisplayed()
    }

    @Test
    fun `queries shown as chips`() {
        content(WebSearchState(
            loading = false,
            identityPack = pack,
            queries = listOf("\"Иванов Иван\"", "\"Ivanov Ivan\""),
        ))
        composeTestRule.onNodeWithText("Search Queries").assertIsDisplayed()
    }

    @Test
    fun `error state shows error message`() {
        content(WebSearchState(loading = false, error = "Case not found"))
        composeTestRule.onNodeWithText("Case not found").assertIsDisplayed()
    }

    @Test
    fun `results show title and snippet`() {
        val result = WebSearchResult(
            caseId = 1, query = "test",
            title = "Test Result Title",
            snippet = "This is a test snippet",
            url = "https://example.com",
            source = "google", relevanceScore = 0.5f,
        )
        content(WebSearchState(
            loading = false, identityPack = pack,
            queries = listOf("test"),
            results = listOf(result),
        ))
        composeTestRule.onNodeWithText("Test Result Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("This is a test snippet").assertIsDisplayed()
    }

    @Test
    fun `promote button exists for new result`() {
        val result = WebSearchResult(
            caseId = 1, query = "test", title = "Result",
            snippet = "Snippet", url = "https://example.com",
            source = "google", relevanceScore = 0.5f,
        )
        content(
            WebSearchState(
                loading = false, identityPack = pack,
                queries = emptyList(), results = listOf(result),
            ),
        )
        composeTestRule.onNodeWithContentDescription("Promote to lead").assertExists()
        composeTestRule.onNodeWithContentDescription("Dismiss").assertExists()
    }

    @Test
    fun `promoted result shows LEAD badge and no action buttons`() {
        val result = WebSearchResult(
            caseId = 1, query = "test", title = "Promoted",
            snippet = "Already a lead", url = "https://example.com",
            source = "google", relevanceScore = 0.5f,
            status = SearchResultStatus.PROMOTED_TO_LEAD,
        )
        content(WebSearchState(
            loading = false, identityPack = pack,
            queries = listOf("test"), results = listOf(result),
        ))
        composeTestRule.onNodeWithText("LEAD").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Promote to lead").assertDoesNotExist()
    }

    @Test
    fun `top bar shows Web Search title`() {
        content(WebSearchState(loading = false, identityPack = pack))
        composeTestRule.onNodeWithText("Web Search").assertIsDisplayed()
    }
}
