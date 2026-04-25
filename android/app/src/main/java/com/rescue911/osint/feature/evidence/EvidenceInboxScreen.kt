package com.rescue911.osint.feature.evidence

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.remote.Rescue911Api
import com.rescue911.osint.data.remote.mapper.toDomain
import com.rescue911.osint.data.repository.Rescue911Repository
import com.rescue911.osint.domain.model.Evidence
import com.rescue911.osint.domain.model.SourceType
import com.rescue911.osint.ui.components.ActionResultBanner
import com.rescue911.osint.ui.components.BannerKind
import com.rescue911.osint.ui.components.EmptyState
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold
import com.rescue911.osint.ui.components.ValidationBadge
import com.rescue911.osint.ui.components.ValidationLevelDots
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Per-tab grouping. Each tab maps to one or more domain SourceTypes. */
enum class SearchKind(val key: String, val sources: Set<SourceType>) {
    ALL("", emptySet()),
    WEB("web", setOf(SourceType.WEB)),
    SOCIAL("social", setOf(SourceType.SOCIAL)),
    ARCHIVE("archive", setOf(SourceType.ARCHIVE)),
    GEOINT("geoint", setOf(
        SourceType.GEOINT, SourceType.MAPS, SourceType.OCR, SourceType.VISION, SourceType.EXIF,
    )),
    ;

    companion object {
        fun fromKey(key: String): SearchKind =
            values().firstOrNull { it.key.equals(key, ignoreCase = true) } ?: ALL
    }
}

@HiltViewModel
class EvidenceInboxViewModel @Inject constructor(
    private val repository: Rescue911Repository,
    private val api: Rescue911Api,
) : ViewModel() {
    private val _all = MutableStateFlow<List<Evidence>?>(null)
    val all: StateFlow<List<Evidence>?> = _all.asStateFlow()

    private val _banner = MutableStateFlow<Pair<String, BannerKind>?>(null)
    val banner: StateFlow<Pair<String, BannerKind>?> = _banner.asStateFlow()

    suspend fun load(caseId: String) {
        _all.value = runCatching { repository.evidenceForCase(caseId).first() }
            .getOrDefault(emptyList())
    }

    /** Calls POST /v1/search/start/{caseId} which dispatches the case's full
     *  query plan across web/social/archive providers (mock in this milestone)
     *  and returns the resulting evidence. We then reload via the repository
     *  so the visible list reflects what the backend stored. */
    fun dispatchSearch(caseId: String, kind: SearchKind) {
        viewModelScope.launch {
            val result = runCatching { api.startSearch(caseId).map { it.toDomain() } }
            result.fold(
                onSuccess = { fresh ->
                    val mineCount = if (kind.sources.isEmpty()) fresh.size
                        else fresh.count { it.sourceType in kind.sources }
                    _banner.value = "Search dispatched. ${fresh.size} item(s) total, $mineCount for ${kind.name.lowercase()}." to BannerKind.SUCCESS
                    load(caseId)
                },
                onFailure = {
                    _banner.value = "Search failed: ${it.javaClass.simpleName}: ${it.message ?: "no detail"}" to BannerKind.ERROR
                },
            )
        }
    }
}

@Composable
fun EvidenceInboxScreen(
    navController: NavHostController,
    padding: PaddingValues,
    caseId: String,
    source: String = "",
    vm: EvidenceInboxViewModel = hiltViewModel(),
) {
    val all by vm.all.collectAsState()
    val banner by vm.banner.collectAsState()
    val kind = remember(source) { SearchKind.fromKey(source) }

    LaunchedEffect(caseId) { vm.load(caseId) }

    val title = stringResource(
        when (kind) {
            SearchKind.WEB -> R.string.evidence_results_web
            SearchKind.SOCIAL -> R.string.evidence_results_social
            SearchKind.ARCHIVE -> R.string.evidence_results_archive
            SearchKind.GEOINT -> R.string.evidence_results_geoint
            SearchKind.ALL -> R.string.nav_evidence
        }
    )
    val explainerRes = when (kind) {
        SearchKind.WEB -> R.string.evidence_explain_web
        SearchKind.SOCIAL -> R.string.evidence_explain_social
        SearchKind.ARCHIVE -> R.string.evidence_explain_archive
        SearchKind.GEOINT -> R.string.evidence_explain_geoint
        SearchKind.ALL -> 0
    }
    val emptyRes = when (kind) {
        SearchKind.WEB -> R.string.evidence_empty_web
        SearchKind.SOCIAL -> R.string.evidence_empty_social
        SearchKind.ARCHIVE -> R.string.evidence_empty_archive
        SearchKind.GEOINT -> R.string.evidence_empty_geoint
        SearchKind.ALL -> R.string.state_empty_evidence
    }

    val items: List<Evidence>? = all?.let { list ->
        if (kind == SearchKind.ALL) list else list.filter { it.sourceType in kind.sources }
    }

    ScreenScaffold(title, padding) {
        if (explainerRes != 0) {
            ActionResultBanner(message = stringResource(explainerRes), kind = BannerKind.INFO)
        }
        banner?.let { (msg, k) -> ActionResultBanner(message = msg, kind = k) }

        // GeoINT cannot be dispatched without media — render a distinct hint.
        if (kind == SearchKind.GEOINT) {
            ActionResultBanner(
                message = stringResource(R.string.evidence_geoint_dispatch_hint),
                kind = BannerKind.WARNING,
            )
        } else if (kind != SearchKind.ALL && caseId.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Button(
                    onClick = { vm.dispatchSearch(caseId, kind) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.evidence_dispatch_now, kind.name.lowercase()))
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        when {
            items == null -> EmptyState(stringResource(R.string.state_loading))
            items.isEmpty() -> EmptyState(stringResource(emptyRes))
            else -> LazyColumn(Modifier.fillMaxWidth()) {
                items(items) { e ->
                    InfoCard(
                        title = e.title ?: e.provider,
                        body = e.snippet ?: e.url ?: "",
                        trailing = {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                ValidationBadge(e.status)
                                Spacer(Modifier.height(4.dp))
                                ValidationLevelDots(e.validation)
                            }
                        },
                    )
                    e.nextAction?.let { next ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Text(
                                "${stringResource(R.string.next_action)}: $next",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
