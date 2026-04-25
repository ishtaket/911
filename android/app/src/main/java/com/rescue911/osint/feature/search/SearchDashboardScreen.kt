package com.rescue911.osint.feature.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold

@Composable
fun SearchDashboardScreen(navController: NavHostController, padding: PaddingValues, caseId: String) {
    ScreenScaffold(stringResource(R.string.search_dashboard), padding) {
        InfoCard(title = stringResource(R.string.search_web), body = stringResource(R.string.search_web_explainer))
        InfoCard(title = stringResource(R.string.search_social), body = stringResource(R.string.search_social_explainer))
        InfoCard(title = stringResource(R.string.search_archive), body = stringResource(R.string.search_archive_explainer))
        InfoCard(title = stringResource(R.string.search_geoint), body = stringResource(R.string.search_geoint_explainer))
    }
}
