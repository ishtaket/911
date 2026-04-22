package com.searchaid.ui.feature_outreach

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.OutreachMessage
import com.searchaid.domain.model.OutreachStatus
import com.searchaid.domain.usecase.GetOutreachMessagesByCaseUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.SendOutreachMessageUseCase
import com.searchaid.domain.usecase.UpdateOutreachStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OutreachState(
    val messages: List<OutreachMessage> = emptyList(),
    val loading: Boolean = true,
    val showAddDialog: Boolean = false,
    val addForm: AddOutreachForm = AddOutreachForm(),
)

data class AddOutreachForm(
    val channel: String = "",
    val recipient: String = "",
    val messageText: String = "",
)

@HiltViewModel
class OutreachViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMessages: GetOutreachMessagesByCaseUseCase,
    private val sendMessage: SendOutreachMessageUseCase,
    private val updateStatus: UpdateOutreachStatusUseCase,
    private val logAction: LogActionUseCase,
) : ViewModel() {

    val caseId: Long = savedStateHandle["caseId"] ?: -1L

    private val _state = MutableStateFlow(OutreachState())
    val state: StateFlow<OutreachState> = _state

    init {
        viewModelScope.launch {
            getMessages(caseId).collect { list ->
                _state.update { it.copy(messages = list, loading = false) }
            }
        }
    }

    fun showAddDialog() {
        _state.update { it.copy(showAddDialog = true, addForm = AddOutreachForm()) }
    }

    fun dismissAddDialog() {
        _state.update { it.copy(showAddDialog = false) }
    }

    fun onChannelChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(channel = v)) }
    }

    fun onRecipientChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(recipient = v)) }
    }

    fun onMessageTextChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(messageText = v)) }
    }

    fun submitMessage() {
        val f = _state.value.addForm
        if (f.channel.isBlank() || f.recipient.isBlank() || f.messageText.isBlank()) return
        viewModelScope.launch {
            val msg = OutreachMessage(
                caseId = caseId,
                channel = f.channel.trim(),
                recipient = f.recipient.trim(),
                messageText = f.messageText.trim(),
                sentAt = System.currentTimeMillis(),
                status = OutreachStatus.SENT,
            )
            val id = sendMessage(msg)
            logAction("OUTREACH_SENT", caseId = caseId, details = "Message #$id to ${f.recipient} via ${f.channel}")
            _state.update { it.copy(showAddDialog = false) }
        }
    }

    fun markResponded(messageId: Long) {
        viewModelScope.launch {
            updateStatus(messageId, OutreachStatus.RESPONDED)
            logAction("OUTREACH_RESPONDED", caseId = caseId, details = "Message #$messageId got response")
        }
    }
}
