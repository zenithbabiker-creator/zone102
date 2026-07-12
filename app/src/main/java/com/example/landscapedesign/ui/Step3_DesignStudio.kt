package com.example.landscapedesign.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.landscapedesign.R
import com.example.landscapedesign.geometry.GeometryUtils
import com.example.landscapedesign.model.BorderTier
import com.example.landscapedesign.model.PlantNode
import com.example.landscapedesign.model.PlantType
import com.example.landscapedesign.model.Point3D
import com.example.landscapedesign.model.ScreenPoint
import com.example.landscapedesign.model.ShapeElement
import com.example.landscapedesign.model.ShapeType
import com.example.landscapedesign.ui.components.BorderConfigRow
import com.example.landscapedesign.ui.components.RadiusInputDialog
import com.example.landscapedesign.ui.components.TreesPerMeterDialog
import com.example.landscapedesign.viewmodel.LandscapeViewModel

/** Active interaction tool in the design canvas. */
private enum class StudioTool { NONE, ARC, CIRCLE, POLYGON, PLANT, ERASER }

/**
 * Real-world-to-screen scale used to convert the garden's meter coordinates
 * (X,Z from Step 1) into canvas pixel offsets and back. A production build
 * would derive this from the frozen frame's saved View/Projection matrices
 * (see [com.example.landscapedesign.ar.FrozenFrameProjector]); here we expose
 * a simple, explicit meters-per-pixel scale anchored to the canvas center so
 * every drawing/measurement operation stays consistent and testable.
 */
private class ScreenMapper(val canvasSize: IntSize, val pixelsPerMeter: Float = 40f) {
    private val originX get() = canvasSize.width / 2f
    private val originY get() = canvasSize.height / 2f

    fun worldToScreen(p: Point3D): Offset =
        Offset(originX + p.x * pixelsPerMeter, originY - p.z * pixelsPerMeter)

    fun screenToWorld(offset: Offset): Point3D {
        val x = (offset.x - originX) / pixelsPerMeter
        val z = -(offset.y - originY) / pixelsPerMeter
        return Point3D(x, 0f, z)
    }
}

/**
 * STEP 3 — Interactive Design Studio, built on top of the captured/frozen
 * photo and real-world meter scale established in Steps 1-2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step3DesignStudioScreen(
    viewModel: LandscapeViewModel,
    onNext: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    var canvasSize by remember { mutableStateOf(IntSize(0, 0)) }
    val mapper = remember(canvasSize) { ScreenMapper(canvasSize) }

    var activeTool by remember { mutableStateOf(StudioTool.NONE) }
    var activePlant by remember { mutableStateOf<PlantType?>(null) }

    // Pending shape placement awaiting a radius input.
    var pendingShapeCenter by remember { mutableStateOf<Point3D?>(null) }
    var pendingShapeType by remember { mutableStateOf<ShapeType?>(null) }
    var showRadiusDialog by remember { mutableStateOf(false) }

    // Hedge-fill flow: shape id awaiting a trees-per-meter value.
    var pendingHedgeShapeId by remember { mutableStateOf<String?>(null) }
    var showTreesPerMeterDialog by remember { mutableStateOf(false) }

    // Live measurement lines from the last tap to the two nearest boundary edges.
    var lastTouchWorld by remember { mutableStateOf<Point3D?>(null) }

    // Eraser drag tracking (start/end world points along a border/shape line).
    var eraserDragStart by remember { mutableStateOf<Offset?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.step3_title)) },
                    actions = {
                        IconButton(onClick = { viewModel.undo() }) {
                            Icon(Icons.Filled.Undo, contentDescription = stringResource(R.string.undo))
                        }
                        IconButton(onClick = { viewModel.redo() }) {
                            Icon(Icons.Filled.Redo, contentDescription = stringResource(R.string.redo))
                        }
                    }
                )
                ShapeToolbar(
                    activeTool = activeTool,
                    onToolSelected = { tool ->
                        activeTool = if (activeTool == tool) StudioTool.NONE else tool
                        if (activeTool != StudioTool.PLANT) activePlant = null
                    }
                )
            }
        },
        floatingActionButton = {
            PlantDropperMenu(
                activePlant = activePlant,
                onPlantSelected = { plant ->
                    activePlant = plant
                    activeTool = StudioTool.PLANT
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // --- Design canvas over the frozen photo ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFEFEBE0)) // stands in for the frozen photo background
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(activeTool, activePlant) {
                        if (activeTool == StudioTool.ERASER) {
                            detectDragGestures(
                                onDragStart = { eraserDragStart = it },
                                onDragEnd = {
                                    // Gap length was already applied incrementally during onDrag below;
                                    // just clear the drag anchor here.
                                    eraserDragStart = null
                                }
                            ) { change, dragAmount ->
                                // Incremental gap length for this drag step (world meters),
                                // accumulated onto whichever border tier the drag started over.
                                val stepStartScreen = change.position - dragAmount
                                val w1 = mapper.screenToWorld(stepStartScreen)
                                val w2 = mapper.screenToWorld(change.position)
                                val gap = GeometryUtils.distanceXZ(w1, w2)
                                val nearestTier = state.borders
                                    .filter { it.rawLengthMeters() > 0f }
                                    .maxByOrNull { it.rawLengthMeters() }
                                    ?.tier
                                if (nearestTier != null && gap > 0.01f) {
                                    viewModel.eraseBorderSegment(nearestTier, gap)
                                }
                            }
                        } else {
                            detectTapGestures { offset ->
                                val world = mapper.screenToWorld(offset)
                                lastTouchWorld = world

                                when (activeTool) {
                                    StudioTool.CIRCLE, StudioTool.ARC -> {
                                        pendingShapeCenter = world
                                        pendingShapeType = if (activeTool == StudioTool.CIRCLE) ShapeType.CIRCLE else ShapeType.ARC
                                        showRadiusDialog = true
                                    }
                                    StudioTool.PLANT -> {
                                        activePlant?.let { plant ->
                                            viewModel.addPlant(
                                                PlantNode(
                                                    type = plant,
                                                    world = world,
                                                    screen = ScreenPoint(offset.x, offset.y)
                                                )
                                            )
                                        }
                                    }
                                    StudioTool.POLYGON, StudioTool.NONE, StudioTool.ERASER -> {
                                        // Tapping with no tool active just updates the measurement lines.
                                    }
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // --- Garden boundary ---
                    val boundaryScreen = state.gardenBoundary.map { mapper.worldToScreen(it) }
                    if (boundaryScreen.size >= 2) {
                        for (i in boundaryScreen.indices) {
                            val a = boundaryScreen[i]
                            val b = boundaryScreen[(i + 1) % boundaryScreen.size]
                            drawLine(Color(0xFF2E7D32), a, b, strokeWidth = 5f)
                        }
                    }

                    // --- Shapes (circle/arc/polygon hedge rings) ---
                    state.shapes.forEach { shape ->
                        drawShape(shape, mapper)
                    }

                    // --- Borders (3-tier ribbons, thickness scaled to meters) ---
                    state.borders.forEach { border ->
                        if (border.pathVertices.size >= 2) {
                            val widthPx = border.thicknessMeters() * mapper.pixelsPerMeter
                            val color = when (border.tier) {
                                BorderTier.LARGE -> Color(0xFF6D4C41)
                                BorderTier.MEDIUM -> Color(0xFF8D6E63)
                                BorderTier.SMALL -> Color(0xFFA1887F)
                            }
                            for (i in 0 until border.pathVertices.size - 1) {
                                val a = mapper.worldToScreen(border.pathVertices[i])
                                val b = mapper.worldToScreen(border.pathVertices[i + 1])
                                drawLine(color, a, b, strokeWidth = widthPx.coerceAtLeast(4f))
                            }
                        }
                    }

                    // --- Plant nodes ---
                    state.plants.forEach { plant ->
                        val p = mapper.worldToScreen(plant.world)
                        val color = when (plant.type) {
                            PlantType.ROYAL_PALM -> Color(0xFF33691E)
                            PlantType.NO_THORN -> Color(0xFF558B2F)
                            PlantType.DURANTA -> Color(0xFF7CB342)
                            PlantType.CUSTOM -> Color(0xFF9E9D24)
                        }
                        drawCircle(color, radius = 16f, center = p)
                        drawCircle(Color.Black, radius = 16f, center = p, style = Stroke(width = 2f))
                    }

                    // --- Dynamic measurement lines: last tap to nearest 2 boundary edges ---
                    lastTouchWorld?.let { touch ->
                        val edges = GeometryUtils.nearestTwoEdgeDistances(touch, state.gardenBoundary)
                        val touchScreen = mapper.worldToScreen(touch)
                        edges.forEach { edge ->
                            val closestScreen = mapper.worldToScreen(edge.closestPoint)
                            drawLine(
                                color = Color(0xFFD32F2F),
                                start = touchScreen,
                                end = closestScreen,
                                strokeWidth = 3f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                            )
                        }
                    }
                }

                // Distance text tags (drawn as Compose Text overlays, positioned at each
                // measurement line's midpoint, for crisp Arabic glyph rendering).
                val density = androidx.compose.ui.platform.LocalDensity.current
                lastTouchWorld?.let { touch ->
                    val edges = GeometryUtils.nearestTwoEdgeDistances(touch, state.gardenBoundary)
                    edges.forEach { edge ->
                        val mid = Point3D(
                            (touch.x + edge.closestPoint.x) / 2f,
                            0f,
                            (touch.z + edge.closestPoint.z) / 2f
                        )
                        val screenMid = mapper.worldToScreen(mid)
                        val offsetXDp = with(density) { screenMid.x.toDp() }
                        val offsetYDp = with(density) { screenMid.y.toDp() }
                        Text(
                            text = "%.1f م".format(edge.distanceMeters),
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.offset(x = offsetXDp, y = offsetYDp)
                        )
                    }
                }
            }

            // --- Summary + borders configuration + save button ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.borders_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        BorderConfigRow(
                            titleRes = R.string.border_large,
                            suggestionRes = R.string.border_plant_suggestion_large,
                            border = viewModel.borderFor(BorderTier.LARGE),
                            onPlantNameChanged = { viewModel.updateBorderPlantName(BorderTier.LARGE, it) },
                            onDensityChanged = { viewModel.updateBorderDensity(BorderTier.LARGE, it) }
                        )
                        BorderConfigRow(
                            titleRes = R.string.border_medium,
                            suggestionRes = R.string.border_plant_suggestion_medium,
                            border = viewModel.borderFor(BorderTier.MEDIUM),
                            onPlantNameChanged = { viewModel.updateBorderPlantName(BorderTier.MEDIUM, it) },
                            onDensityChanged = { viewModel.updateBorderDensity(BorderTier.MEDIUM, it) }
                        )
                        BorderConfigRow(
                            titleRes = R.string.border_small,
                            suggestionRes = R.string.border_plant_suggestion_small,
                            border = viewModel.borderFor(BorderTier.SMALL),
                            onPlantNameChanged = { viewModel.updateBorderPlantName(BorderTier.SMALL, it) },
                            onDensityChanged = { viewModel.updateBorderDensity(BorderTier.SMALL, it) }
                        )
                    }
                }

                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(
                                R.string.summary_plants_added,
                                state.royalPalmCount,
                                state.noThornCount
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        state.borders.forEach { border ->
                            val tierLabel = when (border.tier) {
                                BorderTier.LARGE -> stringResource(R.string.border_large)
                                BorderTier.MEDIUM -> stringResource(R.string.border_medium)
                                BorderTier.SMALL -> stringResource(R.string.border_small)
                            }
                            val line = if (border.rawLengthMeters() > 0f) {
                                if (border.isStructural) {
                                    stringResource(
                                        R.string.border_summary_structural,
                                        tierLabel,
                                        border.netLengthMeters()
                                    )
                                } else {
                                    stringResource(
                                        R.string.border_summary_planted,
                                        tierLabel,
                                        border.plantName ?: "",
                                        border.requiredPlantCount()
                                    )
                                }
                            } else null
                            line?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }

                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_save_generate))
                }
            }
        }
    }

    // --- Radius input dialog for newly placed Circle/Arc shapes ---
    if (showRadiusDialog && pendingShapeCenter != null && pendingShapeType != null) {
        RadiusInputDialog(
            onConfirm = { radius ->
                val shape = ShapeElement(
                    type = pendingShapeType!!,
                    centerWorld = pendingShapeCenter,
                    radiusMeters = radius,
                    startAngleDeg = 0f,
                    sweepAngleDeg = if (pendingShapeType == ShapeType.ARC) 180f else 360f
                )
                viewModel.addShape(shape)
                pendingHedgeShapeId = shape.id
                showRadiusDialog = false
                showTreesPerMeterDialog = true
            },
            onDismiss = { showRadiusDialog = false }
        )
    }

    // --- Trees-per-meter dialog to auto-fill the new shape's perimeter with Duranta hedge ---
    if (showTreesPerMeterDialog && pendingHedgeShapeId != null) {
        TreesPerMeterDialog(
            plantName = stringResource(R.string.plant_duranta),
            onConfirm = { density ->
                viewModel.applyHedgeToShape(
                    pendingHedgeShapeId!!,
                    plantName = "دورنتا",
                    treesPerMeter = density
                )
                showTreesPerMeterDialog = false
                pendingHedgeShapeId = null
            },
            onDismiss = {
                showTreesPerMeterDialog = false
                pendingHedgeShapeId = null
            }
        )
    }
}

@Composable
private fun ShapeToolbar(
    activeTool: StudioTool,
    onToolSelected: (StudioTool) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            listOf(
                Triple(StudioTool.ARC, R.string.tool_arc, null),
                Triple(StudioTool.CIRCLE, R.string.tool_circle, null),
                Triple(StudioTool.POLYGON, R.string.tool_polygon, null),
                Triple(StudioTool.ERASER, R.string.tool_eraser, null)
            )
        ) { (tool, labelRes, _) ->
            FilterChip(
                selected = activeTool == tool,
                onClick = { onToolSelected(tool) },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}

@Composable
private fun PlantDropperMenu(
    activePlant: PlantType?,
    onPlantSelected: (PlantType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.plant_menu_title))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plant_no_thorn)) },
                onClick = { onPlantSelected(PlantType.NO_THORN); expanded = false }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plant_royal_palm)) },
                onClick = { onPlantSelected(PlantType.ROYAL_PALM); expanded = false }
            )
        }
    }
}

/** Draws a Circle/Arc/Polygon [ShapeElement] onto the Canvas using the shared [ScreenMapper]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShape(
    shape: ShapeElement,
    mapper: ScreenMapper
) {
    when (shape.type) {
        ShapeType.CIRCLE -> {
            val center = shape.centerWorld ?: return
            val radius = shape.radiusMeters ?: return
            drawCircle(
                color = Color(0xFF558B2F),
                radius = radius * mapper.pixelsPerMeter,
                center = mapper.worldToScreen(center),
                style = Stroke(width = 4f)
            )
        }
        ShapeType.ARC -> {
            val center = shape.centerWorld ?: return
            val radius = shape.radiusMeters ?: return
            val screenCenter = mapper.worldToScreen(center)
            val r = radius * mapper.pixelsPerMeter
            drawArc(
                color = Color(0xFF558B2F),
                startAngle = shape.startAngleDeg ?: 0f,
                sweepAngle = shape.sweepAngleDeg ?: 180f,
                useCenter = false,
                topLeft = Offset(screenCenter.x - r, screenCenter.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(width = 4f)
            )
        }
        ShapeType.POLYGON -> {
            val pts = shape.polygonVertices.map { mapper.worldToScreen(it) }
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                drawLine(Color(0xFF558B2F), a, b, strokeWidth = 4f)
            }
        }
    }
}
