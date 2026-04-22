package com.searchaid.ui.feature_witness

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.WitnessReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WitnessReportsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun report(
        id: Long = 1,
        status: ReportStatus = ReportStatus.NEW,
        confidence: Float = 0.7f,
        text: String = "Saw person near river",
        sourceName: String? = "John",
    ) = WitnessReport(
        id = id, caseId = 1L, sourceName = sourceName, sourceType = "Eyewitness",
        text = text, timestamp = 1000L, possibleLocationName = "River bank",
        lat = 55.75, lon = 37.61, confidence = confidence, status = status,
    )

    private fun content(
        state: WitnessReportsState,
        onShowAddDialog: () -> Unit = {},
        onVerifyReport: (Long) -> Unit = {},
        onRejectReport: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            WitnessReportsScreenContent(
                state = state,
                onBack = {},
                onShowAddDialog = onShowAddDialog,
                onDismissAddDialog = {},
                onVerifyReport = onVerifyReport,
                onRejectReport = onRejectReport,
                onSourceNameChange = {}, onSourceTypeChange = {},
                onTextChange = {}, onLocationNameChange = {},
                onLatChange = {}, onLonChange = {}, onConfidenceChange = {},
                onSubmitReport = {},
            )
        }
    }

    @Test
    fun `empty state shows placeholder`() {
        content(WitnessReportsState(loading = false, reports = emptyList()))

        composeTestRule.onNodeWithText("No witness reports yet. Tap + to add one.")
            .assertIsDisplayed()
    }

    @Test
    fun `top bar shows Witness Reports title`() {
        content(WitnessReportsState(loading = false))
        composeTestRule.onNodeWithText("Witness Reports").assertIsDisplayed()
    }

    @Test
    fun `report card shows source text location confidence and status`() {
        content(WitnessReportsState(loading = false, reports = listOf(report())))

        composeTestRule.onNodeWithText("Source: John").assertIsDisplayed()
        composeTestRule.onNodeWithText("Type: Eyewitness").assertIsDisplayed()
        composeTestRule.onNodeWithText("NEW").assertIsDisplayed()
        composeTestRule.onNodeWithText("Saw person near river").assertIsDisplayed()
        composeTestRule.onNodeWithText("Location: River bank").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confidence: 70%").assertIsDisplayed()
    }

    @Test
    fun `NEW report shows verify and reject buttons`() {
        content(WitnessReportsState(loading = false, reports = listOf(report(status = ReportStatus.NEW))))

        composeTestRule.onNodeWithContentDescription("Verify").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Reject").assertIsDisplayed()
    }

    @Test
    fun `VERIFIED report hides verify and reject buttons`() {
        content(WitnessReportsState(loading = false, reports = listOf(report(status = ReportStatus.VERIFIED))))

        composeTestRule.onNodeWithText("VERIFIED").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Verify").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Reject").assertDoesNotExist()
    }

    @Test
    fun `verify button triggers callback with report id`() {
        var verifiedId: Long? = null
        content(
            WitnessReportsState(loading = false, reports = listOf(report(id = 42))),
            onVerifyReport = { verifiedId = it },
        )

        composeTestRule.onNodeWithContentDescription("Verify").performClick()
        assertEquals(42L, verifiedId)
    }

    @Test
    fun `reject button triggers callback with report id`() {
        var rejectedId: Long? = null
        content(
            WitnessReportsState(loading = false, reports = listOf(report(id = 42))),
            onRejectReport = { rejectedId = it },
        )

        composeTestRule.onNodeWithContentDescription("Reject").performClick()
        assertEquals(42L, rejectedId)
    }

    @Test
    fun `FAB triggers add dialog callback`() {
        var addClicked = false
        content(
            WitnessReportsState(loading = false),
            onShowAddDialog = { addClicked = true },
        )

        composeTestRule.onNodeWithContentDescription("Add report").performClick()
        assertTrue(addClicked)
    }

    @Test
    fun `add dialog shows when state flag is true`() {
        content(WitnessReportsState(loading = false, showAddDialog = true))

        composeTestRule.onNodeWithText("Add Report").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `multiple reports are rendered`() {
        content(WitnessReportsState(
            loading = false,
            reports = listOf(
                report(id = 1, text = "First report"),
                report(id = 2, text = "Second report", sourceName = "Anna"),
            ),
        ))

        composeTestRule.onNodeWithText("First report").assertIsDisplayed()
        composeTestRule.onNodeWithText("Second report").performScrollTo().assertIsDisplayed()
    }
}
