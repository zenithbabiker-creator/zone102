package com.example.landscapedesign.ui

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.example.landscapedesign.ar.ARSessionManager
import com.google.ar.core.Config
import com.google.ar.core.Frame
import io.github.sceneview.ar.ArSceneView

/**
 * Hosts the live ARCore camera feed inside Compose using SceneView's
 * [ArSceneView] — this fully replaces the previous manual `GLSurfaceView` +
 * hand-written `GLES20`/shader renderer. SceneView owns:
 *  - ARCore session creation, install prompts, and lifecycle (resume/pause/close),
 *    tied automatically to the current [androidx.lifecycle.LifecycleOwner].
 *  - The camera passthrough background (rendered via Filament internally —
 *    zero manual OpenGL/shader code required here).
 *  - The horizontal-plane visualization mesh (`planeRenderer`), so the user
 *    sees the standard "scanning dots/mesh" feedback out of the box.
 *
 * This composable's only remaining responsibilities are:
 *  1. Configuring the session for HORIZONTAL plane finding.
 *  2. Forwarding session/frame updates to [ARSessionManager] so the rest of
 *     the app (Live hit-testing, Frozen-frame projection) keeps working
 *     against a single consistent source of spatial truth.
 *  3. Forwarding raw touch taps (with the [Frame] active at tap time) up to
 *     [onTap] so `AnchorManager` can hit-test against the tracked plane —
 *     same contract as before, so Step 1 / Step 3 required no changes.
 *  4. Registering itself with [ArCameraPreviewBridge] so "Capture/Freeze"
 *     can request a still bitmap snapshot of the current view.
 */
@Composable
fun ArCameraPreview(
    arSessionManager: ARSessionManager,
    onTap: (x: Float, y: Float, frame: Frame?) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ArSceneView(ctx).apply {
                // Camera passthrough + plane mesh are rendered automatically;
                // just make sure the built-in plane visualization is visible
                // so the user gets the "scanning..." feedback for free.
                planeRenderer.isVisible = true

                // Configure horizontal-only plane finding, matching the spec.
                onSessionConfiguration = { session, config ->
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    config.focusMode = Config.FocusMode.AUTO
                }

                // SceneView hands us the freshly created Session once ARCore
                // install/creation succeeds — hand it to ARSessionManager so
                // AnchorManager can keep creating anchors against it.
                onSessionCreated = { session ->
                    arSessionManager.bindSession(session)
                }

                // Fired every render tick with the latest Frame — this is our
                // replacement for the old manual `onDrawFrame` GLES callback.
                onSessionUpdated = { session, frame ->
                    latestFrame = frame
                    arSessionManager.onFrameUpdated(session, frame)
                }

                onArSessionFailed = { exception ->
                    android.util.Log.e("ArCameraPreview", "AR session failed to start", exception)
                }

                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        onTap(event.x, event.y, latestFrame)
                    }
                    true
                }

                ArCameraPreviewBridge.register(this)

                // ArSceneView is lifecycle-aware: registering it here makes it
                // resume/pause/close itself automatically in step with the
                // hosting Activity/Composable, exactly like the old manual
                // ARSessionManager.resume()/pause() used to do by hand.
                lifecycleOwner.lifecycle.addObserver(this)
            }
        },
        onRelease = { view ->
            ArCameraPreviewBridge.unregister()
            arSessionManager.clearSession()
            lifecycleOwner.lifecycle.removeObserver(view)
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            ArCameraPreviewBridge.unregister()
        }
    }
}

/** Latest [Frame] seen by this view's `onSessionUpdated` callback, exposed for touch hit-testing. */
private var ArSceneView.latestFrame: Frame?
    get() = tag as? Frame
    set(value) { tag = value }

/**
 * Bridges the Compose layer (Step1AreaCaptureScreen) with the live
 * [ArSceneView] so "Capture/Freeze" can request a still bitmap snapshot of
 * whatever is currently on screen, via [android.view.PixelCopy]. Works
 * unchanged against ArSceneView since PixelCopy reads back the Window/View's
 * on-screen pixels regardless of which renderer (GLSurfaceView, SurfaceView,
 * or SceneView's internal Filament surface) drew them.
 */
object ArCameraPreviewBridge {
    private var view: ArSceneView? = null

    fun register(view: ArSceneView) {
        this.view = view
    }

    fun unregister() {
        view = null
    }

    /**
     * Captures the current AR view contents as a [android.graphics.Bitmap]
     * using [PixelCopyHelper], asynchronously. Result (or null on failure) is
     * delivered via [onResult] on the main thread once the copy completes.
     */
    fun captureLastFrame(width: Int, height: Int, onResult: (android.graphics.Bitmap?) -> Unit) {
        val v = view
        if (v == null || width <= 0 || height <= 0) {
            onResult(null)
            return
        }
        PixelCopyHelper.copyFromView(v, width, height, onResult)
    }
}
