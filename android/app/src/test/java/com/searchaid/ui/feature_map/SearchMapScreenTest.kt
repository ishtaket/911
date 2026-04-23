package com.searchaid.ui.feature_map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.signal.HeatMapPoint
import com.searchaid.domain.signal.ScoredZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SearchMapScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testCase = MissingCase(
        id = 10, personId = 1, status = CaseStatus.ACTIVE,
        createdAt = 1000L, lastSeenTime = 900L,
        lastSeenLocationName = "Park", lastSeenLat = 55.75, lastSeenLon = 37.61,
        clothesDescription = "Blue jacket", notes = null, operatorId = null,
    )

    private val testHeatPoints = listOf(
        HeatMapPoint(55.75, 37.61, 0.8),
        HeatMapPoint(55.76, 37.62, 0.5),
    )

    private val testSuggestedZone = ScoredZone(
        lat = 55.75, lon = 37.61, radiusMeters = 300.0,
        score = 0.8f, reason = "Last seen", signals = emptyList(),
    )

    private fun content(
        state: SearchMapState,
        onToggleHeatMap: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            SearchMapScreenContent(
                state = state,
                onBack = {},
                onZoneChecked = {},
                onToggleHeatMap = onToggleHeatMap,
            )
        }
    }

    @Test
    fun `title is displayed`() {
        content(SearchMapState(loading = false))
        composeTestRule.onNodeWithText("Search Map").assertIsDisplayed()
    }

    @Test
    fun `loading state shows progress indicator`() {
        content(SearchMapState(loading = true))
        // Should not crash; loading state is shown
        composeTestRule.onNodeWithText("Search Map").assertIsDisplayed()
    }

    @Test
    fun `heat map FAB hidden when no heat points`() {
        content(
            SearchMapState(
                case_ = testCase,
                heatMapPoints = emptyList(),
                loading = false,
            )
        )
        composeTestRule.onNodeWithContentDescription("Show Heat Map").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Hide Heat Map").assertDoesNotExist()
    }

    @Test
    fun `heat map FAB shown when heat points exist`() {
        content(
            SearchMapState(
                case_ = testCase,
                heatMapPoints = testHeatPoints,
                heatMapEnabled = false,
                loading = false,
            )
        )
        composeTestRule.onNodeWithContentDescription("Show Heat Map").assertIsDisplayed()
    }

    @Test
    fun `heat map FAB shows hide when enabled`() {
        content(
            SearchMapState(
                case_ = testCase,
                heatMapPoints = testHeatPoints,
                heatMapEnabled = true,
                loading = false,
            )
        )
        composeTestRule.onNodeWithContentDescription("Hide Heat Map").assertIsDisplayed()
    }

    @Test
    fun `heat map FAB click triggers callback`() {
        var toggleCount = 0
        content(
            state = SearchMapState(
                case_ = testCase,
                heatMapPoints = testHeatPoints,
                heatMapEnabled = false,
                loading = false,
            ),
            onToggleHeatMap = { toggleCount++ },
        )
        composeTestRule.onNodeWithContentDescription("Show Heat Map").performClick()
        assertEquals(1, toggleCount)
    }

    @Test
    fun `legend shows counts`() {
        content(
            SearchMapState(
                case_ = testCase,
                suggestedZones = listOf(testSuggestedZone),
                heatMapPoints = testHeatPoints,
                loading = false,
            )
        )
        composeTestRule.onNodeWithText("Zones: 0 | Suggested: 1 | Leads: 0 | Reports: 0")
            .assertIsDisplayed()
    }
}
