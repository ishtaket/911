package com.searchaid.ui.feature_search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.SearchLead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LeadsListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun lead(
        id: Long = 1,
        type: LeadType = LeadType.MANUAL,
        status: LeadStatus = LeadStatus.NEW,
        confidence: Float = 0.8f,
        textSnippet: String = "Seen near the store",
    ) = SearchLead(
        id = id, caseId = 1L, type = type, platform = "Telegram",
        matchedValue = "photo match", textSnippet = textSnippet,
        possibleLocationName = "Store", lat = 55.75, lon = 37.61,
        timestamp = 1000L, confidence = confidence, status = status,
    )

    private fun content(
        state: LeadsListState,
        onShowAddDialog: () -> Unit = {},
        onConfirmLead: (Long) -> Unit = {},
        onRejectLead: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            LeadsListScreenContent(
                state = state,
                onBack = {},
                onShowAddDialog = onShowAddDialog,
                onDismissAddDialog = {},
                onConfirmLead = onConfirmLead,
                onRejectLead = onRejectLead,
                onTypeChange = {}, onPlatformChange = {}, onMatchedValueChange = {},
                onTextSnippetChange = {}, onLocationNameChange = {},
                onLatChange = {}, onLonChange = {}, onConfidenceChange = {},
                onSubmitLead = {},
            )
        }
    }

    @Test
    fun `empty state shows placeholder`() {
        content(LeadsListState(loading = false, leads = emptyList()))

        composeTestRule.onNodeWithText("No leads yet. Tap + to add a manual lead.")
            .assertIsDisplayed()
    }

    @Test
    fun `top bar shows Search Leads title`() {
        content(LeadsListState(loading = false))
        composeTestRule.onNodeWithText("Search Leads").assertIsDisplayed()
    }

    @Test
    fun `lead card shows type status and details`() {
        content(LeadsListState(loading = false, leads = listOf(lead())))

        composeTestRule.onNodeWithText("MANUAL").assertIsDisplayed()
        composeTestRule.onNodeWithText("NEW").assertIsDisplayed()
        composeTestRule.onNodeWithText("Platform: Telegram").assertIsDisplayed()
        composeTestRule.onNodeWithText("Seen near the store").assertIsDisplayed()
        composeTestRule.onNodeWithText("Location: Store").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confidence: 80%").assertIsDisplayed()
    }

    @Test
    fun `NEW lead shows confirm and reject buttons`() {
        content(LeadsListState(loading = false, leads = listOf(lead(status = LeadStatus.NEW))))

        composeTestRule.onNodeWithContentDescription("Confirm").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Reject").assertIsDisplayed()
    }

    @Test
    fun `CONFIRMED lead hides confirm and reject buttons`() {
        content(LeadsListState(loading = false, leads = listOf(lead(status = LeadStatus.CONFIRMED))))

        composeTestRule.onNodeWithText("CONFIRMED").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Confirm").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Reject").assertDoesNotExist()
    }

    @Test
    fun `confirm button triggers callback with lead id`() {
        var confirmedId: Long? = null
        content(
            LeadsListState(loading = false, leads = listOf(lead(id = 42))),
            onConfirmLead = { confirmedId = it },
        )

        composeTestRule.onNodeWithContentDescription("Confirm").performClick()
        assertEquals(42L, confirmedId)
    }

    @Test
    fun `reject button triggers callback with lead id`() {
        var rejectedId: Long? = null
        content(
            LeadsListState(loading = false, leads = listOf(lead(id = 42))),
            onRejectLead = { rejectedId = it },
        )

        composeTestRule.onNodeWithContentDescription("Reject").performClick()
        assertEquals(42L, rejectedId)
    }

    @Test
    fun `FAB triggers add dialog callback`() {
        var addClicked = false
        content(
            LeadsListState(loading = false),
            onShowAddDialog = { addClicked = true },
        )

        composeTestRule.onNodeWithContentDescription("Add lead").performClick()
        assertTrue(addClicked)
    }

    @Test
    fun `add dialog shows when state flag is true`() {
        content(LeadsListState(loading = false, showAddDialog = true))

        composeTestRule.onNodeWithText("Add Lead").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `multiple leads are rendered`() {
        content(LeadsListState(
            loading = false,
            leads = listOf(
                lead(id = 1, textSnippet = "First lead"),
                lead(id = 2, textSnippet = "Second lead", type = LeadType.SOCIAL),
            ),
        ))

        composeTestRule.onNodeWithText("First lead").assertIsDisplayed()
        composeTestRule.onNodeWithText("Second lead").performScrollTo().assertIsDisplayed()
    }
}
