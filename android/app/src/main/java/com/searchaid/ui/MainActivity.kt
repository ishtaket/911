package com.searchaid.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.searchaid.data.preferences.SearchToolPreferences
import com.searchaid.ui.navigation.SearchAidNavHost
import com.searchaid.ui.theme.SearchAidTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var searchToolPreferences: SearchToolPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val config = searchToolPreferences.config.first()
            val onboardingDone = config.onboardingCompleted
            setContent {
                SearchAidTheme {
                    SearchAidNavHost(onboardingCompleted = onboardingDone)
                }
            }
        }
    }
}
