package com.rescue911.osint.feature.archive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.repository.Rescue911Repository
import com.rescue911.osint.domain.model.Evidence
import com.rescue911.osint.domain.model.SourceType
import com.rescue911.osint.ui.components.EmptyState
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val repository: Rescue911Repository,
) : ViewModel() {
    suspend fun load(caseId: String): List<Evidence> = runCatching {
        repository.evidenceForCase(caseId).first().filter { it.sourceType == SourceType.ARCHIVE }
    }.getOrDefault(emptyList())
}

@Composable
fun ArchiveFindingsScreen(
    navController: NavHostController,
    padding: PaddingValues,
    caseId: String,
    vm: ArchiveViewModel = hiltViewModel(),
) {
    var items by remember { mutableStateOf<List<Evidence>?>(null) }
    LaunchedEffect(caseId) {
        items = if (caseId.isBlank()) emptyList() else vm.load(caseId)
    }

    ScreenScaffold(stringResource(R.string.nav_archive), padding) {
        when {
            items == null -> EmptyState(stringResource(R.string.state_loading))
            items!!.isEmpty() -> InfoCard(
                title = stringResource(R.string.archive_empty_title),
                body = stringResource(R.string.archive_empty_body),
            )
            else -> items!!.forEach { e ->
                InfoCard(
                    title = e.title ?: e.provider,
                    body = (e.snippet ?: "") + "\n" + (e.url ?: ""),
                )
            }
        }
    }
}
