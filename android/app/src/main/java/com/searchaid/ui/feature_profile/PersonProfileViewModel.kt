package com.searchaid.ui.feature_profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.HistoricalPlace
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.usecase.AddHistoricalPlaceUseCase
import com.searchaid.domain.usecase.DeleteHistoricalPlaceUseCase
import com.searchaid.domain.usecase.GetCasesByPersonUseCase
import com.searchaid.domain.usecase.GetHistoricalPlacesUseCase
import com.searchaid.domain.usecase.GetPersonProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getProfile: GetPersonProfileUseCase,
    getHistoricalPlaces: GetHistoricalPlacesUseCase,
    getCasesByPerson: GetCasesByPersonUseCase,
    private val addHistoricalPlace: AddHistoricalPlaceUseCase,
    private val deleteHistoricalPlace: DeleteHistoricalPlaceUseCase,
) : ViewModel() {

    val profileId: Long = savedStateHandle["profileId"] ?: -1L

    private val _profile = MutableStateFlow<PersonProfile?>(null)
    val profile: StateFlow<PersonProfile?> = _profile

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    val places: StateFlow<List<HistoricalPlace>> = getHistoricalPlaces(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cases: StateFlow<List<MissingCase>> = getCasesByPerson(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _profile.value = getProfile(profileId)
            _loading.value = false
        }
    }

    fun addPlace(title: String, lat: Double, lon: Double, source: String?, note: String?) {
        viewModelScope.launch {
            addHistoricalPlace(
                HistoricalPlace(
                    personId = profileId,
                    title = title,
                    lat = lat,
                    lon = lon,
                    source = source,
                    note = note,
                )
            )
        }
    }

    fun deletePlace(id: Long) {
        viewModelScope.launch { deleteHistoricalPlace(id) }
    }
}
