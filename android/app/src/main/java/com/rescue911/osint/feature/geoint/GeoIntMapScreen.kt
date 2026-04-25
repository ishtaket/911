package com.rescue911.osint.feature.geoint

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.mock.MockData
import com.rescue911.osint.ui.components.HypothesisBriefRow
import com.rescue911.osint.ui.components.ScreenScaffold

@Composable
fun GeoIntMapScreen(navController: NavHostController, padding: PaddingValues, caseId: String) {
    val hypotheses = MockData.hypotheses.filter { caseId.isBlank() || it.caseId == caseId }
    ScreenScaffold(stringResource(R.string.nav_geoint), padding) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.map_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            hypotheses.forEach { HypothesisBriefRow(it) }
        }
    }
}
