package com.rescue911.osint.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rescue911.osint.domain.model.EvidenceStatus
import com.rescue911.osint.domain.model.RiskLevel
import com.rescue911.osint.ui.components.RiskChip
import com.rescue911.osint.ui.components.ValidationBadge
import com.rescue911.osint.ui.theme.Rescue911Theme
import org.junit.Rule
import org.junit.Test

class ComponentsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun validationBadge_renders_for_human_confirmed() {
        composeTestRule.setContent {
            Rescue911Theme {
                ValidationBadge(EvidenceStatus.HUMAN_CONFIRMED)
            }
        }
        composeTestRule.onNodeWithText("Human Confirmed").assertIsDisplayed()
    }

    @Test
    fun riskChip_renders_for_critical() {
        composeTestRule.setContent {
            Rescue911Theme {
                RiskChip(RiskLevel.CRITICAL)
            }
        }
        composeTestRule.onNodeWithText("CRITICAL").assertIsDisplayed()
    }
}
