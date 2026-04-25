package com.rescue911.osint.feature.cases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.rescue911.osint.domain.model.MissingCase
import com.rescue911.osint.navigation.Routes
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class CaseDetailViewModel @Inject constructor(
    private val repository: Rescue911Repository,
) : ViewModel() {
    suspend fun load(id: String): MissingCase? =
        runCatching { repository.caseById(id).first() }.getOrNull()
}

@Composable
fun CaseDetailScreen(
    navController: NavHostController,
    padding: PaddingValues,
    caseId: String,
    vm: CaseDetailViewModel = hiltViewModel(),
) {
    var case by remember { mutableStateOf<MissingCase?>(null) }
    LaunchedEffect(caseId) { case = vm.load(caseId) }

    ScreenScaffold(case?.title ?: stringResource(R.string.nav_cases), padding) {
        case?.let { c ->
            InfoCard(
                title = c.person.fullName,
                body = "${c.lastSeenLocation ?: "Unknown"} • ${c.languages.joinToString()}",
            )
            InfoCard(
                title = stringResource(R.string.case_status),
                body = c.status.name.replace('_', ' '),
                trailing = {
                    Text(
                        "${c.lastSeenLat ?: "—"}, ${c.lastSeenLon ?: "—"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.search(c.id)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_run_search)) }
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.upload(c.id)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_upload_media)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.evidence(c.id)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.nav_evidence)) }
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.hypotheses(c.id)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.nav_hypotheses)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.geoint(c.id)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.nav_geoint)) }
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.archive(c.id)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.nav_archive)) }
                }
            }
        }
    }
}
