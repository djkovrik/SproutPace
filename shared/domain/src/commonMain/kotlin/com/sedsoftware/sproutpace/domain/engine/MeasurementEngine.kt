package com.sedsoftware.sproutpace.domain.engine

import com.sedsoftware.sproutpace.domain.model.PositiveGrams
import com.sedsoftware.sproutpace.domain.model.SeedDensity
import com.sedsoftware.sproutpace.domain.model.TrayShape

object MeasurementEngine {
    fun trayAreaSquareCentimetres(shape: TrayShape): Double = shape.areaSquareCentimetres

    fun recommendedSeedMass(shape: TrayShape, density: SeedDensity): PositiveGrams =
        PositiveGrams(shape.areaSquareCentimetres * density.gramsPerSquareCentimetre)
}
