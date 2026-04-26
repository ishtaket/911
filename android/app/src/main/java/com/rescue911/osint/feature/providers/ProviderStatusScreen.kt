package com.rescue911.osint.feature.providers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.preferences.AppPreferences
import com.rescue911.osint.data.remote.Rescue911Api
import com.rescue911.osint.data.remote.dto.ProviderInfoV2Dto
import com.rescue911.osint.data.remote.dto.ProviderStateDto
import com.rescue911.osint.ui.components.ActionResultBanner
import com.rescue911.osint.ui.components.BannerKind
import com.rescue911.osint.ui.components.EmptyState
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ProviderStatusViewModel @Inject constructor(
    private val api: Rescue911Api,
    private val prefs: AppPreferences,
) : ViewModel() {
    private val _providers = MutableStateFlow<List<ProviderInfoV2Dto>?>(null)
    val providers: StateFlow<List<ProviderInfoV2Dto>?> = _providers.asStateFlow()

    private val _banner = MutableStateFlow<Pair<String, BannerKind>?>(null)
    val banner: StateFlow<Pair<String, BannerKind>?> = _banner.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { api.listProviders() }
            .onSuccess {
                _providers.value = it.providers
                _banner.value = if (it.mockProviders) {
                    "MOCK_PROVIDERS=true on backend — running deterministic mock providers" to BannerKind.WARNING
                } else null
            }
            .onFailure {
                _providers.value = emptyList()
                _banner.value = "Backend unreachable: ${it.javaClass.simpleName}" to BannerKind.ERROR
            }
    }

    /** Resolves an absolute connect URL by combining the operator's configured
     *  API base URL with the provider's relative `connect_url`. Backend owns
     *  the OAuth flow; Android only opens it. */
    suspend fun resolveConnectUrl(p: ProviderInfoV2Dto): String? {
        val rel = p.connectUrl ?: return null
        val base = prefs.apiBaseUrl.first().trimEnd('/')
        return base + (if (rel.startsWith("/")) rel else "/$rel")
    }
}

@Composable
fun ProviderStatusScreen(
    navController: NavHostController,
    padding: PaddingValues,
    vm: ProviderStatusViewModel = hiltViewModel(),
) {
    val list by vm.providers.collectAsState()
    val banner by vm.banner.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { vm.refresh() }

    ScreenScaffold(stringResource(R.string.nav_providers), padding) {
        banner?.let { (msg, kind) -> ActionResultBanner(message = msg, kind = kind) }
        when {
            list == null -> EmptyState(stringResource(R.string.state_loading))
            list!!.isEmpty() -> EmptyState(stringResource(R.string.providers_empty))
            else -> Column(Modifier.fillMaxWidth()) {
                list!!.forEach { p ->
                    ProviderRow(p, vm = vm, ctx = ctx)
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    p: ProviderInfoV2Dto,
    vm: ProviderStatusViewModel,
    ctx: android.content.Context,
) {
    val stateLabel = when (p.state) {
        ProviderStateDto.CONNECTED -> stringResource(R.string.provider_state_connected)
        ProviderStateDto.MOCK -> stringResource(R.string.provider_state_mock)
        ProviderStateDto.NOT_CONFIGURED -> stringResource(R.string.provider_state_not_configured)
        ProviderStateDto.AUTH_REQUIRED -> stringResource(R.string.provider_state_auth_required)
        ProviderStateDto.RATE_LIMITED -> stringResource(R.string.provider_state_rate_limited)
        ProviderStateDto.ERROR -> stringResource(R.string.provider_state_error)
        ProviderStateDto.DISABLED -> stringResource(R.string.provider_state_disabled)
        ProviderStateDto.UNAVAILABLE -> stringResource(R.string.provider_state_unavailable)
        ProviderStateDto.MANUAL_UI_REQUIRED -> stringResource(R.string.provider_state_manual_ui_required)
    }
    InfoCard(
        title = "${p.displayName} — $stateLabel",
        body = "${p.safeScopeDescription}\nauth: ${p.authType.name.lowercase()} · type: ${p.type}",
        trailing = {
            // Only OAuth providers get a "Open connect page" affordance, and we
            // open it via Custom Tabs — Android never holds the secret.
            if (p.requiresUserAction && p.connectUrl != null) {
                OutlinedButton(onClick = {
                    vm.viewModelScope.launch {
                        val url = vm.resolveConnectUrl(p) ?: return@launch
                        runCatching {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url),
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                        }
                    }
                }) { Text(stringResource(R.string.provider_action_connect)) }
            }
        },
    )
    Spacer(Modifier.height(2.dp))
    if (p.lastError != null) {
        Text(
            "last error: ${p.lastError}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 32.dp, bottom = 6.dp),
        )
    }
}
