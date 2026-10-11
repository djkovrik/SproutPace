package com.sedsoftware.sproutpace.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.sedsoftware.sproutpace.component.RecipeGuideProjection
import com.sedsoftware.sproutpace.component.RecipeStageProjection
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

@Composable
internal fun RecipeScreen(
    state: SproutPaceUiState,
    layout: UiLayout,
    onAction: (UiAction) -> Unit,
    readOnlyPane: Boolean = false,
    initialSourceError: Boolean = false,
    initialEditing: Boolean = false,
) {
    val item = state.items.firstOrNull { it.recipe != null }
    val guide = item?.recipe
    var editing by rememberSaveable(item?.id) { mutableStateOf(initialEditing) }
    var copying by rememberSaveable(item?.id) { mutableStateOf(false) }
    if (guide == null || editing || copying) {
        RecipeEditor(state, item, copying, onAction)
        return
    }
    val content: @Composable (Modifier) -> Unit = { modifier ->
        LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { RecipeHeader(requireNotNull(item), guide) }
            item {
                Button(
                    onClick = { onAction(UiAction.Submit("use-recipe", item?.id)) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { testTag = "action-use-recipe" },
                ) { Text(localizedText("action_use_recipe")) }
            }
            if (!readOnlyPane) item {
                RecipeContextActions(guide, state.busy, onEdit = { editing = true }, onCopy = {
                    if (guide.readOnly) copying = true else onAction(UiAction.Submit("duplicate-recipe", item?.id))
                }, onDelete = { onAction(UiAction.Submit("delete-recipe", item?.id)) })
            }
            state.errorKey?.let { key -> item { Text(localizedText(key), color = MaterialTheme.colorScheme.error) } }
            item { RecipeSectionTitle("recipe_stages_heading") }
            items(guide.stages, key = { it.titleKey.value }) { stage -> RecipeStageCard(stage) }
            if (layout < UiLayout.EXPANDED) item { RecipeProvenance(guide, initialSourceError) }
        }
    }
    if (layout >= UiLayout.EXPANDED) {
        Row(Modifier.fillMaxSize()) {
            content(Modifier.weight(RECIPE_MAIN_PANE_WEIGHT))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
                item { RecipeProvenance(guide, initialSourceError) }
            }
        }
    } else {
        content(Modifier.fillMaxSize())
    }
}

@Composable
private fun RecipeHeader(item: UiItem, guide: RecipeGuideProjection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        guide.cultureId?.let(::cultureImage)?.let { image ->
            Image(painterResource(image), null, Modifier.fillMaxWidth().height(156.dp), contentScale = ContentScale.Crop)
        }
        Text(localizedOrAuthored(item.titleKey, item.authoredTitle), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        guide.scientificNameKey?.let { Text(localizedText(it.value), style = MaterialTheme.typography.bodyMedium) }
        Text(localizedText(if (guide.readOnly) "recipe_built_in_read_only" else "recipe_personal_heading"),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val minimumHours = guide.stages.sumOf { it.minimumHours }
        val maximumHours = guide.stages.sumOf { it.maximumHours }
        val wholeDays = minimumHours % HOURS_PER_DAY == 0L && maximumHours % HOURS_PER_DAY == 0L
        val duration = stringResource(strictTextResource(if (wholeDays) "recipe_duration_days" else "recipe_duration_hours"),
            (if (wholeDays) minimumHours / HOURS_PER_DAY else minimumHours).toString(),
            (if (wholeDays) maximumHours / HOURS_PER_DAY else maximumHours).toString())
        Text(stringResource(strictTextResource("recipe_total_duration"), duration), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(strictTextResource("recipe_density_value"),
            localizedDecimal((round(guide.gramsPerSquareCentimetre * DENSITY_DISPLAY_SCALE) /
                DENSITY_ROUNDING_SCALE).toString())))
    }
}

@Composable
private fun RecipeContextActions(
    guide: RecipeGuideProjection,
    busy: Boolean,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (!guide.readOnly) {
            OutlinedButton(onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                LabeledActionContent("update-recipe", "recipe_edit_action")
            }
        }
        TextButton(onCopy, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(localizedText(if (guide.readOnly) "recipe_create_copy_action" else "action_duplicate_recipe"))
        }
        if (!guide.readOnly) {
            TextButton(onDelete, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                LabeledActionContent("delete-recipe", "action_delete_recipe")
            }
        }
    }
}

@Composable
private fun RecipeStageCard(stage: RecipeStageProjection) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StageIllustration(stage.titleKey.value)
            Text(localizedText(stage.titleKey.value), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(strictTextResource("recipe_duration_hours"), stage.minimumHours.toString(),
                stage.maximumHours.toString()), style = MaterialTheme.typography.labelLarge)
            Text(localizedOrAuthored(stage.guidanceKey?.value, stage.authoredGuidance),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RecipeProvenance(guide: RecipeGuideProjection, initialSourceError: Boolean) {
    val uriHandler = LocalUriHandler.current
    var sourceError by rememberSaveable(guide.cultureId) { mutableStateOf(initialSourceError) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RecipeSectionTitle("recipe_details_heading")
        if (guide.substrateKey != null || guide.authoredSubstrate != null) {
            Text(localizedOrAuthored(guide.substrateKey?.value, guide.authoredSubstrate))
        }
        guide.lightingKey?.let { Text(localizedText(it.value)) }
        if (guide.safetyKeys.isNotEmpty()) {
            RecipeSectionTitle("recipe_safety_title")
            guide.safetyKeys.forEach { Text(localizedText(it.value), style = MaterialTheme.typography.bodyMedium) }
        }
        RecipeSectionTitle("recipe_sources_heading")
        Text(stringResource(strictTextResource("recipe_version_value"), guide.version.toString()))
        Text(stringResource(strictTextResource("recipe_reviewed_value"), localizedDate(LocalDate.parse(guide.reviewedOn))))
        if (guide.reviewOutdated) {
            Text(localizedText("recipe_review_outdated"), color = MaterialTheme.colorScheme.error)
        }
        guide.sources.forEach { source ->
            OutlinedButton(onClick = {
                sourceError = runCatching { uriHandler.openUri(source.url) }.isFailure
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(localizedText(source.titleKey.value))
                    Text(source.url, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (sourceError) Text(localizedText("recipe_source_open_failed"), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun RecipeEditor(
    state: SproutPaceUiState,
    item: UiItem?,
    copying: Boolean,
    onAction: (UiAction) -> Unit,
) {
    val fields = recipeEditorFields(state.fields, copying)
    val firstError = fields.indexOfFirst { it.errorKey != null }
    val listState = rememberLazyListState()
    val errorFocus = remember { FocusRequester() }
    LaunchedEffect(firstError, fields.getOrNull(firstError)?.errorKey) {
        if (firstError >= 0) {
            listState.scrollToItem(firstError + 1 + if (state.errorKey == null) 0 else 1)
            withFrameNanos { }
            errorFocus.requestFocus()
        }
    }
    val operation = when {
        copying -> "duplicate-recipe"
        item != null -> "update-recipe"
        else -> "save-recipe"
    }
    val canCopy = !copying || fields.all { it.value.isNotBlank() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    RecipeSectionTitle(if (copying) "recipe_create_copy_action" else "recipe_editor_heading")
                    if (copying) Text(localizedText("recipe_copy_details"))
                }
                state.errorKey?.let { key -> item { Text(localizedText(key), color = MaterialTheme.colorScheme.error) } }
                itemsIndexed(fields, key = { _, field -> field.id }) { index, field ->
                    Box(if (index == firstError) Modifier.focusRequester(errorFocus) else Modifier) {
                        FieldControl(field, onAction)
                    }
                }
            }
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAction(UiAction.Submit(operation, item?.id)) },
                        enabled = !state.busy && canCopy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .semantics { testTag = "action-$operation" }) {
                        Text(localizedText(if (copying) "recipe_create_copy_action" else "action_save_recipe"))
                    }
                    TextButton(onClick = { onAction(UiAction.Back) }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(localizedText("temporal_cancel"))
                    }
                }
            }
        }
    }
}

internal fun recipeEditorFields(fields: List<UiField>, copying: Boolean): List<UiField> = fields.filter { field ->
    when {
        copying -> field.id in setOf("name", "substrate")
        field.id in setOf("id", "newId") -> false
        else -> {
            val stage = Regex("^(stage[1-5])(MinimumHours|MaximumHours|Guidance)$").matchEntire(field.id)?.groupValues?.get(1)
            stage == null || fields.firstOrNull { it.id == stage }?.value?.let { it.isNotBlank() && it != "none" } == true
        }
    }
}

@Composable
private fun RecipeSectionTitle(key: String) {
    Text(localizedText(key), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

private const val HOURS_PER_DAY = 24
private const val RECIPE_MAIN_PANE_WEIGHT = 1.5f
private const val DENSITY_DISPLAY_SCALE = 100_000
private const val DENSITY_ROUNDING_SCALE = 1000
