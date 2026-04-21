package com.searchaid.ui.feature_profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.usecase.GetPersonProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getProfile: GetPersonProfileUseCase,
) : ViewModel() {

    private val profileId: Long = savedStateHandle["profileId"] ?: -1L

    private val _profile = MutableStateFlow<PersonProfile?>(null)
    val profile: StateFlow<PersonProfile?> = _profile

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    init {
        viewModelScope.launch {
            _profile.value = getProfile(profileId)
            _loading.value = false
        }
    }
}
