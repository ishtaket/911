package com.rescue911.osint.feature.cases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.domain.model.MissingCase
import com.rescue911.osint.navigation.Routes
import com.rescue911.osint.ui.components.EmptyState
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold

@Composable
fun CaseListScreen(
    navController: NavHostController,
    padding: PaddingValues,
    vm: CaseListViewModel = hiltViewModel(),
) {
    val cases by vm.cases.collectAsState()
    // Re-fetch every time CaseList becomes visible so a freshly-created
    // case (POST /v1/cases) shows up immediately on back-nav from
    // CreateCase / CaseDetail.
    LaunchedEffect(Unit) { vm.refresh() }
    ScreenScaffold(stringResource(R.string.nav_cases), padding) {
        // Prominent, deterministic entry point — guaranteed to be in the
        // accessibility tree (no FAB stacking ambiguity inside the bottom
        // nav scaffold). Always at the top of the case list.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { navController.navigate(Routes.CREATE_CASE) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.case_create))
            }
        }
        Spacer(Modifier.height(4.dp))
        when {
            cases == null -> EmptyState(stringResource(R.string.state_loading))
            cases!!.isEmpty() -> EmptyState(stringResource(R.string.state_empty_cases))
            else -> LazyColumn(Modifier.fillMaxWidth()) {
                items(cases!!) { c -> CaseRow(c) { navController.navigate(Routes.caseDetail(c.id)) } }
            }
        }
    }
}

@Composable
private fun CaseRow(c: MissingCase, onClick: () -> Unit) {
    InfoCard(
        title = c.title,
        body = "${c.person.fullName} • ${c.lastSeenLocation ?: "Unknown"}",
        onClick = onClick,
        trailing = {
            Text(
                c.status.name.replace('_', ' '),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        },
    )
    Spacer(Modifier.width(0.dp))
}
