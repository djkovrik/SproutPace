package com.sedsoftware.sproutpace.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sedsoftware.sproutpace.component.PlanEntryStatus
import com.sedsoftware.sproutpace.component.ZoneOccupancy
import org.jetbrains.compose.resources.stringResource
import sproutpace.shared.compose.generated.resources.*

@Composable
internal fun ProjectionContent(item: UiItem) {
    item.batch?.let { batch ->
        Text(batch.trayName, style = MaterialTheme.typography.bodyLarge)
        batch.zoneName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        batch.sowDate?.let { Text(stringResource(Res.string.projection_sown, localizedDate(it))) }
        batch.closedAt?.let { Text(stringResource(Res.string.projection_closed, localizedInstant(it))) }
    }
    item.task?.let { task ->
        Text(
            stringResource(Res.string.projection_due, localizedInstant(task.dueAt)),
            color = if (task.overdue) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (task.overdue) Text(localizedText("today_overdue_group"))
        if (task.fixed) Text(localizedText("projection_fixed"))
    }
    item.occupancy?.let { OccupancyContent(it, showName = false) }
    item.planEntry?.let { entry ->
        if (item.batch == null) entry.sowDate?.let { Text(stringResource(Res.string.projection_sown, localizedDate(it))) }
        entry.harvestDate?.let { Text(stringResource(Res.string.projection_harvest, localizedDate(it))) }
        Text(localizedText(when (entry.status) {
            PlanEntryStatus.CONFIRMED -> "projection_confirmed"
            PlanEntryStatus.SUGGESTED -> "projection_suggested"
            PlanEntryStatus.NEEDS_RESOLUTION -> "projection_needs_resolution"
        }))
        entry.zone?.let { OccupancyContent(it) }
    }
}

@Composable
internal fun OccupancyContent(zone: ZoneOccupancy, showName: Boolean = true) {
    if (showName) Text(zone.zoneName, style = MaterialTheme.typography.titleSmall)
    Text(localizedText("option_zone_type_${zone.type.name.lowercase()}"), style = MaterialTheme.typography.labelMedium)
    Text(stringResource(Res.string.projection_occupancy, zone.occupied, zone.capacity, zone.available))
    if (zone.overflow > 0) {
        Text(stringResource(Res.string.projection_overflow, zone.overflow), color = MaterialTheme.colorScheme.error)
    }
    zone.futureConflict?.let { forecast ->
        Text(stringResource(Res.string.zone_future_conflict, localizedDate(forecast.date),
            forecast.projectedOccupied, forecast.capacity), color = MaterialTheme.colorScheme.error)
        Text(localizedText("zone_future_conflict_basis"), style = MaterialTheme.typography.bodySmall)
    }
}
