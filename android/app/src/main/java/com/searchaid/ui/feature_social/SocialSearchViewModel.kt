package com.searchaid.ui.feature_social

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.IdentityPack
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.model.SearchResultStatus
import com.searchaid.domain.model.SocialSearchResult
import com.searchaid.domain.model.SocialSource
import com.searchaid.domain.usecase.AddLeadUseCase
import com.searchaid.domain.usecase.BuildIdentityPackUseCase
import com.searchaid.domain.usecase.GetMissingCaseUseCase
import com.searchaid.domain.usecase.GetPersonProfileUseCase
import com.searchaid.domain.usecase.GetSocialSourcesByPersonUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.SearchSocialUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SocialSearchState(
    val loading: Boolean = true,
    val searching: Boolean = false,
    val hasSearched: Boolean = false,
    val identityPack: IdentityPack? = null,
    val sources: List<SocialSource> = emptyList(),
    val results: List<SocialSearchResult> = emptyList(),
    val selectedPlatform: String? = null,
    val error: String? = null,
) {
    val platforms: List<String>
        get() = results.map { it.platform }.distinct().sorted()

    val filteredResults: List<SocialSearchResult>
        get() = if (selectedPlatform == null) results
        else results.filter { it.platform == selectedPlatform }
}

@HiltViewModel
class SocialSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCase: GetMissingCaseUseCase,
    private val getProfile: GetPersonProfileUseCase,
    private val getSocialSources: GetSocialSourcesByPersonUseCase,
    private val buildIdentityPack: BuildIdentityPackUseCase,
    private val searchSocial: SearchSocialUseCase,
    private val addLead: AddLeadUseCase,
    private val logAction: LogActionUseCase,
) : ViewModel() {

    val caseId: Long = savedStateHandle["caseId"] ?: -1L

    private val _state = MutableStateFlow(SocialSearchState())
    val state: StateFlow<SocialSearchState> = _state

    init {
        viewModelScope.launch {
            val case_ = getCase(caseId)
            if (case_ == null) {
                _state.update { it.copy(loading = false, error = "Case not found") }
                return@launch
            }

            val profile = getProfile(case_.personId)
            if (profile == null) {
                _state.update { it.copy(loading = false, error = "Profile not found") }
                return@launch
            }

            val sources = getSocialSources(profile.id).first()
            val pack = buildIdentityPack(profile, sources)

            _state.update {
                it.copy(
                    loading = false,
                    identityPack = pack,
                    sources = sources,
                )
            }
        }
    }

    fun runSearch() {
        val pack = _state.value.identityPack ?: return
        _state.update { it.copy(searching = true, error = null) }

        viewModelScope.launch {
            try {
                val results = searchSocial(pack, _state.value.sources, caseId)
                _state.update { it.copy(searching = false, hasSearched = true, results = results) }
                logAction("SOCIAL_SEARCH", caseId = caseId, details = "Found ${results.size} profiles")
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, hasSearched = true, error = "Search failed. Check your connection and try again.") }
            }
        }
    }

    fun selectPlatform(platform: String?) {
        _state.update { it.copy(selectedPlatform = platform) }
    }

    fun promoteToLead(result: SocialSearchResult) {
        viewModelScope.launch {
            val lead = SearchLead(
                caseId = caseId,
                type = LeadType.SOCIAL,
                platform = result.platform,
                matchedValue = result.profileUrl,
                textSnippet = "${result.profileName}: ${result.snippet ?: result.handle ?: ""}".take(200),
                possibleLocationName = null,
                lat = null, lon = null,
                timestamp = result.foundAt,
                confidence = result.relevanceScore,
            )
            addLead(lead)
            logAction("LEAD_FROM_SOCIAL", caseId = caseId, details = "${result.platform}: ${result.profileUrl}")

            _state.update { state ->
                state.copy(
                    results = state.results.map {
                        if (it.profileUrl == result.profileUrl) it.copy(status = SearchResultStatus.PROMOTED_TO_LEAD)
                        else it
                    },
                )
            }
        }
    }

    fun dismissResult(result: SocialSearchResult) {
        _state.update { state ->
            state.copy(
                results = state.results.map {
                    if (it.profileUrl == result.profileUrl) it.copy(status = SearchResultStatus.DISMISSED)
                    else it
                },
            )
        }
    }
}
