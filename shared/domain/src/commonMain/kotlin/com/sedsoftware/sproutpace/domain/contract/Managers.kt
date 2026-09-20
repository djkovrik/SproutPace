package com.sedsoftware.sproutpace.domain.contract

import com.sedsoftware.sproutpace.domain.model.BatchChange
import com.sedsoftware.sproutpace.domain.model.GrowBatch
import com.sedsoftware.sproutpace.domain.model.GrowZone
import com.sedsoftware.sproutpace.domain.model.HarvestPlan
import com.sedsoftware.sproutpace.domain.model.ProductSnapshot
import com.sedsoftware.sproutpace.domain.model.RestoreValidation
import com.sedsoftware.sproutpace.domain.model.TraySizePreset
import com.sedsoftware.sproutpace.domain.model.UserGrowRecipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

interface SetupManager {
    fun watchZones(): Flow<List<GrowZone>>
    suspend fun saveZone(zone: GrowZone): Result<GrowZone>
    suspend fun saveTrayPreset(preset: TraySizePreset): Result<TraySizePreset>
}

interface RecipeManager {
    fun watchRecipes(): Flow<List<UserGrowRecipe>>
    suspend fun saveRecipe(recipe: UserGrowRecipe): Result<UserGrowRecipe>
}

interface BatchManager {
    fun watchBatches(): Flow<List<GrowBatch>>
    suspend fun saveBatch(batch: GrowBatch): Result<GrowBatch>
    suspend fun applyChange(change: BatchChange): Result<GrowBatch>
}

interface PlanManager {
    fun watchPlans(): Flow<List<HarvestPlan>>
    suspend fun savePlan(plan: HarvestPlan): Result<HarvestPlan>
}

interface BackupManager {
    suspend fun createSnapshot(): Result<ProductSnapshot>
    suspend fun validate(snapshot: ProductSnapshot): Result<RestoreValidation>
    suspend fun replace(snapshot: ProductSnapshot): Result<Unit>
}

suspend inline fun <T> domainResult(crossinline operation: suspend () -> T): Result<T> =
    runCatching { operation() }.onFailure { failure ->
        if (failure is CancellationException) throw failure
    }
