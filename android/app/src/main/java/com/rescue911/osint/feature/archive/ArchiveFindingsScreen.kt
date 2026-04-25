package com.rescue911.osint.feature.archive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.mock.MockData
import com.rescue911.osint.domain.model.SourceType
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold

@Composable
fun ArchiveFindingsScreen(navController: NavHostController, padding: PaddingValues, caseId: String) {
    val items = MockData.evidence.filter { it.sourceType == SourceType.ARCHIVE && (caseId.isBlank() || it.caseId == caseId) }
    ScreenScaffold(stringResource(R.string.nav_archive), padding) {
        if (items.isEmpty()) {
            InfoCard(
                title = stringResource(R.string.archive_empty_title),
                body = stringResource(R.string.archive_empty_body),
            )
        } else {
            items.forEach { e ->
                InfoCard(
                    title = e.title ?: e.provider,
                    body = (e.snippet ?: "") + "\n" + (e.url ?: ""),
                )
            }
        }
    }
}
