package com.searchaid.ui.feature_outreach

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.searchaid.domain.model.OutreachMessage
import com.searchaid.domain.model.OutreachStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OutreachScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun message(
        id: Long = 1,
        status: OutreachStatus = OutreachStatus.SENT,
        channel: String = "Telegram",
        recipient: String = "Search Group",
        text: String = "Looking for Ivan Petrov, last seen near park",
    ) = OutreachMessage(
        id = id, caseId = 1L, channel = channel, recipient = recipient,
        messageText = text, sentAt = 1000L, status = status,
    )

    private fun content(
        state: OutreachState,
        onShowAddDialog: () -> Unit = {},
        onMarkResponded: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            OutreachScreenContent(
                state = state,
                onBack = {},
                onShowAddDialog = onShowAddDialog,
                onDismissAddDialog = {},
                onMarkResponded = onMarkResponded,
                onChannelChange = {}, onRecipientChange = {},
                onMessageTextChange = {}, onSubmitMessage = {},
            )
        }
    }

    @Test
    fun `empty state shows placeholder`() {
        content(OutreachState(loading = false, messages = emptyList()))
        composeTestRule.onNodeWithText("No outreach messages yet. Tap + to send one.")
            .assertIsDisplayed()
    }

    @Test
    fun `top bar shows Outreach title`() {
        content(OutreachState(loading = false))
        composeTestRule.onNodeWithText("Outreach").assertIsDisplayed()
    }

    @Test
    fun `message card shows channel recipient and text`() {
        content(OutreachState(loading = false, messages = listOf(message())))

        composeTestRule.onNodeWithText("Telegram").assertIsDisplayed()
        composeTestRule.onNodeWithText("SENT").assertIsDisplayed()
        composeTestRule.onNodeWithText("To: Search Group").assertIsDisplayed()
        composeTestRule.onNodeWithText("Looking for Ivan Petrov, last seen near park").assertIsDisplayed()
    }

    @Test
    fun `SENT message shows mark responded button`() {
        content(OutreachState(loading = false, messages = listOf(message(status = OutreachStatus.SENT))))
        composeTestRule.onNodeWithContentDescription("Mark responded").assertIsDisplayed()
    }

    @Test
    fun `RESPONDED message hides mark responded button`() {
        content(OutreachState(loading = false, messages = listOf(message(status = OutreachStatus.RESPONDED))))
        composeTestRule.onNodeWithText("RESPONDED").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mark responded").assertDoesNotExist()
    }

    @Test
    fun `mark responded triggers callback`() {
        var respondedId: Long? = null
        content(
            OutreachState(loading = false, messages = listOf(message(id = 42))),
            onMarkResponded = { respondedId = it },
        )

        composeTestRule.onNodeWithContentDescription("Mark responded").performClick()
        assertEquals(42L, respondedId)
    }

    @Test
    fun `FAB triggers add dialog callback`() {
        var addClicked = false
        content(
            OutreachState(loading = false),
            onShowAddDialog = { addClicked = true },
        )

        composeTestRule.onNodeWithContentDescription("Add message").performClick()
        assertTrue(addClicked)
    }

    @Test
    fun `add dialog shows when state flag is true`() {
        content(OutreachState(loading = false, showAddDialog = true))

        composeTestRule.onNodeWithText("Send Message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Send").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `multiple messages are rendered`() {
        content(OutreachState(
            loading = false,
            messages = listOf(
                message(id = 1, text = "First message"),
                message(id = 2, text = "Second message", channel = "WhatsApp"),
            ),
        ))

        composeTestRule.onNodeWithText("First message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Second message").performScrollTo().assertIsDisplayed()
    }
}
