package com.searchaid.ui.feature_profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.usecase.DeletePersonProfileUseCase
import com.searchaid.domain.usecase.GetAllProfilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesListViewModel @Inject constructor(
    getAllProfiles: GetAllProfilesUseCase,
    private val deleteProfile: DeletePersonProfileUseCase,
) : ViewModel() {

    val profiles: StateFlow<List<PersonProfile>> = getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onDelete(id: Long) {
        viewModelScope.launch { deleteProfile(id) }
    }
}
