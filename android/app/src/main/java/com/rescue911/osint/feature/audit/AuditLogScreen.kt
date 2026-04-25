package com.rescue911.osint.feature.audit

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.repository.Rescue911Repository
import com.rescue911.osint.domain.model.AuditEntry
import com.rescue911.osint.ui.components.EmptyState
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val repository: Rescue911Repository,
) : ViewModel() {
    private val _entries = MutableStateFlow<List<AuditEntry>?>(null)
    val entries: StateFlow<List<AuditEntry>?> = _entries.asStateFlow()

    init {
        viewModelScope.launch {
            repository.auditLog().collect { _entries.value = it }
        }
    }
}

@Composable
fun AuditLogScreen(
    navController: NavHostController,
    padding: PaddingValues,
    vm: AuditLogViewModel = hiltViewModel(),
) {
    val entries by vm.entries.collectAsState()
    ScreenScaffold(stringResource(R.string.nav_audit), padding) {
        when {
            entries == null -> EmptyState(stringResource(R.string.state_loading))
            entries!!.isEmpty() -> EmptyState(stringResource(R.string.state_empty_audit))
            else -> LazyColumn(Modifier.fillMaxWidth()) {
                items(entries!!) { entry ->
                    InfoCard(
                        title = entry.action,
                        body = "${entry.targetType ?: "—"} ${entry.targetId ?: ""}\n${entry.actorId ?: "system"} • ${entry.createdAtIso}",
                        trailing = {
                            Text(
                                entry.id,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}
