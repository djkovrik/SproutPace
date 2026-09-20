package com.sedsoftware.sproutpace.domain.model

import kotlin.math.PI

enum class GrowZoneType { NATURAL_LIGHT, GROW_LIGHT, DARK }

data class GrowZone(
    val id: GrowZoneId,
    val name: String,
    val type: GrowZoneType,
    val capacity: Int,
) {
    init {
        require(name.isNotBlank())
        require(capacity >= 0)
    }

    val isLightCapable: Boolean get() = type != GrowZoneType.DARK
}

sealed interface TrayShape {
    val areaSquareCentimetres: Double

    data class Rectangle(val widthCentimetres: Double, val lengthCentimetres: Double) : TrayShape {
        init {
            require(widthCentimetres.isFinite() && widthCentimetres > 0.0)
            require(lengthCentimetres.isFinite() && lengthCentimetres > 0.0)
        }
        override val areaSquareCentimetres: Double = widthCentimetres * lengthCentimetres
    }

    data class Circle(val diameterCentimetres: Double) : TrayShape {
        init { require(diameterCentimetres.isFinite() && diameterCentimetres > 0.0) }
        override val areaSquareCentimetres: Double = PI * diameterCentimetres * diameterCentimetres / 4.0
    }
}

data class TraySizePreset(
    val id: TraySizePresetId,
    val name: String,
    val shape: TrayShape,
) {
    init { require(name.isNotBlank()) }
}

sealed interface Placement {
    data object None : Placement
    data class Assigned(val zoneId: GrowZoneId, val zoneNameSnapshot: String) : Placement {
        init { require(zoneNameSnapshot.isNotBlank()) }
    }
    data class Unassigned(val priorZoneNameSnapshot: String?) : Placement
}
