package com.rescue911.osint.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.mock.MockData
import com.rescue911.osint.domain.model.EvidenceStatus
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold
import com.rescue911.osint.ui.components.ValidationBadge
import com.rescue911.osint.ui.components.ValidationLevelDots

@Composable
fun ManualReviewQueueScreen(navController: NavHostController, padding: PaddingValues) {
    val items = MockData.evidence.filter { it.status == EvidenceStatus.NEEDS_REVIEW || it.status == EvidenceStatus.CORROBORATED }
    ScreenScaffold(stringResource(R.string.nav_review), padding) {
        items.forEach { e ->
            InfoCard(
                title = e.title ?: e.provider,
                body = e.snippet ?: "",
                trailing = { ValidationBadge(e.status) },
            )
            ValidationLevelDots(e.validation, modifier = Modifier.padding(start = 32.dp, top = 0.dp, end = 16.dp))
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {}, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_confirm))
                }
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_reject))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_needs_more))
                }
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_escalate))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
