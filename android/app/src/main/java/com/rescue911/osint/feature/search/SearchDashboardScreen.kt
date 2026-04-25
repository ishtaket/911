package com.rescue911.osint.feature.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.navigation.Routes
import com.rescue911.osint.ui.components.ActionResultBanner
import com.rescue911.osint.ui.components.BannerKind
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold

@Composable
fun SearchDashboardScreen(
    navController: NavHostController,
    padding: PaddingValues,
    caseId: String,
) {
    var lastAction by remember { mutableStateOf<String?>(null) }
    val pickCaseFirst = stringResource(R.string.search_pick_case_first)

    ScreenScaffold(stringResource(R.string.search_dashboard), padding) {
        ActionResultBanner(
            message = stringResource(R.string.search_dispatch_note),
            kind = BannerKind.INFO,
        )

        lastAction?.let { msg ->
            ActionResultBanner(message = msg, kind = BannerKind.SUCCESS)
        }

        val openTyped: (String, String) -> Unit = { label, source ->
            if (caseId.isBlank()) {
                lastAction = pickCaseFirst
            } else {
                lastAction = "Opening $label results"
                navController.navigate(Routes.evidence(caseId, source))
            }
        }

        InfoCard(
            title = stringResource(R.string.search_web),
            body = stringResource(R.string.search_web_explainer),
            onClick = { openTyped("Web", "web") },
        )
        InfoCard(
            title = stringResource(R.string.search_social),
            body = stringResource(R.string.search_social_explainer),
            onClick = { openTyped("Social", "social") },
        )
        InfoCard(
            title = stringResource(R.string.search_archive),
            body = stringResource(R.string.search_archive_explainer),
            onClick = { openTyped("Archive", "archive") },
        )
        InfoCard(
            title = stringResource(R.string.search_geoint),
            body = stringResource(R.string.search_geoint_explainer),
            onClick = { openTyped("GeoINT", "geoint") },
        )
    }
}
