package com.searchaid.ui.feature_websearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.ArchiveResult
import com.searchaid.domain.model.IdentityPack
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.model.SearchResultStatus
import com.searchaid.domain.model.WebSearchResult
import com.searchaid.domain.usecase.AddLeadUseCase
import com.searchaid.domain.usecase.BuildIdentityPackUseCase
import com.searchaid.domain.usecase.GenerateSearchQueriesUseCase
import com.searchaid.domain.usecase.GetPersonProfileUseCase
import com.searchaid.domain.usecase.GetMissingCaseUseCase
import com.searchaid.domain.usecase.GetSocialSourcesByPersonUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.SearchArchivesUseCase
import com.searchaid.domain.usecase.SearchWebUseCase
import kotlinx.coroutines.async
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WebSearchState(
    val loading: Boolean = true,
    val searching: Boolean = false,
    val queries: List<String> = emptyList(),
    val results: List<WebSearchResult> = emptyList(),
    val archiveResults: List<ArchiveResult> = emptyList(),
    val identityPack: IdentityPack? = null,
    val includeImages: Boolean = true,
    val error: String? = null,
) {
    val textResults: List<WebSearchResult>
        get() = results.filter { it.source != "google_images" }
    val imageResults: List<WebSearchResult>
        get() = results.filter { it.source == "google_images" }
}

@HiltViewModel
class WebSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCase: GetMissingCaseUseCase,
    private val getProfile: GetPersonProfileUseCase,
    private val getSocialSources: GetSocialSourcesByPersonUseCase,
    private val buildIdentityPack: BuildIdentityPackUseCase,
    private val generateQueries: GenerateSearchQueriesUseCase,
    private val searchWeb: SearchWebUseCase,
    private val searchArchives: SearchArchivesUseCase,
    private val addLead: AddLeadUseCase,
    private val logAction: LogActionUseCase,
) : ViewModel() {

    val caseId: Long = savedStateHandle["caseId"] ?: -1L

    private val _state = MutableStateFlow(WebSearchState())
    val state: StateFlow<WebSearchState> = _state

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
            val queries = generateQueries(pack)

            _state.update {
                it.copy(
                    loading = false,
                    identityPack = pack,
                    queries = queries,
                )
            }
        }
    }

    fun runSearch() {
        val pack = _state.value.identityPack ?: return
        _state.update { it.copy(searching = true, error = null) }

        viewModelScope.launch {
            try {
                val webDeferred = async { searchWeb(pack, caseId, includeImages = _state.value.includeImages) }
                val archiveDeferred = async {
                    try { searchArchives(pack) } catch (_: Exception) { emptyList() }
                }

                val results = webDeferred.await()
                val archives = archiveDeferred.await()

                _state.update {
                    it.copy(searching = false, results = results, archiveResults = archives)
                }
                logAction(
                    "WEB_SEARCH", caseId = caseId,
                    details = "Found ${results.size} web + ${archives.size} archive results",
                )
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, error = "Search failed. Check your connection and try again.") }
            }
        }
    }

    fun promoteToLead(result: WebSearchResult) {
        viewModelScope.launch {
            val lead = SearchLead(
                caseId = caseId,
                type = LeadType.WEB,
                platform = result.source,
                matchedValue = result.url,
                textSnippet = "${result.title}: ${result.snippet}".take(200),
                possibleLocationName = null,
                lat = null, lon = null,
                timestamp = result.foundAt,
                confidence = result.relevanceScore,
            )
            addLead(lead)
            logAction("LEAD_FROM_WEB", caseId = caseId, details = "Promoted: ${result.url}")

            // Mark as promoted in local state
            _state.update { state ->
                state.copy(
                    results = state.results.map {
                        if (it.url == result.url) it.copy(status = SearchResultStatus.PROMOTED_TO_LEAD)
                        else it
                    },
                )
            }
        }
    }

    fun toggleImages() {
        _state.update { it.copy(includeImages = !it.includeImages) }
    }

    fun dismissResult(result: WebSearchResult) {
        _state.update { state ->
            state.copy(
                results = state.results.map {
                    if (it.url == result.url) it.copy(status = SearchResultStatus.DISMISSED)
                    else it
                },
            )
        }
    }
}
