package com.rescue911.osint.feature.cases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescue911.osint.data.repository.Rescue911Repository
import com.rescue911.osint.domain.model.MissingCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CaseListViewModel @Inject constructor(
    private val repository: Rescue911Repository,
) : ViewModel() {
    private val _cases = MutableStateFlow<List<MissingCase>>(emptyList())
    val cases: StateFlow<List<MissingCase>> = _cases.asStateFlow()

    init {
        viewModelScope.launch {
            repository.cases().collect { _cases.value = it }
        }
    }
}
