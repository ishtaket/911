package com.pca.assistant.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pca.assistant.service.ListeningService
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.ui.dashboard.DashboardScreen
import com.pca.assistant.ui.dashboard.DashboardViewModel
import com.pca.assistant.ui.history.HistoryScreen
import com.pca.assistant.ui.history.HistoryViewModel
import com.pca.assistant.ui.onboarding.OnboardingScreen
import com.pca.assistant.ui.onboarding.OnboardingViewModel
import com.pca.assistant.ui.settings.SettingsScreen
import com.pca.assistant.ui.settings.SettingsViewModel
import com.pca.assistant.ui.theme.PcaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ensureChannels + MemoryScheduler.schedule moved to PcaApplication
        // (B-9) so they run even when the user never opens the activity.

        // If onboarding is complete and listening was on, start service eagerly.
        lifecycleScope.launch {
            val cfg = settings.flow.first()
            if (cfg.onboardingDone && cfg.listeningEnabled) {
                ListeningService.start(this@MainActivity)
            }
        }

        setContent {
            PcaTheme {
                val cfg by settings.flow.collectAsState(initial = null)
                val nav = rememberNavController()
                val startRoute = when {
                    cfg == null -> Routes.ONBOARDING
                    cfg?.onboardingDone == true -> Routes.DASHBOARD
                    else -> Routes.ONBOARDING
                }
                NavHost(navController = nav, startDestination = startRoute) {
                    composable(Routes.ONBOARDING) {
                        val vm: OnboardingViewModel = hiltViewModel()
                        OnboardingScreen(
                            vm = vm,
                            onDone = {
                                ListeningService.start(this@MainActivity)
                                nav.navigate(Routes.DASHBOARD) {
                                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Routes.DASHBOARD) {
                        val vm: DashboardViewModel = hiltViewModel()
                        DashboardScreen(
                            vm = vm,
                            onOpenHistory = { nav.navigate(Routes.HISTORY) },
                            onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        )
                    }
                    composable(Routes.HISTORY) {
                        val vm: HistoryViewModel = hiltViewModel()
                        HistoryScreen(vm = vm, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.SETTINGS) {
                        val vm: SettingsViewModel = hiltViewModel()
                        SettingsScreen(
                            vm = vm,
                            onBack = { nav.popBackStack() },
                            onReEnroll = {
                                nav.navigate(Routes.ONBOARDING) {
                                    popUpTo(Routes.DASHBOARD) { inclusive = false }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}
