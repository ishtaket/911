package com.rescue911.osint.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Cases
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rescue911.osint.feature.common.DataSourceViewModel
import com.rescue911.osint.ui.components.DataSourceBadge
import com.rescue911.osint.feature.archive.ArchiveFindingsScreen
import com.rescue911.osint.feature.audit.AuditLogScreen
import com.rescue911.osint.feature.cases.CaseDetailScreen
import com.rescue911.osint.feature.cases.CaseListScreen
import com.rescue911.osint.feature.cases.CreateCaseScreen
import com.rescue911.osint.feature.cases.PersonProfileScreen
import com.rescue911.osint.feature.evidence.EvidenceInboxScreen
import com.rescue911.osint.feature.evidence.UploadMediaScreen
import com.rescue911.osint.feature.geoint.GeoIntMapScreen
import com.rescue911.osint.feature.hypotheses.HypothesisBoardScreen
import com.rescue911.osint.feature.providers.ProviderStatusScreen
import com.rescue911.osint.feature.review.ManualReviewQueueScreen
import com.rescue911.osint.feature.search.SearchDashboardScreen
import com.rescue911.osint.feature.settings.SettingsScreen
import com.rescue911.osint.feature.social.SocialGraphScreen
import com.rescue911.osint.feature.timeline.TimelineScreen

object Routes {
    const val CASES = "cases"
    const val CREATE_CASE = "cases/new"
    const val CASE_DETAIL = "cases/{caseId}"
    const val PERSON = "person/{personId}"
    const val UPLOAD_MEDIA = "media/upload?caseId={caseId}"
    const val SEARCH = "search?caseId={caseId}"
    const val EVIDENCE = "evidence?caseId={caseId}"
    const val HYPOTHESES = "hypotheses?caseId={caseId}"
    const val GEOINT_MAP = "geoint?caseId={caseId}"
    const val TIMELINE = "timeline?caseId={caseId}"
    const val SOCIAL_GRAPH = "social?caseId={caseId}"
    const val ARCHIVE = "archive?caseId={caseId}"
    const val REVIEW_QUEUE = "review"
    const val AUDIT = "audit"
    const val SETTINGS = "settings"
    const val PROVIDERS = "providers"

    fun caseDetail(caseId: String) = "cases/$caseId"
    fun person(personId: String) = "person/$personId"
    fun search(caseId: String) = "search?caseId=$caseId"
    fun evidence(caseId: String) = "evidence?caseId=$caseId"
    fun hypotheses(caseId: String) = "hypotheses?caseId=$caseId"
    fun geoint(caseId: String) = "geoint?caseId=$caseId"
    fun upload(caseId: String) = "media/upload?caseId=$caseId"
    fun timeline(caseId: String) = "timeline?caseId=$caseId"
    fun social(caseId: String) = "social?caseId=$caseId"
    fun archive(caseId: String) = "archive?caseId=$caseId"
}

private data class BottomItem(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
fun Rescue911Nav(
    dataSourceVm: DataSourceViewModel = hiltViewModel(),
) {
    val navController: NavHostController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val source by dataSourceVm.source.collectAsState()

    val items = listOf(
        BottomItem(Routes.CASES, com.rescue911.osint.R.string.nav_cases, Icons.Filled.Cases),
        BottomItem(Routes.REVIEW_QUEUE, com.rescue911.osint.R.string.nav_review, Icons.Filled.Inbox),
        BottomItem("geoint?caseId=case-1", com.rescue911.osint.R.string.nav_map, Icons.Filled.Map),
        BottomItem(Routes.AUDIT, com.rescue911.osint.R.string.nav_audit, Icons.Filled.AdminPanelSettings),
        BottomItem(Routes.SETTINGS, com.rescue911.osint.R.string.nav_settings, Icons.Filled.Settings),
    )

    Scaffold(
        bottomBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DataSourceBadge(source)
                }
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = current?.substringBefore('?') == item.route.substringBefore('?'),
                            onClick = {
                                navController.navigate(item.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(Routes.CASES) { saveState = true }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(androidx.compose.ui.res.stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CASES,
            modifier = Modifier
        ) {
            composable(Routes.CASES) { CaseListScreen(navController, padding) }
            composable(Routes.CREATE_CASE) { CreateCaseScreen(navController, padding) }
            composable(
                Routes.CASE_DETAIL,
                arguments = listOf(navArgument("caseId") { type = NavType.StringType }),
            ) {
                val caseId = it.arguments?.getString("caseId").orEmpty()
                CaseDetailScreen(navController, padding, caseId)
            }
            composable(
                Routes.PERSON,
                arguments = listOf(navArgument("personId") { type = NavType.StringType }),
            ) {
                val personId = it.arguments?.getString("personId").orEmpty()
                PersonProfileScreen(navController, padding, personId)
            }
            composable(
                Routes.UPLOAD_MEDIA,
                arguments = listOf(navArgument("caseId") { type = NavType.StringType; defaultValue = "" }),
            ) {
                val caseId = it.arguments?.getString("caseId").orEmpty()
                UploadMediaScreen(navController, padding, caseId)
            }
            composable(
                Routes.SEARCH,
                arguments = listOf(navArgument("caseId") { type = NavType.StringType; defaultValue = "" }),
            ) {
                val caseId = it.arguments?.getString("caseId").orEmpty()
                SearchDashboardScreen(navController, padding, caseId)
            }
            composable(
                Routes.EVIDENCE,
                arguments = listOf(navArgument("caseId") { type = NavType.StringType; defaultValue = "" }),
            ) {
                val caseId = it.arguments?.getString("caseId").orEmpty()
                EvidenceInboxScreen(navController, padding, caseId)
            }
            composable(
                Routes.HYPOTHESES,
                arguments = listOf(navArgument("caseId") { type = NavType.StringType; defaultValue = "" }),
            ) {
                val caseId = it.arguments?.getString("caseId").orEmpty()
                HypothesisBoardScreen(navController, padding, caseId)
            }
            composable(
                Routes.GEOINT_MAP,
                arguments = listOf(navArgument("caseId") { type = NavType.StringType; defaultValue = "" }),
            ) {
                val caseId = it.arguments?.getString("caseId").orEmpty()
                GeoIntMapScreen(navController, padding, caseId)
            }
            composable(
                Routes.TIMELINE,
                arguments = listOf(navArgument("caseId") { type = NavType.StringType; defaultValue = "" }),
            ) {
                val caseId = it.arguments?.getString("caseId").orEmpty()
                TimelineScreen(navController, padding, caseId)
            }
            composable(
                Routes.SOCIAL_GRAPH,
                arguments = listOf(navArgument("caseId") { type = NavType.StringType; defaultValue = "" }),
            ) {
                val caseId = it.arguments?.getString("caseId").orEmpty()
                SocialGraphScreen(navController, padding, caseId)
            }
            composable(
                Routes.ARCHIVE,
                arguments = listOf(navArgument("caseId") { type = NavType.StringType; defaultValue = "" }),
            ) {
                val caseId = it.arguments?.getString("caseId").orEmpty()
                ArchiveFindingsScreen(navController, padding, caseId)
            }
            composable(Routes.REVIEW_QUEUE) { ManualReviewQueueScreen(navController, padding) }
            composable(Routes.AUDIT) { AuditLogScreen(navController, padding) }
            composable(Routes.SETTINGS) { SettingsScreen(navController, padding) }
            composable(Routes.PROVIDERS) { ProviderStatusScreen(navController, padding) }
        }
    }
}
