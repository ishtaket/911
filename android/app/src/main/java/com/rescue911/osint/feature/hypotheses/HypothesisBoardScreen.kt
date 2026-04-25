package com.rescue911.osint.feature.hypotheses

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.rescue911.osint.domain.model.Hypothesis
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.RiskChip
import com.rescue911.osint.ui.components.ScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class HypothesisViewModel @Inject constructor(
    private val repository: Rescue911Repository,
) : ViewModel() {
    suspend fun load(caseId: String): List<Hypothesis> = repository.hypothesesForCase(caseId).first()
}

@Composable
fun HypothesisBoardScreen(
    navController: NavHostController,
    padding: PaddingValues,
    caseId: String,
    vm: HypothesisViewModel = hiltViewModel(),
) {
    var items by remember { mutableStateOf<List<Hypothesis>>(emptyList()) }
    LaunchedEffect(caseId) { items = vm.load(caseId) }

    ScreenScaffold(stringResource(R.string.nav_hypotheses), padding) {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(items) { h ->
                InfoCard(
                    title = "${(h.confidence * 100).toInt()}% — ${h.label}",
                    body = (h.placeName ?: "") + (
                        if (h.contradictions.isNotEmpty())
                            "\n⚠ " + h.contradictions.joinToString()
                        else ""
                    ),
                    trailing = { RiskChip(h.risk) },
                )
                if (h.nextChecks.isNotEmpty()) {
                    Text(
                        "${stringResource(R.string.next_checks)}: " + h.nextChecks.joinToString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}
