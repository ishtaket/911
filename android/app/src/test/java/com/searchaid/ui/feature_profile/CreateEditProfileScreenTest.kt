package com.searchaid.ui.feature_profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CreateEditProfileScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun content(
        state: ProfileFormState = ProfileFormState(),
        onBack: () -> Unit = {},
        onSave: () -> Unit = {},
        onNameChange: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            CreateEditProfileContent(
                state = state,
                onBack = onBack, onSaved = {}, onSave = onSave,
                onNameChange = onNameChange, onAgeChange = {}, onConditionChange = {},
                onFeaturesChange = {}, onHabitsChange = {}, onLocationsChange = {},
                onAliasesChange = {}, onNicknamesChange = {}, onEmailsChange = {},
                onPhonesChange = {}, onFamilyNotesChange = {},
            )
        }
    }

    @Test
    fun `new profile shows correct title and sections`() {
        content()

        composeTestRule.onNodeWithText("New Profile").assertIsDisplayed()
        composeTestRule.onNodeWithText("Basic Info").assertIsDisplayed()
        composeTestRule.onNodeWithText("Behavior").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Identity").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Family").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `edit mode shows Edit Profile title`() {
        content(state = ProfileFormState(isEdit = true, name = "Ivan"))
        composeTestRule.onNodeWithText("Edit Profile").assertIsDisplayed()
    }

    @Test
    fun `form fields are displayed`() {
        content()

        composeTestRule.onNodeWithText("Full name *").assertIsDisplayed()
        composeTestRule.onNodeWithText("Age").assertIsDisplayed()
        composeTestRule.onNodeWithText("Condition / Diagnosis").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `save FAB triggers save callback`() {
        var saveClicked = false
        content(onSave = { saveClicked = true })

        composeTestRule.onNodeWithContentDescription("Save").performClick()
        assertTrue(saveClicked)
    }

    @Test
    fun `back button triggers back callback`() {
        var backClicked = false
        content(onBack = { backClicked = true })

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun `name input triggers onNameChange`() {
        var captured = ""
        content(onNameChange = { captured = it })

        composeTestRule.onNodeWithText("Full name *").performTextInput("Ivan")
        assertTrue(captured.isNotEmpty())
    }

    @Test
    fun `pre-filled state shows values in form`() {
        content(state = ProfileFormState(
            name = "Ivan Petrov",
            age = "78",
            condition = "Alzheimer's",
            isEdit = true,
        ))

        composeTestRule.onNodeWithText("Ivan Petrov").assertIsDisplayed()
        composeTestRule.onNodeWithText("78").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alzheimer's").performScrollTo().assertIsDisplayed()
    }
}
