package com.rescue911.osint.feature.audit

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.mock.MockData
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold

@Composable
fun AuditLogScreen(navController: NavHostController, padding: PaddingValues) {
    ScreenScaffold(stringResource(R.string.nav_audit), padding) {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(MockData.auditLog) { entry ->
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
