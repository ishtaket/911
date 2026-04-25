package com.rescue911.osint.feature.providers

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold

private data class ProviderRow(val name: String, val mode: String, val note: String)

@Composable
fun ProviderStatusScreen(navController: NavHostController, padding: PaddingValues) {
    val providers = listOf(
        ProviderRow("Brave web search", "MOCK", "No API key configured"),
        ProviderRow("Google CSE", "MOCK", "No API key configured"),
        ProviderRow("Wayback CDX", "READY", "Public, no key required"),
        ProviderRow("Common Crawl", "STUB", "TODO: implement CDXJ"),
        ProviderRow("Telegram public", "MOCK", "No api_id/api_hash"),
        ProviderRow("YouTube Data v3", "MOCK", "No API key"),
        ProviderRow("GeoSeer", "MOCK", "No API key"),
        ProviderRow("Picarta", "MOCK", "No API key"),
        ProviderRow("Google Vision", "MOCK", "No GOOGLE_APPLICATION_CREDENTIALS"),
        ProviderRow("Azure Vision", "MOCK", "No endpoint/key"),
        ProviderRow("OpenAI Vision reasoner", "MOCK", "No API key"),
        ProviderRow("Google Maps", "MOCK", "No API key"),
        ProviderRow("LocationIQ", "MOCK", "No API key"),
        ProviderRow("OSM Nominatim", "READY", "Public; respect rate limit"),
    )
    ScreenScaffold(stringResource(R.string.nav_providers), padding) {
        providers.forEach {
            InfoCard(title = it.name, body = it.note, trailing = { androidx.compose.material3.Text(it.mode) })
        }
    }
}
