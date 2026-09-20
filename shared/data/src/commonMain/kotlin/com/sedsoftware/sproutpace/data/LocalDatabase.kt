package com.sedsoftware.sproutpace.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import com.sedsoftware.sproutpace.data.db.SproutPaceDatabase
import com.sedsoftware.sproutpace.domain.contract.BatchManager
import com.sedsoftware.sproutpace.domain.contract.PlanManager
import com.sedsoftware.sproutpace.domain.contract.RecipeManager
import com.sedsoftware.sproutpace.domain.contract.SetupManager
import com.sedsoftware.sproutpace.domain.contract.domainResult
import com.sedsoftware.sproutpace.domain.model.BatchChange
import com.sedsoftware.sproutpace.domain.model.BatchId
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.GrowZoneId
import com.sedsoftware.sproutpace.domain.model.GrowZoneType
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.HarvestPlanId
import com.sedsoftware.sproutpace.domain.model.Observation
import com.sedsoftware.sproutpace.domain.model.ObservationId
import com.sedsoftware.sproutpace.domain.model.ProductSnapshot
import com.sedsoftware.sproutpace.domain.model.RecipeId
import com.sedsoftware.sproutpace.domain.model.TrayShape
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.TraySizePresetId
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface SqlDriverFactory : AutoCloseable {
    fun create(): SqlDriver
    override fun close()
}

class LocalDatabase(
    factory: SqlDriverFactory,
    private val ioDispatcher: CoroutineDispatcher,
    private val codec: DomainJsonCodec = DomainJsonCodec(),
) : AutoCloseable {
    private val driver = factory.create()
    private val database = SproutPaceDatabase(driver)
    private val queries = database.sproutPaceQueries

    val watchZones: () -> Flow<List<GrowZone>> = { queries.selectZones(::mapZone).listFlow() }
    val watchTrayPresets: () -> Flow<List<TraySizePreset>> = { queries.selectTrayPresets(::mapTray).listFlow() }
    val watchRecipes: () -> Flow<List<UserGrowRecipe>> = { queries.selectRecipes().decodedListFlow(codec::decodeRecipe) }
    val watchBatches: () -> Flow<List<GrowBatch>> = { queries.selectBatches().decodedListFlow(codec::decodeBatch) }
    val watchPlans: () -> Flow<List<HarvestPlan>> = { queries.selectPlans().decodedListFlow(codec::decodePlan) }
    val watchObservations: () -> Flow<List<Observation>> = {
        queries.selectObservations().decodedListFlow(codec::decodeObservation)
    }

    suspend fun snapshot(): ProductSnapshot = withContext(ioDispatcher) { snapshotNow() }

    suspend fun saveZone(zone: GrowZone): GrowZone = write {
        val order = snapshotNow().zones.indexOfFirst { it.id == zone.id }.takeIf { it >= 0 }
            ?: snapshotNow().zones.size
        queries.upsertZone(zone.id.value, zone.name, zone.type.name, zone.capacity.toLong(), order.toLong())
        zone
    }

    suspend fun saveTrayPreset(preset: TraySizePreset): TraySizePreset = write {
        val (kind, primary, secondary) = when (val shape = preset.shape) {
            is TrayShape.Rectangle -> Triple("RECTANGLE", shape.widthCentimetres, shape.lengthCentimetres)
            is TrayShape.Circle -> Triple("CIRCLE", shape.diameterCentimetres, null)
        }
        val order = snapshotNow().trayPresets.indexOfFirst { it.id == preset.id }.takeIf { it >= 0 }
            ?: snapshotNow().trayPresets.size
        queries.upsertTrayPreset(preset.id.value, preset.name, kind, primary, secondary, order.toLong())
        preset
    }

    suspend fun saveRecipe(recipe: UserGrowRecipe): UserGrowRecipe = write {
        val order = snapshotNow().recipes.indexOfFirst { it.id == recipe.id }.takeIf { it >= 0 }
            ?: snapshotNow().recipes.size
        queries.upsertRecipe(recipe.id.value, recipe.name, recipe.cultureId?.value, codec.encodeRecipe(recipe), order.toLong())
        recipe
    }

    suspend fun saveBatch(batch: GrowBatch): GrowBatch = write {
        val order = snapshotNow().batches.indexOfFirst { it.id == batch.id }.takeIf { it >= 0 }
            ?: snapshotNow().batches.size
        queries.upsertBatch(
            batch.id.value, batch.state.name, batch.recipe.sourceRecipeId?.value,
            batch.recipe.builtInCultureId?.value, codec.encodeBatch(batch), order.toLong(),
        )
        batch
    }

    suspend fun savePlan(plan: HarvestPlan): HarvestPlan = write {
        val order = snapshotNow().plans.indexOfFirst { it.id == plan.id }.takeIf { it >= 0 }
            ?: snapshotNow().plans.size
        queries.upsertPlan(plan.id.value, codec.encodePlan(plan), order.toLong())
        plan
    }

    suspend fun saveObservation(observation: Observation): Observation = write {
        queries.upsertObservation(
            observation.id.value, observation.batchId.value, observation.occurredAt.toString(),
            observation.photoId?.value, codec.encodeObservation(observation),
        )
        observation
    }

    val deleteZone: suspend (GrowZoneId) -> Unit = { id -> write { queries.deleteZone(id.value) } }
    val deleteTrayPreset: suspend (TraySizePresetId) -> Unit = { id -> write { queries.deleteTrayPreset(id.value) } }
    val deleteRecipe: suspend (RecipeId) -> Unit = { id -> write { queries.deleteRecipe(id.value) } }
    val deletePlan: suspend (HarvestPlanId) -> Unit = { id -> write { queries.deletePlan(id.value) } }
    val deleteObservation: suspend (ObservationId) -> Unit = { id -> write { queries.deleteObservation(id.value) } }
    val deleteBatch: suspend (BatchId) -> Unit = { id -> write { queries.deleteBatch(id.value) } }

    suspend fun replace(snapshot: ProductSnapshot) = write { replaceNow(snapshot) }
    suspend fun erase() = write { clearNow() }

    internal fun <T> transaction(block: () -> T): T = database.transactionWithResult { block() }
    internal fun replaceNow(snapshot: ProductSnapshot) {
        clearNow()
        snapshot.zones.forEachIndexed { index, zone ->
            queries.upsertZone(zone.id.value, zone.name, zone.type.name, zone.capacity.toLong(), index.toLong())
        }
        snapshot.trayPresets.forEachIndexed { index, preset ->
            val values = when (val shape = preset.shape) {
                is TrayShape.Rectangle -> Triple("RECTANGLE", shape.widthCentimetres, shape.lengthCentimetres)
                is TrayShape.Circle -> Triple("CIRCLE", shape.diameterCentimetres, null)
            }
            queries.upsertTrayPreset(preset.id.value, preset.name, values.first, values.second, values.third, index.toLong())
        }
        snapshot.recipes.forEachIndexed { index, recipe ->
            queries.upsertRecipe(recipe.id.value, recipe.name, recipe.cultureId?.value, codec.encodeRecipe(recipe), index.toLong())
        }
        snapshot.batches.forEachIndexed { index, batch ->
            queries.upsertBatch(batch.id.value, batch.state.name, batch.recipe.sourceRecipeId?.value,
                batch.recipe.builtInCultureId?.value, codec.encodeBatch(batch), index.toLong())
        }
        snapshot.plans.forEachIndexed { index, plan ->
            queries.upsertPlan(plan.id.value, codec.encodePlan(plan), index.toLong())
        }
        snapshot.observations.forEach { observation ->
            queries.upsertObservation(observation.id.value, observation.batchId.value, observation.occurredAt.toString(),
                observation.photoId?.value, codec.encodeObservation(observation))
        }
    }

    internal fun snapshotNow(): ProductSnapshot {
        val zones = queries.selectZones(::mapZone).executeAsList()
        val trays = queries.selectTrayPresets(::mapTray).executeAsList()
        val recipes = queries.selectRecipes().executeAsList().map(codec::decodeRecipe)
        val batches = queries.selectBatches().executeAsList().map(codec::decodeBatch)
        val plans = queries.selectPlans().executeAsList().map(codec::decodePlan)
        val observations = queries.selectObservations().executeAsList().map(codec::decodeObservation)
        return ProductSnapshot(
            manifest = SnapshotValidation.manifest(zones, trays, recipes, batches, plans, observations, emptyMap()),
            zones = zones, trayPresets = trays, recipes = recipes, batches = batches,
            plans = plans, observations = observations,
        )
    }

    internal fun clearNow() {
        queries.deleteAllObservations()
        queries.deleteAllPlans()
        queries.deleteAllBatches()
        queries.deleteAllRecipes()
        queries.deleteAllTrayPresets()
        queries.deleteAllZones()
    }

    private suspend fun <T> write(block: () -> T): T = withContext(ioDispatcher) { transaction(block) }
    private fun <T : Any> Query<T>.listFlow(): Flow<List<T>> = asFlow().mapToList(ioDispatcher)
    private fun <T : Any> Query<String>.decodedListFlow(decode: (String) -> T): Flow<List<T>> =
        asFlow()
            .mapToList(ioDispatcher)
            .map { payloads -> payloads.map(decode) }

    override fun close() = driver.close()
}

private fun mapZone(id: String, name: String, type: String, capacity: Long, sortOrder: Long): GrowZone {
    require(sortOrder >= 0L) { "Zone sort order must be non-negative" }
    return GrowZone(GrowZoneId(id), name, GrowZoneType.valueOf(type), capacity.toInt())
}

private fun mapTray(
    id: String, name: String, shape: String, primaryCm: Double, secondaryCm: Double?, sortOrder: Long,
): TraySizePreset {
    require(sortOrder >= 0L) { "Tray sort order must be non-negative" }
    return TraySizePreset(
        TraySizePresetId(id),
        name,
        if (shape == "RECTANGLE") TrayShape.Rectangle(primaryCm, requireNotNull(secondaryCm))
        else TrayShape.Circle(primaryCm),
    )
}

class DatabaseSetupManager(private val database: LocalDatabase) : SetupManager {
    override fun watchZones() = database.watchZones()
    override suspend fun saveZone(zone: GrowZone) = domainResult { database.saveZone(zone) }
    override suspend fun saveTrayPreset(preset: TraySizePreset) = domainResult { database.saveTrayPreset(preset) }
}

class DatabaseRecipeManager(private val database: LocalDatabase) : RecipeManager {
    override fun watchRecipes() = database.watchRecipes()
    override suspend fun saveRecipe(recipe: UserGrowRecipe) = domainResult { database.saveRecipe(recipe) }
}

class DatabaseBatchManager(private val database: LocalDatabase) : BatchManager {
    override fun watchBatches() = database.watchBatches()
    override suspend fun saveBatch(batch: GrowBatch) = domainResult { database.saveBatch(batch) }
    override suspend fun applyChange(change: BatchChange) = domainResult { database.saveBatch(change.batch) }
}

class DatabasePlanManager(private val database: LocalDatabase) : PlanManager {
    override fun watchPlans() = database.watchPlans()
    override suspend fun savePlan(plan: HarvestPlan) = domainResult { database.savePlan(plan) }
}
