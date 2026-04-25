package com.rescue911.osint.feature.providers

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.remote.Rescue911Api
import com.rescue911.osint.data.remote.dto.ProviderInfoDto
import com.rescue911.osint.data.remote.dto.ProviderModeDto
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val FALLBACK = listOf(
    ProviderInfoDto("Brave web search", "web_search", ProviderModeDto.MOCK, "No API key configured"),
    ProviderInfoDto("Google CSE", "web_search", ProviderModeDto.MOCK, "No API key configured"),
    ProviderInfoDto("Wayback CDX", "archive", ProviderModeDto.READY, "Public, no key required"),
    ProviderInfoDto("Common Crawl", "archive", ProviderModeDto.STUB, "TODO: implement CDXJ"),
    ProviderInfoDto("Telegram public", "social", ProviderModeDto.MOCK, "No api_id/api_hash"),
    ProviderInfoDto("YouTube Data v3", "social", ProviderModeDto.MOCK, "No API key"),
    ProviderInfoDto("GeoSeer", "geoint", ProviderModeDto.MOCK, "No API key"),
    ProviderInfoDto("Picarta", "geoint", ProviderModeDto.MOCK, "No API key"),
    ProviderInfoDto("Google Vision", "geoint", ProviderModeDto.MOCK, "No GOOGLE_APPLICATION_CREDENTIALS"),
    ProviderInfoDto("Azure Vision", "geoint", ProviderModeDto.MOCK, "No endpoint/key"),
    ProviderInfoDto("OpenAI Vision reasoner", "geoint", ProviderModeDto.MOCK, "No API key"),
    ProviderInfoDto("Google Maps", "maps", ProviderModeDto.MOCK, "No API key"),
    ProviderInfoDto("LocationIQ", "maps", ProviderModeDto.MOCK, "No API key"),
    ProviderInfoDto("OSM Nominatim", "maps", ProviderModeDto.READY, "Public; respect rate limit"),
)

@HiltViewModel
class ProviderStatusViewModel @Inject constructor(
    private val api: Rescue911Api,
) : ViewModel() {
    private val _providers = MutableStateFlow<List<ProviderInfoDto>>(FALLBACK)
    val providers: StateFlow<List<ProviderInfoDto>> = _providers.asStateFlow()

    private val _source = MutableStateFlow("fallback")
    val source: StateFlow<String> = _source.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { api.providerStatus() }
            .onSuccess {
                _providers.value = it.providers
                _source.value = "backend"
            }
            .onFailure {
                _providers.value = FALLBACK
                _source.value = "fallback"
            }
    }
}

@Composable
fun ProviderStatusScreen(
    navController: NavHostController,
    padding: PaddingValues,
    vm: ProviderStatusViewModel = hiltViewModel(),
) {
    val list by vm.providers.collectAsState()
    val source by vm.source.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }

    ScreenScaffold(stringResource(R.string.nav_providers), padding) {
        Text(
            text = if (source == "backend")
                stringResource(R.string.providers_source_backend)
            else
                stringResource(R.string.providers_source_fallback),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        list.forEach { p ->
            InfoCard(
                title = p.name,
                body = (p.note ?: "") + " · " + p.category,
                trailing = { Text(p.mode.name) },
            )
        }
    }
}
