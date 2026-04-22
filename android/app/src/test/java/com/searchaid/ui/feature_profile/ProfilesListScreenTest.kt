package com.searchaid.ui.feature_profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.searchaid.domain.model.PersonProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ProfilesListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun profile(
        id: Long,
        name: String,
        age: Int? = 78,
        condition: String? = "Alzheimer's",
    ) = PersonProfile(
        id = id, name = name, age = age, photoUri = null,
        condition = condition, distinguishingFeatures = null,
        habits = null, knownLocations = null,
        familyNotes = null, createdAt = 1000L, updatedAt = 2000L,
    )

    @Test
    fun `empty state shows placeholder text`() {
        composeTestRule.setContent {
            ProfilesListScreenContent(
                profiles = emptyList(),
                onProfileClick = {},
                onCreateClick = {},
            )
        }

        composeTestRule.onNodeWithText("No profiles yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap + to create a person profile").assertIsDisplayed()
    }

    @Test
    fun `profiles list shows profile items`() {
        composeTestRule.setContent {
            ProfilesListScreenContent(
                profiles = listOf(
                    profile(1, "Ivan Petrov"),
                    profile(2, "Maria Ivanova", age = 82, condition = "Dementia"),
                ),
                onProfileClick = {},
                onCreateClick = {},
            )
        }

        composeTestRule.onNodeWithText("Ivan Petrov").assertIsDisplayed()
        composeTestRule.onNodeWithText("Maria Ivanova").assertIsDisplayed()
        composeTestRule.onNodeWithText("82 y.o. · Dementia").assertIsDisplayed()
    }

    @Test
    fun `clicking profile item triggers callback`() {
        var clickedId: Long? = null
        composeTestRule.setContent {
            ProfilesListScreenContent(
                profiles = listOf(profile(42, "Ivan Petrov")),
                onProfileClick = { clickedId = it },
                onCreateClick = {},
            )
        }

        composeTestRule.onNodeWithText("Ivan Petrov").performClick()
        assertEquals(42L, clickedId)
    }

    @Test
    fun `FAB triggers create callback`() {
        var createClicked = false
        composeTestRule.setContent {
            ProfilesListScreenContent(
                profiles = emptyList(),
                onProfileClick = {},
                onCreateClick = { createClicked = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("Create profile").performClick()
        assertTrue(createClicked)
    }

    @Test
    fun `top bar shows Profiles title`() {
        composeTestRule.setContent {
            ProfilesListScreenContent(
                profiles = emptyList(),
                onProfileClick = {},
                onCreateClick = {},
            )
        }

        composeTestRule.onNodeWithText("Profiles").assertIsDisplayed()
    }
}
