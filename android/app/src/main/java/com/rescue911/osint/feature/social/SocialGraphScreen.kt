package com.rescue911.osint.feature.social

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold

@Composable
fun SocialGraphScreen(navController: NavHostController, padding: PaddingValues, caseId: String) {
    ScreenScaffold(stringResource(R.string.nav_social), padding) {
        InfoCard(
            title = stringResource(R.string.social_graph_placeholder_title),
            body = stringResource(R.string.social_graph_placeholder_body),
        )
    }
}
