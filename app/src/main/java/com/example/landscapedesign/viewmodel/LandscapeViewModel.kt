package com.example.landscapedesign.viewmodel

import androidx.lifecycle.ViewModel
import com.example.landscapedesign.geometry.GeometryUtils
import com.example.landscapedesign.geometry.PolygonClipper
import com.example.landscapedesign.model.BorderElement
import com.example.landscapedesign.model.BorderTier
import com.example.landscapedesign.model.DesignLayoutState
import com.example.landscapedesign.model.PlantNode
import com.example.landscapedesign.model.Point3D
import com.example.landscapedesign.model.ShapeElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds [DesignLayoutState] across the entire flow (Steps 1-5) and exposes
 * mutation functions used by each screen. All derived values (soil volume,
 * net lawn area, plant counts) are recalculated reactively whenever their
 * inputs change, so every screen and the final report always stay in sync.
 */
class LandscapeViewModel : ViewModel() {

    private val _state = MutableStateFlow(DesignLayoutState())
    val state: StateFlow<DesignLayoutState> = _state.asStateFlow()

    // Undo/Redo history for the Step 3 design studio (shapes, plants, borders).
    private val undoStack = ArrayDeque<DesignLayoutState>()
    private val redoStack = ArrayDeque<DesignLayoutState>()
    private val historyLimit = 50

    // ---------------------------------------------------------------------
    // STEP 1: Garden boundary & area (shared by Live + Frozen AR modes)
    // ---------------------------------------------------------------------

    fun updateGardenBoundary(points: List<Point3D>) {
        val area = GeometryUtils.round2(GeometryUtils.shoelaceArea(points))
        _state.value = _state.value.copy(gardenBoundary = points, gardenAreaM2 = area)
        recalcSoilVolume()
        recalcLawn()
    }

    // ---------------------------------------------------------------------
    // STEP 2: Soil volume
    // ---------------------------------------------------------------------

    fun updateSoilThickness(thicknessCm: Int) {
        _state.value = _state.value.copy(soilThicknessCm = thicknessCm)
        recalcSoilVolume()
    }

    private fun recalcSoilVolume() {
        val s = _state.value
        val thicknessMeters = s.soilThicknessCm / 100.0
        val volume = GeometryUtils.round2(s.gardenAreaM2 * thicknessMeters).toFloat()
        _state.value = _state.value.copy(soilVolumeM3 = volume)
    }

    // ---------------------------------------------------------------------
    // STEP 3: Design studio — shapes, plants, borders (with undo/redo)
    // ---------------------------------------------------------------------

    private fun pushHistory() {
        undoStack.addLast(_state.value)
        if (undoStack.size > historyLimit) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_state.value)
        _state.value = undoStack.removeLast()
        recalcLawn()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_state.value)
        _state.value = redoStack.removeLast()
        recalcLawn()
    }

    fun addPlant(plant: PlantNode) {
        pushHistory()
        _state.value = _state.value.copy(plants = _state.value.plants + plant)
    }

    fun movePlant(id: String, newWorld: Point3D, newScreen: com.example.landscapedesign.model.ScreenPoint) {
        val updated = _state.value.plants.map {
            if (it.id == id) it.copy(world = newWorld, screen = newScreen) else it
        }
        _state.value = _state.value.copy(plants = updated)
    }

    fun deletePlant(id: String) {
        pushHistory()
        _state.value = _state.value.copy(plants = _state.value.plants.filterNot { it.id == id })
    }

    fun addShape(shape: ShapeElement) {
        pushHistory()
        _state.value = _state.value.copy(shapes = _state.value.shapes + shape)
        recalcLawn()
    }

    fun updateShape(shapeId: String, transform: (ShapeElement) -> ShapeElement) {
        val updated = _state.value.shapes.map { if (it.id == shapeId) transform(it) else it }
        _state.value = _state.value.copy(shapes = updated)
        recalcLawn()
    }

    /**
     * Fills a shape's perimeter with a border/hedge plant at the given density
     * (trees per linear meter), computing the required plant count from the
     * shape's real-world perimeter (circle / arc / polygon formulas).
     */
    fun applyHedgeToShape(shapeId: String, plantName: String, treesPerMeter: Float) {
        updateShape(shapeId) { shape ->
            val perimeter = shape.perimeterMeters()
            val count = GeometryUtils.ceilToInt(perimeter * treesPerMeter)
            shape.copy(
                hedgePlantName = plantName,
                hedgeDensityPerMeter = treesPerMeter,
                hedgePlantCount = count
            )
        }
    }

    /** Records an eraser cut on a shape's perimeter (e.g. opening in a hedge ring). */
    fun eraseShapeSegment(shapeId: String, gapLengthMeters: Float) {
        pushHistory()
        updateShape(shapeId) { shape ->
            val newGap = shape.eraserGapsMeters + gapLengthMeters
            val updated = shape.copy(eraserGapsMeters = newGap)
            // Recompute hedge plant count against the new net perimeter.
            if (updated.hedgePlantName != null && updated.hedgeDensityPerMeter != null) {
                val count = GeometryUtils.ceilToInt(updated.perimeterMeters() * updated.hedgeDensityPerMeter)
                updated.copy(hedgePlantCount = count)
            } else updated
        }
    }

    // --- Borders (3-tier) ---

    fun updateBorderPath(tier: BorderTier, path: List<Point3D>) {
        pushHistory()
        val updated = _state.value.borders.map {
            if (it.tier == tier) it.copy(pathVertices = path) else it
        }
        _state.value = _state.value.copy(borders = updated)
        recalcLawn()
    }

    fun updateBorderPlantName(tier: BorderTier, plantName: String?) {
        val updated = _state.value.borders.map {
            if (it.tier == tier) it.copy(plantName = plantName?.takeIf { n -> n.isNotBlank() }) else it
        }
        _state.value = _state.value.copy(borders = updated)
    }

    fun updateBorderDensity(tier: BorderTier, densityPerMeter: Float?) {
        val updated = _state.value.borders.map {
            if (it.tier == tier) it.copy(densityPerMeter = densityPerMeter) else it
        }
        _state.value = _state.value.copy(borders = updated)
    }

    /** Applies an eraser cut (door/opening) to a border's path, reducing its net length. */
    fun eraseBorderSegment(tier: BorderTier, gapLengthMeters: Float) {
        pushHistory()
        val updated = _state.value.borders.map {
            if (it.tier == tier) {
                it.copy(
                    eraserGapsMeters = it.eraserGapsMeters + gapLengthMeters,
                    openingsCount = it.openingsCount + 1
                )
            } else it
        }
        _state.value = _state.value.copy(borders = updated)
        recalcLawn()
    }

    fun borderFor(tier: BorderTier): BorderElement =
        _state.value.borders.first { it.tier == tier }

    // ---------------------------------------------------------------------
    // STEP 4: Lawn density & net area
    // ---------------------------------------------------------------------

    fun updateLawnDensity(densityPerM2: Int) {
        _state.value = _state.value.copy(lawnDensityPerM2 = densityPerM2)
        recalcLawn()
    }

    private fun recalcLawn() {
        val s = _state.value
        val net = PolygonClipper.netLawnArea(
            baseAreaMeters2 = s.gardenAreaM2,
            borders = s.borders,
            treeZoneShapes = s.shapes
        )
        val netRounded = GeometryUtils.round2(net)
        val totalPlants = GeometryUtils.ceilToInt(netRounded * s.lawnDensityPerM2)
        _state.value = _state.value.copy(netLawnAreaM2 = netRounded, totalLawnPlants = totalPlants)
    }
}
