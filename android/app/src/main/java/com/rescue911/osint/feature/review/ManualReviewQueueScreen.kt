package com.rescue911.osint.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.remote.Rescue911Api
import com.rescue911.osint.data.remote.dto.L3ActionDto
import com.rescue911.osint.data.remote.dto.ReviewBodyDto
import com.rescue911.osint.data.remote.mapper.toDomain
import com.rescue911.osint.data.repository.Rescue911Repository
import com.rescue911.osint.domain.model.Evidence
import com.rescue911.osint.domain.model.EvidenceStatus
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

@HiltViewModel
class ManualReviewViewModel @Inject constructor(
    private val repository: Rescue911Repository,
    private val api: Rescue911Api,
) : ViewModel() {
    private val _items = MutableStateFlow<List<Evidence>?>(null)
    val items: StateFlow<List<Evidence>?> = _items.asStateFlow()

    private val _banner = MutableStateFlow<Pair<String, BannerKind>?>(null)
    val banner: StateFlow<Pair<String, BannerKind>?> = _banner.asStateFlow()

    init { reload() }

    private fun reload() {
        viewModelScope.launch {
            // Pull review queue from all cases. The repository exposes per-case
            // evidence; for the queue we currently fetch /v1/evidence with no
            // case filter via the API.
            val all = runCatching { api.listEvidence(caseId = null).map { it.toDomain() } }
                .getOrElse { emptyList() }
            _items.value = all.filter {
                it.status == EvidenceStatus.NEEDS_REVIEW || it.status == EvidenceStatus.CORROBORATED
            }
        }
    }

    fun act(evidenceId: String, action: L3ActionDto) {
        viewModelScope.launch {
            val result = runCatching {
                api.reviewEvidence(evidenceId, ReviewBodyDto(action = action, reviewerId = "operator"))
            }
            result.fold(
                onSuccess = {
                    _banner.value = "Review recorded: ${action.name.lowercase()} → ${it.status.name}" to BannerKind.SUCCESS
                    reload()
                },
                onFailure = {
                    _banner.value = "Review failed: ${it.javaClass.simpleName}: ${it.message ?: "no detail"}" to BannerKind.ERROR
                },
            )
        }
    }

    fun dismissBanner() { _banner.value = null }
}

@Composable
fun ManualReviewQueueScreen(
    navController: NavHostController,
    padding: PaddingValues,
    vm: ManualReviewViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsState()
    val banner by vm.banner.collectAsState()

    ScreenScaffold(stringResource(R.string.nav_review), padding) {
        banner?.let { (msg, kind) ->
            ActionResultBanner(message = msg, kind = kind)
        }

        when {
            items == null -> EmptyState(stringResource(R.string.state_loading))
            items!!.isEmpty() -> EmptyState(stringResource(R.string.state_empty_review))
            else -> items!!.forEach { e ->
                InfoCard(
                    title = e.title ?: e.provider,
                    body = e.snippet ?: "",
                    trailing = { ValidationBadge(e.status) },
                )
                ValidationLevelDots(e.validation, modifier = Modifier.padding(start = 32.dp, top = 0.dp, end = 16.dp))
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { vm.act(e.id, L3ActionDto.CONFIRM) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.review_confirm)) }
                    OutlinedButton(
                        onClick = { vm.act(e.id, L3ActionDto.REJECT) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.review_reject)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { vm.act(e.id, L3ActionDto.NEEDS_MORE_CHECKS) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.review_needs_more)) }
                    OutlinedButton(
                        onClick = { vm.act(e.id, L3ActionDto.ESCALATE_TO_AUTHORITIES) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.review_escalate)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
