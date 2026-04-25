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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.repository.Rescue911Repository
import com.rescue911.osint.domain.model.Evidence
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold
import com.rescue911.osint.ui.components.ValidationBadge
import com.rescue911.osint.ui.components.ValidationLevelDots
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class EvidenceInboxViewModel @Inject constructor(
    private val repository: Rescue911Repository,
) : ViewModel() {
    suspend fun load(caseId: String): List<Evidence> =
        repository.evidenceForCase(caseId).first()
}

@Composable
fun EvidenceInboxScreen(
    navController: NavHostController,
    padding: PaddingValues,
    caseId: String,
    vm: EvidenceInboxViewModel = hiltViewModel(),
) {
    var items by remember { mutableStateOf<List<Evidence>>(emptyList()) }
    LaunchedEffect(caseId) { items = vm.load(caseId) }

    ScreenScaffold(stringResource(R.string.nav_evidence), padding) {
        LazyColumn(Modifier.fillMaxWidth()) {
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
