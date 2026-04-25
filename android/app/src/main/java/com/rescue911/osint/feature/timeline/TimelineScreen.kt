package com.rescue911.osint.feature.timeline

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold

@Composable
fun TimelineScreen(navController: NavHostController, padding: PaddingValues, caseId: String) {
    ScreenScaffold(stringResource(R.string.nav_timeline), padding) {
        InfoCard(
            title = stringResource(R.string.timeline_placeholder_title),
            body = stringResource(R.string.timeline_placeholder_body),
        )
    }
}
