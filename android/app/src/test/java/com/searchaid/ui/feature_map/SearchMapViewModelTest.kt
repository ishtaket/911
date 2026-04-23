package com.searchaid.ui.feature_map

import androidx.lifecycle.SavedStateHandle
import com.searchaid.MainDispatcherRule
import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.SearchZone
import com.searchaid.domain.signal.HeatMapDataBuilder
import com.searchaid.domain.signal.SignalScorer
import com.searchaid.domain.signal.ZoneGenerator
import com.searchaid.domain.usecase.AggregateSignalsUseCase
import com.searchaid.domain.usecase.GetHistoricalPlacesUseCase
import com.searchaid.domain.usecase.GetLeadsByCaseUseCase
import com.searchaid.domain.usecase.GetMissingCaseUseCase
import com.searchaid.domain.usecase.GetSearchZonesByCaseUseCase
import com.searchaid.domain.usecase.GetWitnessReportsByCaseUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.MarkZoneCheckedUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchMapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getCase = mockk<GetMissingCaseUseCase>()
    private val getZones = mockk<GetSearchZonesByCaseUseCase>()
    private val getLeads = mockk<GetLeadsByCaseUseCase>()
    private val getReports = mockk<GetWitnessReportsByCaseUseCase>()
    private val getHistoricalPlaces = mockk<GetHistoricalPlacesUseCase>()
    private val aggregateSignals = AggregateSignalsUseCase(SignalScorer(), ZoneGenerator())
    private val markZoneChecked = mockk<MarkZoneCheckedUseCase>(relaxed = true)
    private val logAction = mockk<LogActionUseCase>(relaxed = true)

    private val testCase = MissingCase(
        id = 10, personId = 1, status = CaseStatus.ACTIVE,
        createdAt = 1000L, lastSeenTime = 900L,
        lastSeenLocationName = "Park", lastSeenLat = 55.75, lastSeenLon = 37.61,
        clothesDescription = "Blue jacket", notes = null, operatorId = null,
    )

    private val testZone = SearchZone(
        id = 1, caseId = 10, lat = 55.76, lon = 37.62,
        radiusMeters = 500.0, score = 0.8f, reason = "Historical place",
    )

    private fun createViewModel(): SearchMapViewModel {
        coEvery { getCase(10L) } returns testCase
        every { getZones(10L) } returns flowOf(listOf(testZone))
        every { getLeads(10L) } returns flowOf(emptyList())
        every { getReports(10L) } returns flowOf(emptyList())
        every { getHistoricalPlaces(1L) } returns flowOf(emptyList())
        return SearchMapViewModel(
            SavedStateHandle(mapOf("caseId" to 10L)),
            getCase, getZones, getLeads, getReports,
            getHistoricalPlaces, aggregateSignals,
            markZoneChecked, logAction, HeatMapDataBuilder(),
        )
    }

    @Test
    fun `init loads case and zones`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertNotNull(vm.state.value.case_)
        assertEquals("Park", vm.state.value.case_?.lastSeenLocationName)
        assertEquals(1, vm.state.value.zones.size)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `init generates suggested zones from last seen`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        // Last seen location should produce at least one suggested zone
        assertTrue(vm.state.value.suggestedZones.isNotEmpty())
    }

    @Test
    fun `onZoneChecked calls markZoneChecked and logs`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onZoneChecked(1L)
        advanceUntilIdle()

        coVerify { markZoneChecked(1L) }
        coVerify { logAction("ZONE_CHECKED", caseId = 10L, details = any()) }
    }

    @Test
    fun `state reflects empty zones when none exist`() = runTest {
        coEvery { getCase(10L) } returns testCase
        every { getZones(10L) } returns flowOf(emptyList())
        every { getLeads(10L) } returns flowOf(emptyList())
        every { getReports(10L) } returns flowOf(emptyList())
        every { getHistoricalPlaces(1L) } returns flowOf(emptyList())
        val vm = SearchMapViewModel(
            SavedStateHandle(mapOf("caseId" to 10L)),
            getCase, getZones, getLeads, getReports,
            getHistoricalPlaces, aggregateSignals,
            markZoneChecked, logAction, HeatMapDataBuilder(),
        )
        advanceUntilIdle()

        assertEquals(0, vm.state.value.zones.size)
    }

    @Test
    fun `no case produces empty state`() = runTest {
        coEvery { getCase(10L) } returns null
        every { getZones(10L) } returns flowOf(emptyList())
        val vm = SearchMapViewModel(
            SavedStateHandle(mapOf("caseId" to 10L)),
            getCase, getZones, getLeads, getReports,
            getHistoricalPlaces, aggregateSignals,
            markZoneChecked, logAction, HeatMapDataBuilder(),
        )
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.suggestedZones.isEmpty())
    }

    // ---- Heat Map L2 Tests ----

    @Test
    fun `heat map is disabled by default`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.state.value.heatMapEnabled)
    }

    @Test
    fun `onToggleHeatMap enables heat map`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onToggleHeatMap()
        assertTrue(vm.state.value.heatMapEnabled)
    }

    @Test
    fun `onToggleHeatMap twice disables heat map`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onToggleHeatMap()
        vm.onToggleHeatMap()
        assertFalse(vm.state.value.heatMapEnabled)
    }

    @Test
    fun `heat map points are generated from suggested zones`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        // Case has last seen location, so signals exist → heat map points should be generated
        assertTrue(vm.state.value.heatMapPoints.isNotEmpty())
    }

    @Test
    fun `no case produces empty heat map points`() = runTest {
        coEvery { getCase(10L) } returns null
        every { getZones(10L) } returns flowOf(emptyList())
        val vm = SearchMapViewModel(
            SavedStateHandle(mapOf("caseId" to 10L)),
            getCase, getZones, getLeads, getReports,
            getHistoricalPlaces, aggregateSignals,
            markZoneChecked, logAction, HeatMapDataBuilder(),
        )
        advanceUntilIdle()

        assertTrue(vm.state.value.heatMapPoints.isEmpty())
    }
}
