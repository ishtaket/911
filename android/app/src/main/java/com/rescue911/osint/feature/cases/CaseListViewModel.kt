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
    private val _cases = MutableStateFlow<List<MissingCase>?>(null)
    val cases: StateFlow<List<MissingCase>?> = _cases.asStateFlow()

    init { refresh() }

    /** Re-fetch the list. The repository's cases() is a cold flow that
     *  emits once; the screen re-invokes this on each re-entry so a case
     *  created via POST /v1/cases shows up immediately when the user pops
     *  back from CaseDetail. */
    fun refresh() {
        viewModelScope.launch {
            repository.cases().collect { _cases.value = it }
        }
    }
}
