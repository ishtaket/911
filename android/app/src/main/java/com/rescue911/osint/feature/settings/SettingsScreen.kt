package com.rescue911.osint.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.core.backend.BackendStatus
import com.rescue911.osint.core.backend.BackendStatusMonitor
import com.rescue911.osint.data.preferences.AppPreferences
import com.rescue911.osint.navigation.Routes
import com.rescue911.osint.ui.components.ScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    val backendStatus: BackendStatusMonitor,
) : ViewModel() {
    suspend fun current(): Triple<String, String, Boolean> = Triple(
        prefs.apiBaseUrl.first(),
        prefs.language.first(),
        prefs.mockMode.first(),
    )

    fun setApiBase(value: String) = viewModelScope.launch { prefs.setApiBaseUrl(value) }
    fun setLanguage(value: String) = viewModelScope.launch { prefs.setLanguage(value) }
    fun setMock(value: Boolean) = viewModelScope.launch { prefs.setMockMode(value) }
    fun checkBackend() = viewModelScope.launch { backendStatus.check() }
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
    val status by vm.backendStatus.state.collectAsState()

    LaunchedEffect(Unit) {
        val (a, l, m) = vm.current()
        apiBase = a; language = l; mock = m
    }

    ScreenScaffold(stringResource(R.string.nav_settings), padding) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {

            // ---- Backend status row ----
            BackendStatusRow(
                status = status,
                modeLabel = if (mock) stringResource(R.string.settings_data_source_mock)
                else stringResource(R.string.settings_data_source_backend),
                onCheck = { vm.checkBackend() },
            )

            Spacer(Modifier.height(12.dp))

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

@Composable
private fun BackendStatusRow(
    status: BackendStatus,
    modeLabel: String,
    onCheck: () -> Unit,
) {
    val (label, color) = when (status) {
        is BackendStatus.Online -> stringResource(R.string.settings_backend_online) to Color(0xFF2E7D32)
        is BackendStatus.Offline -> stringResource(R.string.settings_backend_offline) to Color(0xFFC62828)
        is BackendStatus.Checking -> stringResource(R.string.settings_backend_checking) to Color(0xFFEF6C00)
        BackendStatus.Unknown -> stringResource(R.string.settings_backend_unknown) to Color(0xFF616161)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_backend_status), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .background(color, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_data_source) + ": " + modeLabel,
                style = MaterialTheme.typography.bodySmall,
            )
            if (status is BackendStatus.Offline) {
                Text(status.reason, style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
            }
        }
        Button(onClick = onCheck) { Text(stringResource(R.string.settings_backend_check)) }
    }
}
