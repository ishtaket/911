package com.rescue911.osint.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rescue911.osint.domain.model.EvidenceStatus
import com.rescue911.osint.domain.model.Hypothesis
import com.rescue911.osint.domain.model.RiskLevel
import com.rescue911.osint.domain.model.ValidationState
import com.rescue911.osint.ui.theme.Critical
import com.rescue911.osint.ui.theme.Gold
import com.rescue911.osint.ui.theme.MutedText
import com.rescue911.osint.ui.theme.Success

@Composable
fun ValidationBadge(status: EvidenceStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        EvidenceStatus.CANDIDATE -> "Candidate" to MutedText
        EvidenceStatus.NEEDS_REVIEW -> "Needs Review" to Gold
        EvidenceStatus.CORROBORATED -> "Corroborated" to Success
        EvidenceStatus.REJECTED -> "Rejected" to Critical
        EvidenceStatus.HUMAN_CONFIRMED -> "Human Confirmed" to Success
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .semantics { contentDescription = "Validation status: $label" },
        color = color.copy(alpha = 0.18f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
fun ValidationLevelDots(state: ValidationState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Dot(passed = state.level1.isPassed, label = "L1")
        Spacer(Modifier.width(4.dp))
        Dot(passed = state.level2.isPassed, label = "L2")
        Spacer(Modifier.width(4.dp))
        Dot(passed = state.isHumanConfirmed, label = "L3")
    }
}

@Composable
private fun Dot(passed: Boolean, label: String) {
    val color = if (passed) Success else MutedText
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun RiskChip(risk: RiskLevel, modifier: Modifier = Modifier) {
    val color = when (risk) {
        RiskLevel.LOW -> Success
        RiskLevel.MEDIUM -> Gold
        RiskLevel.HIGH, RiskLevel.CRITICAL -> Critical
    }
    val icon = when (risk) {
        RiskLevel.LOW -> Icons.Filled.CheckCircle
        RiskLevel.MEDIUM -> Icons.Filled.Schedule
        RiskLevel.HIGH, RiskLevel.CRITICAL -> Icons.Filled.WarningAmber
    }
    Surface(
        modifier = modifier.clip(RoundedCornerShape(50)),
        color = color.copy(alpha = 0.18f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(risk.name, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun ScreenScaffold(
    title: String,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 0.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
        )
        content()
    }
}

@Composable
fun InfoCard(
    title: String,
    body: String,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    accentColor: Color? = null,
) {
    Card(
        onClick = { onClick?.invoke() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                accentColor?.let {
                    Box(Modifier.size(8.dp).background(it, RoundedCornerShape(50)))
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                trailing()
            }
            if (body.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

enum class DataSource { MOCK, BACKEND, FALLBACK, CHECKING }

@Composable
fun DataSourceBadge(source: DataSource, modifier: Modifier = Modifier) {
    val (label, color) = when (source) {
        DataSource.MOCK -> "Mock" to MutedText
        DataSource.BACKEND -> "Backend" to Success
        DataSource.FALLBACK -> "Fallback" to Critical
        DataSource.CHECKING -> "Checking…" to Gold
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .semantics { contentDescription = "Data source: $label" },
        color = color.copy(alpha = 0.18f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Data: $label",
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    ElevatedAssistChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier,
    )
}

@Composable
fun HypothesisBriefRow(h: Hypothesis) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text("${(h.confidence * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = Gold)
        Spacer(Modifier.width(8.dp))
        Text(h.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RiskChip(h.risk)
    }
}
