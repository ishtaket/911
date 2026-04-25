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

    /** Dispatch via the channel-specific backend endpoint.
     *
     *  All branches set a visible banner — never leave the screen empty
     *  without explanation.
     *
     *  ARCHIVE  : structured ArchiveStartResponse → no_targets / no_results
     *             / completed banner.
     *  GEOINT   : structured GeoIntStartResponse → no_media_uploaded etc.
     *  WEB/SOC. : structured WebSocialStartResponse with state, per-provider
     *             info, items_returned, items_deduped, message → banner is
     *             driven by the backend `state` so the operator sees the
     *             exact reason (no_results / not_configured / auth_required
     *             / rate_limited / deduplicated / provider_error / mock /
     *             completed) and never a silent empty screen.
     */
    fun dispatchSearch(caseId: String, kind: SearchKind) {
        viewModelScope.launch {
            runCatching {
                when (kind) {
                    SearchKind.WEB, SearchKind.SOCIAL -> {
                        val r = if (kind == SearchKind.WEB) api.startWebSearch(caseId)
                            else api.startSocialSearch(caseId)
                        _banner.value = formatWebSocialBanner(r)
                    }
                    SearchKind.ARCHIVE -> {
                        val r = api.startArchiveSearch(caseId)
                        val k = when (r.state) {
                            "completed" -> BannerKind.SUCCESS
                            "no_results" -> BannerKind.WARNING
                            "no_targets" -> BannerKind.WARNING
                            else -> BannerKind.INFO
                        }
                        _banner.value = (r.message + " (state=${r.state}, targets_attempted=${r.targetsAttempted})") to k
                    }
                    SearchKind.GEOINT -> {
                        val r = api.startGeoint(caseId)
                        _banner.value = (r.message + " (state=${r.state})") to
                            (if (r.state == "no_media_uploaded") BannerKind.WARNING else BannerKind.SUCCESS)
                    }
                    SearchKind.ALL -> {
                        val items = api.startSearch(caseId)
                        _banner.value = "Dispatched all channels: ${items.size} item(s) returned." to
                            (if (items.isEmpty()) BannerKind.WARNING else BannerKind.SUCCESS)
                    }
                }
            }.onFailure {
                _banner.value = "Dispatch failed: ${it.javaClass.simpleName}: ${it.message ?: "no detail"}" to BannerKind.ERROR
            }
            // Refresh the evidence list from the canonical store after every
            // dispatch — the structured response only carries items stored
            // in this call, but the full evidence list lives in /v1/evidence.
            load(caseId)
        }
    }

    private fun formatWebSocialBanner(
        r: com.rescue911.osint.data.remote.dto.WebSocialStartResponseDto,
    ): Pair<String, BannerKind> {
        val kind = when (r.state) {
            "completed" -> BannerKind.SUCCESS
            "mock" -> BannerKind.INFO
            "deduplicated", "no_results" -> BannerKind.WARNING
            "not_configured", "auth_required", "rate_limited" -> BannerKind.WARNING
            "provider_error" -> BannerKind.ERROR
            else -> BannerKind.INFO
        }
        // Per-provider one-line summary. Truncated so the banner doesn't
        // overflow the screen on the social channel (9 providers).
        val provs = r.providers.take(6)
            .joinToString(", ") { "${it.provider}=${it.state}(${it.items})" }
        val more = if (r.providers.size > 6) " +${r.providers.size - 6} more" else ""
        val msg = "Dispatched ${r.channel}: state=${r.state}, " +
            "items_returned=${r.itemsReturned}, items_stored=${r.evidence.size}, " +
            "items_deduped=${r.itemsDeduped}. Providers: [$provs]$more."
        return msg to kind
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

        if (kind == SearchKind.GEOINT) {
            ActionResultBanner(
                message = stringResource(R.string.evidence_geoint_dispatch_hint),
                kind = BannerKind.WARNING,
            )
        }
        if (kind != SearchKind.ALL && caseId.isNotBlank()) {
            val labelRes = if (kind == SearchKind.GEOINT)
                R.string.evidence_probe_geoint else R.string.evidence_dispatch_now
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Button(
                    onClick = { vm.dispatchSearch(caseId, kind) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(labelRes, kind.name.lowercase()))
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
