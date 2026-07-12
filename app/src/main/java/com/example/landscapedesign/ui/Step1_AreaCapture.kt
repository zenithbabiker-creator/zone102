package com.example.landscapedesign.ui

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.landscapedesign.R
import com.example.landscapedesign.ar.ARSessionManager
import com.example.landscapedesign.ar.AnchorManager
import com.example.landscapedesign.ar.ArMode
import com.example.landscapedesign.ar.FrozenFrameProjector
import com.example.landscapedesign.model.ScreenPoint
import com.example.landscapedesign.viewmodel.LandscapeViewModel

/**
 * STEP 1 — Dual-mode area capture.
 *
 * LIVE mode: renders the live ARCore camera feed (via [ArCameraPreview]) and
 * lets the user tap to hit-test the tracked horizontal plane, placing anchors.
 *
 * FROZEN mode: freezes the last camera frame as a still bitmap and lets the
 * user keep tapping — taps are unprojected through the SAVED view/projection
 * matrices and ray-cast onto the cached plane via [FrozenFrameProjector],
 * so measurements stay real-world accurate without a live feed.
 *
 * Both modes feed the SAME [AnchorManager] / Shoelace area pipeline.
 */
@Composable
fun Step1AreaCaptureScreen(
    viewModel: LandscapeViewModel,
    onConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity } ?: return
    val arSessionManager = remember { ARSessionManager(activity) }
    val anchorManager = remember { AnchorManager(arSessionManager) }

    val mode by arSessionManager.mode.collectAsState()
    val planeTracked by arSessionManager.planeTracked.collectAsState()
    val points by anchorManager.points.collectAsState()
    val liveArea by anchorManager.liveAreaM2.collectAsState()

    var viewportSize by remember { mutableStateOf(IntSize(0, 0)) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // ArSceneView (see ArCameraPreview.kt) now owns session creation, install
    // prompts, and resume/pause/close — tied to LocalLifecycleOwner. We only
    // need a one-time pre-flight availability check here so unsupported
    // devices get a clean message instead of a crash.
    val arAvailability = remember { arSessionManager.checkAvailability() }

    Scaffold { padding ->
        if (!arAvailability.supported) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ar_not_supported),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            return@Scaffold
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onSizeChanged { viewportSize = IntSize(it.width, it.height) }
        ) {
            // --- Camera / Frozen photo background layer ---
            when (mode) {
                ArMode.LIVE -> {
                    ArCameraPreview(
                        arSessionManager = arSessionManager,
                        onTap = { x, y, frame ->
                            frame?.let { anchorManager.handleTap(it, x, y) }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ArMode.FROZEN -> {
                    frozenBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(mode) {
                                detectTapGestures { offset ->
                                    val plane = arSessionManager.trackedPlane.value ?: return@detectTapGestures
                                    val viewMatrix = arSessionManager.frozenViewMatrix ?: return@detectTapGestures
                                    val projMatrix = arSessionManager.frozenProjectionMatrix ?: return@detectTapGestures
                                    val world = FrozenFrameProjector.projectTouchToPlane(
                                        screenX = offset.x,
                                        screenY = offset.y,
                                        viewportWidth = viewportSize.width,
                                        viewportHeight = viewportSize.height,
                                        viewMatrix = viewMatrix,
                                        projectionMatrix = projMatrix,
                                        plane = plane
                                    )
                                    world?.let {
                                        anchorManager.addExternalPoint(it, ScreenPoint(offset.x, offset.y))
                                    }
                                }
                            }
                    )
                }
            }

            // --- Polygon / point overlay (shared by both modes) ---
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (points.size >= 2) {
                    for (i in points.indices) {
                        val a = points[i].screen
                        val b = points[(i + 1) % points.size].screen
                        if (i < points.size - 1 || points.size >= 3) {
                            drawLine(
                                color = Color(0xFF2E7D32),
                                start = Offset(a.x, a.y),
                                end = Offset(b.x, b.y),
                                strokeWidth = 6f
                            )
                        }
                    }
                }
                points.forEach { p ->
                    drawCircle(color = Color(0xFFFFC107), radius = 14f, center = Offset(p.screen.x, p.screen.y))
                    drawCircle(color = Color.Black, radius = 14f, center = Offset(p.screen.x, p.screen.y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                }
            }

            // --- Top status card ---
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (mode == ArMode.LIVE) stringResource(R.string.mode_live) else stringResource(R.string.mode_photo),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (mode == ArMode.LIVE && !planeTracked) {
                        Text(stringResource(R.string.scanning_hint), style = MaterialTheme.typography.bodySmall)
                    } else if (mode == ArMode.LIVE) {
                        Text(stringResource(R.string.plane_locked_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = stringResource(R.string.area_label, liveArea),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }

            // --- Bottom controls ---
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = { anchorManager.clearAll() }) {
                    Text(stringResource(R.string.btn_clear_points))
                }

                if (mode == ArMode.LIVE) {
                    Button(
                        enabled = planeTracked,
                        onClick = {
                            arSessionManager.captureFrame(viewportSize.width, viewportSize.height)
                            ArCameraPreviewBridge.captureLastFrame(viewportSize.width, viewportSize.height) { bmp ->
                                frozenBitmap = bmp
                            }
                        }
                    ) {
                        Text(stringResource(R.string.btn_capture))
                    }
                } else {
                    Button(onClick = {
                        arSessionManager.goLive()
                        frozenBitmap = null
                    }) {
                        Text(stringResource(R.string.btn_live_reset))
                    }
                }

                Button(
                    enabled = points.size >= 3,
                    onClick = {
                        viewModel.updateGardenBoundary(points.map { it.world })
                        onConfirmed()
                    }
                ) {
                    Text(stringResource(R.string.btn_confirm_area))
                }
            }
        }
    }
}
