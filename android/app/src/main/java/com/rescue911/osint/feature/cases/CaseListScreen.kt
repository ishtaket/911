package com.rescue911.osint.feature.cases

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.domain.model.MissingCase
import com.rescue911.osint.navigation.Routes
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold

@Composable
fun CaseListScreen(
    navController: NavHostController,
    padding: PaddingValues,
    vm: CaseListViewModel = hiltViewModel(),
) {
    val cases by vm.cases.collectAsState()
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Routes.CREATE_CASE) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.case_create)) },
                containerColor = MaterialTheme.colorScheme.primary,
            )
        }
    ) { inner ->
        ScreenScaffold(stringResource(R.string.nav_cases), padding, Modifier.padding(inner)) {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(cases) { c -> CaseRow(c) { navController.navigate(Routes.caseDetail(c.id)) } }
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
