package com.searchaid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.searchaid.ui.feature_audit.AuditLogScreen
import com.searchaid.ui.feature_case.ActiveCaseDashboardScreen
import com.searchaid.ui.feature_case.StartMissingCaseScreen
import com.searchaid.ui.feature_map.SearchMapScreen
import com.searchaid.ui.feature_outreach.OutreachScreen
import com.searchaid.ui.feature_profile.CreateEditProfileScreen
import com.searchaid.ui.feature_profile.PersonProfileScreen
import com.searchaid.ui.feature_profile.ProfilesListScreen
import com.searchaid.ui.feature_search.LeadsListScreen
import com.searchaid.ui.feature_social.SocialSearchScreen
import com.searchaid.ui.feature_websearch.WebSearchScreen
import com.searchaid.ui.feature_witness.WitnessReportsScreen

@Composable
fun SearchAidNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.ProfilesList.route) {

        composable(Screen.ProfilesList.route) {
            ProfilesListScreen(
                onProfileClick = { navController.navigate(Screen.PersonProfile.withId(it)) },
                onCreateClick = { navController.navigate(Screen.CreateEditProfile.create()) },
            )
        }

        composable(
            Screen.PersonProfile.route,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) {
            PersonProfileScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Screen.CreateEditProfile.edit(it)) },
                onStartCase = { navController.navigate(Screen.StartMissingCase.withId(it)) },
                onOpenCase = { navController.navigate(Screen.ActiveCaseDashboard.withId(it)) },
            )
        }

        composable(
            Screen.CreateEditProfile.route,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            CreateEditProfileScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            Screen.StartMissingCase.route,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
        ) {
            StartMissingCaseScreen(
                onBack = { navController.popBackStack() },
                onCaseStarted = { caseId ->
                    navController.navigate(Screen.ActiveCaseDashboard.withId(caseId)) {
                        popUpTo(Screen.ProfilesList.route)
                    }
                },
            )
        }

        composable(
            Screen.ActiveCaseDashboard.route,
            arguments = listOf(navArgument("caseId") { type = NavType.LongType }),
        ) {
            ActiveCaseDashboardScreen(
                onBack = { navController.popBackStack() },
                onOpenMap = { navController.navigate(Screen.SearchMap.withId(it)) },
                onOpenLeads = { navController.navigate(Screen.LeadsList.withId(it)) },
                onOpenWitness = { navController.navigate(Screen.WitnessReports.withId(it)) },
                onOpenOutreach = { navController.navigate(Screen.Outreach.withId(it)) },
                onOpenWebSearch = { navController.navigate(Screen.WebSearch.withId(it)) },
                onOpenSocialSearch = { navController.navigate(Screen.SocialSearch.withId(it)) },
                onOpenAudit = { navController.navigate(Screen.AuditLog.forCase(it)) },
            )
        }

        composable(
            Screen.SearchMap.route,
            arguments = listOf(navArgument("caseId") { type = NavType.LongType }),
        ) {
            SearchMapScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Screen.LeadsList.route,
            arguments = listOf(navArgument("caseId") { type = NavType.LongType }),
        ) {
            LeadsListScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Screen.WitnessReports.route,
            arguments = listOf(navArgument("caseId") { type = NavType.LongType }),
        ) {
            WitnessReportsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Screen.Outreach.route,
            arguments = listOf(navArgument("caseId") { type = NavType.LongType }),
        ) {
            OutreachScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Screen.WebSearch.route,
            arguments = listOf(navArgument("caseId") { type = NavType.LongType }),
        ) {
            WebSearchScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Screen.SocialSearch.route,
            arguments = listOf(navArgument("caseId") { type = NavType.LongType }),
        ) {
            SocialSearchScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Screen.AuditLog.route,
            arguments = listOf(navArgument("caseId") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            AuditLogScreen(onBack = { navController.popBackStack() })
        }
    }
}
