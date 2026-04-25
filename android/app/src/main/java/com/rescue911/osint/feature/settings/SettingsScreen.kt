package com.rescue911.osint.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.preferences.AppPreferences
import com.rescue911.osint.navigation.Routes
import com.rescue911.osint.ui.components.ScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
) : ViewModel() {
    suspend fun current(): Triple<String, String, Boolean> = Triple(
        prefs.apiBaseUrl.first(),
        prefs.language.first(),
        prefs.mockMode.first(),
    )

    fun setApiBase(value: String) = viewModelScope.launch { prefs.setApiBaseUrl(value) }
    fun setLanguage(value: String) = viewModelScope.launch { prefs.setLanguage(value) }
    fun setMock(value: Boolean) = viewModelScope.launch { prefs.setMockMode(value) }
}

@Composable
fun SettingsScreen(
    navController: NavHostController,
    padding: PaddingValues,
    vm: SettingsViewModel = hiltViewModel(),
) {
    var apiBase by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("en") }
    var mock by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val (a, l, m) = vm.current(); apiBase = a; language = l; mock = m
    }
    ScreenScaffold(stringResource(R.string.nav_settings), padding) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = apiBase,
                onValueChange = { apiBase = it; vm.setApiBase(it) },
                label = { Text(stringResource(R.string.settings_api_base)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en", "he", "ru").forEach { code ->
                    OutlinedButton(
                        onClick = { language = code; vm.setLanguage(code) },
                        modifier = Modifier.weight(1f),
                    ) { Text(code.uppercase()) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_mock_mode),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(checked = mock, onCheckedChange = { mock = it; vm.setMock(it) })
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { navController.navigate(Routes.PROVIDERS) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_open_providers)) }
        }
    }
}
