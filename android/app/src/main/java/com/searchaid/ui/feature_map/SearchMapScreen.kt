package com.searchaid.ui.feature_map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.ReportStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchMapScreen(
    onBack: () -> Unit,
    viewModel: SearchMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchMapScreenContent(
        state = state,
        onBack = onBack,
        onZoneChecked = viewModel::onZoneChecked,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchMapScreenContent(
    state: SearchMapState,
    onBack: () -> Unit,
    onZoneChecked: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                val case_ = state.case_
                val initialPosition = remember(case_) {
                    if (case_?.lastSeenLat != null && case_.lastSeenLon != null) {
                        LatLng(case_.lastSeenLat, case_.lastSeenLon)
                    } else {
                        LatLng(55.75, 37.61) // Default: Moscow
                    }
                }

                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(initialPosition, 14f)
                }

                // Legend
                MapLegend(
                    zonesCount = state.zones.size,
                    leadsCount = state.leads.size,
                    reportsCount = state.reports.size,
                )

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                ) {
                    // Last seen marker (red)
                    if (case_?.lastSeenLat != null && case_.lastSeenLon != null) {
                        Marker(
                            state = MarkerState(position = LatLng(case_.lastSeenLat, case_.lastSeenLon)),
                            title = "Last Seen",
                            snippet = case_.lastSeenLocationName ?: "Unknown location",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                        )
                    }

                    // Search zones (circles)
                    state.zones.forEach { zone ->
                        val color = if (zone.checked) {
                            Color(0x3000C853) // Green, checked
                        } else {
                            Color((0x30FF0000 + (zone.score * 0xCC).toInt() * 0x10000).toLong())
                                .copy(alpha = 0.2f + zone.score * 0.3f)
                        }
                        Circle(
                            center = LatLng(zone.lat, zone.lon),
                            radius = zone.radiusMeters,
                            fillColor = if (zone.checked) Color(0x3000C853) else Color(0x30FF6D00),
                            strokeColor = if (zone.checked) Color(0xFF00C853) else Color(0xFFFF6D00),
                            strokeWidth = 2f,
                            clickable = !zone.checked,
                            onClick = { onZoneChecked(zone.id) },
                        )
                    }

                    // Lead markers (blue)
                    state.leads.filter { it.lat != null && it.lon != null }
                        .filter { it.status != LeadStatus.REJECTED }
                        .forEach { lead ->
                            Marker(
                                state = MarkerState(position = LatLng(lead.lat!!, lead.lon!!)),
                                title = "Lead: ${lead.type.name}",
                                snippet = lead.textSnippet ?: lead.possibleLocationName ?: "",
                                icon = BitmapDescriptorFactory.defaultMarker(
                                    if (lead.status == LeadStatus.CONFIRMED) BitmapDescriptorFactory.HUE_GREEN
                                    else BitmapDescriptorFactory.HUE_AZURE,
                                ),
                            )
                        }

                    // Witness report markers (yellow)
                    state.reports.filter { it.lat != null && it.lon != null }
                        .filter { it.status != ReportStatus.REJECTED }
                        .forEach { report ->
                            Marker(
                                state = MarkerState(position = LatLng(report.lat!!, report.lon!!)),
                                title = "Witness: ${report.sourceName ?: "Anonymous"}",
                                snippet = report.text.take(80),
                                icon = BitmapDescriptorFactory.defaultMarker(
                                    if (report.status == ReportStatus.VERIFIED) BitmapDescriptorFactory.HUE_GREEN
                                    else BitmapDescriptorFactory.HUE_YELLOW,
                                ),
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun MapLegend(zonesCount: Int, leadsCount: Int, reportsCount: Int) {
    if (zonesCount == 0 && leadsCount == 0 && reportsCount == 0) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            "Zones: $zonesCount | Leads: $leadsCount | Reports: $reportsCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
