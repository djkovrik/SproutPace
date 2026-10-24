package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.sedsoftware.sproutpace.component.PlanSummaryProjection
import org.jetbrains.compose.resources.vectorResource
import sproutpace.shared.compose.generated.resources.Res
import sproutpace.shared.compose.generated.resources.ic_edit

@Composable
internal fun PlanSummaryCard(
    plan: PlanSummaryProjection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val cultureNames = plan.cultureIds.map { localizedText("culture_${it}_name") }
    Surface(
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = localizedText("action_update_plan"), onClick = onClick)
                .semantics(mergeDescendants = true) { testTag = "plan-summary-${plan.id}" }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        cultureNames.joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (plan.paused) colors.onTertiaryContainer else colors.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(6.dp),
                        ) {}
                        Text(
                            localizedText(if (plan.paused) "plan_paused" else "plan_active"),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Surface(color = colors.onTertiaryContainer.copy(alpha = 0.06f), shape = CircleShape) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(vectorResource(Res.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
            HorizontalDivider(color = colors.onTertiaryContainer.copy(alpha = 0.12f))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(localizedText("plan_frequency"), style = MaterialTheme.typography.labelLarge)
                Text(
                    localizedText("projection_cadence").replace("%1\$s", plan.cadenceDays.toString()),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
    }
}
