package com.rescue911.osint.feature.geoint

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.repository.Rescue911Repository
import com.rescue911.osint.domain.model.Hypothesis
import com.rescue911.osint.ui.components.EmptyState
import com.rescue911.osint.ui.components.HypothesisBriefRow
import com.rescue911.osint.ui.components.ScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class GeoIntMapViewModel @Inject constructor(
    private val repository: Rescue911Repository,
) : ViewModel() {
    suspend fun load(caseId: String): List<Hypothesis> = runCatching {
        if (caseId.isBlank()) {
            // No case context (e.g. bottom-nav Map tap) — surface every case's
            // hypotheses so the map screen still has something to show.
            repository.cases().first().flatMap { c ->
                repository.hypothesesForCase(c.id).first()
            }
        } else {
            repository.hypothesesForCase(caseId).first()
        }
    }.getOrDefault(emptyList())
}

@Composable
fun GeoIntMapScreen(
    navController: NavHostController,
    padding: PaddingValues,
    caseId: String,
    vm: GeoIntMapViewModel = hiltViewModel(),
) {
    var hypotheses by remember { mutableStateOf<List<Hypothesis>?>(null) }
    LaunchedEffect(caseId) { hypotheses = vm.load(caseId) }

    ScreenScaffold(stringResource(R.string.nav_geoint), padding) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.map_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            hypotheses == null -> EmptyState(stringResource(R.string.state_loading))
            hypotheses!!.isEmpty() -> EmptyState(stringResource(R.string.state_empty_hypotheses))
            else -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                hypotheses!!.forEach { HypothesisBriefRow(it) }
            }
        }
    }
}
