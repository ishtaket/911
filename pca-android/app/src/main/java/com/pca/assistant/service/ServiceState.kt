package com.pca.assistant.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ListeningState { STOPPED, LISTENING, PAUSED }

@Singleton
class ServiceState @Inject constructor() {
    private val _state = MutableStateFlow(ListeningState.STOPPED)
    val state: StateFlow<ListeningState> = _state

    fun update(s: ListeningState) {
        _state.value = s
    }
}
