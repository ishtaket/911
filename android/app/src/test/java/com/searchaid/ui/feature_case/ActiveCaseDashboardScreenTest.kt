package com.searchaid.ui.feature_case

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.PersonProfile
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ActiveCaseDashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testProfile = PersonProfile(
        id = 42, name = "Ivan Petrov", age = 78, photoUri = null,
        condition = "Alzheimer's", distinguishingFeatures = "Scar on left hand",
        habits = null, knownLocations = null,
        familyNotes = null, createdAt = 1000L, updatedAt = 2000L,
    )

    private val testCase = MissingCase(
        id = 10, personId = 42, status = CaseStatus.ACTIVE,
        createdAt = 1713800000000L, lastSeenTime = 1713790000000L,
        lastSeenLocationName = "Central Park",
        lastSeenLat = 55.75, lastSeenLon = 37.61,
        clothesDescription = "Blue jacket", notes = "Last seen walking east",
        operatorId = null,
    )

    private fun content(
        state: CaseDashboardState,
        onMarkFound: () -> Unit = {},
        onCloseCase: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ActiveCaseDashboardContent(
                state = state,
                caseId = 10L,
                onBack = {}, onOpenMap = {}, onOpenLeads = {},
                onOpenWitness = {}, onOpenOutreach = {}, onOpenAudit = {},
                onMarkFound = onMarkFound, onCloseCase = onCloseCase,
            )
        }
    }

    @Test
    fun `loading state shows progress indicator`() {
        content(CaseDashboardState(loading = true))
        composeTestRule.onNodeWithText("Case Info").assertDoesNotExist()
    }

    @Test
    fun `case not found shows message`() {
        content(CaseDashboardState(loading = false, case_ = null))
        composeTestRule.onNodeWithText("Case not found").assertIsDisplayed()
    }

    @Test
    fun `active case shows person info and case details`() {
        content(CaseDashboardState(case_ = testCase, person = testProfile, loading = false))

        composeTestRule.onNodeWithText("Ivan Petrov").assertIsDisplayed()
        composeTestRule.onNodeWithText("ACTIVE CASE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Case Info").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Location: Central Park").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Clothing: Blue jacket").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `active case shows action buttons`() {
        content(CaseDashboardState(case_ = testCase, person = testProfile, loading = false))

        composeTestRule.onNodeWithText("Search Map").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Search Leads").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Witness Reports").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Outreach").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Audit Log").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `active case shows FOUND and Close buttons`() {
        content(CaseDashboardState(case_ = testCase, person = testProfile, loading = false))

        composeTestRule.onNodeWithText("FOUND").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Close Case").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `FOUND button triggers callback`() {
        var foundClicked = false
        content(
            CaseDashboardState(case_ = testCase, person = testProfile, loading = false),
            onMarkFound = { foundClicked = true },
        )

        composeTestRule.onNodeWithText("FOUND").performScrollTo().performClick()
        assertTrue(foundClicked)
    }

    @Test
    fun `Close Case button triggers callback`() {
        var closeClicked = false
        content(
            CaseDashboardState(case_ = testCase, person = testProfile, loading = false),
            onCloseCase = { closeClicked = true },
        )

        composeTestRule.onNodeWithText("Close Case").performScrollTo().performClick()
        assertTrue(closeClicked)
    }

    @Test
    fun `found case hides status control buttons`() {
        val foundCase = testCase.copy(status = CaseStatus.FOUND)
        content(CaseDashboardState(case_ = foundCase, person = testProfile, loading = false))

        composeTestRule.onNodeWithText("Close Case").assertDoesNotExist()
    }

    @Test
    fun `person distinguishing features are shown`() {
        content(CaseDashboardState(case_ = testCase, person = testProfile, loading = false))

        composeTestRule.onNodeWithText("Features: Scar on left hand").performScrollTo().assertIsDisplayed()
    }
}
