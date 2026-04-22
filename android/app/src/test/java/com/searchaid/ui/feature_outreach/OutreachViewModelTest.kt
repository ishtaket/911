package com.searchaid.ui.feature_outreach

import androidx.lifecycle.SavedStateHandle
import com.searchaid.MainDispatcherRule
import com.searchaid.domain.model.OutreachMessage
import com.searchaid.domain.model.OutreachStatus
import com.searchaid.domain.usecase.GetOutreachMessagesByCaseUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.SendOutreachMessageUseCase
import com.searchaid.domain.usecase.UpdateOutreachStatusUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OutreachViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getMessages = mockk<GetOutreachMessagesByCaseUseCase>()
    private val sendMessage = mockk<SendOutreachMessageUseCase>(relaxed = true)
    private val updateStatus = mockk<UpdateOutreachStatusUseCase>(relaxed = true)
    private val logAction = mockk<LogActionUseCase>(relaxed = true)

    private val testMessage = OutreachMessage(
        id = 1, caseId = 10, channel = "Telegram", recipient = "Search Group",
        messageText = "Looking for Ivan", sentAt = 1000L, status = OutreachStatus.SENT,
    )

    private fun createViewModel(): OutreachViewModel {
        every { getMessages(10L) } returns flowOf(listOf(testMessage))
        return OutreachViewModel(
            SavedStateHandle(mapOf("caseId" to 10L)),
            getMessages, sendMessage, updateStatus, logAction,
        )
    }

    @Test
    fun `init loads messages from repository`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.messages.size)
        assertEquals("Telegram", vm.state.value.messages[0].channel)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `showAddDialog opens dialog`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.showAddDialog()

        assertTrue(vm.state.value.showAddDialog)
    }

    @Test
    fun `submitMessage calls sendMessage and logs action`() = runTest {
        coEvery { sendMessage(any()) } returns 5L
        val vm = createViewModel()
        advanceUntilIdle()

        vm.showAddDialog()
        vm.onChannelChange("WhatsApp")
        vm.onRecipientChange("Volunteers")
        vm.onMessageTextChange("Please help find Ivan")
        vm.submitMessage()
        advanceUntilIdle()

        coVerify { sendMessage(match { it.channel == "WhatsApp" && it.recipient == "Volunteers" }) }
        coVerify { logAction("OUTREACH_SENT", caseId = 10L, details = any()) }
        assertFalse(vm.state.value.showAddDialog)
    }

    @Test
    fun `markResponded updates status and logs`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.markResponded(1L)
        advanceUntilIdle()

        coVerify { updateStatus(1L, OutreachStatus.RESPONDED) }
        coVerify { logAction("OUTREACH_RESPONDED", caseId = 10L, details = any()) }
    }

    @Test
    fun `submitMessage with blank fields does nothing`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.showAddDialog()
        vm.onChannelChange("")
        vm.submitMessage()
        advanceUntilIdle()

        coVerify(exactly = 0) { sendMessage(any()) }
        assertTrue(vm.state.value.showAddDialog) // dialog stays open
    }
}
