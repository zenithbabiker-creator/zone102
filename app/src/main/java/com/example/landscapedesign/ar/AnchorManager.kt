package com.example.landscapedesign.ar

import com.example.landscapedesign.geometry.GeometryUtils
import com.example.landscapedesign.model.AnchorPoint
import com.example.landscapedesign.model.Point3D
import com.example.landscapedesign.model.ScreenPoint
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Handles MODE 1: Live AR Interaction.
 * Performs ARCore hit-tests against the tracked horizontal plane on tap,
 * places persistent [Anchor]s, and exposes the running polygon + live area.
 */
class AnchorManager(private val arSessionManager: ARSessionManager) {

    private val anchors = mutableListOf<Anchor>()

    private val _points = MutableStateFlow<List<AnchorPoint>>(emptyList())
    val points: StateFlow<List<AnchorPoint>> = _points

    private val _liveAreaM2 = MutableStateFlow(0f)
    val liveAreaM2: StateFlow<Float> = _liveAreaM2

    /**
     * Performs a HitTest at the given screen tap coordinate against the current
     * live [Frame]. If it lands on the tracked horizontal plane's polygon, a new
     * anchor is placed and the running polygon/area is recalculated.
     */
    fun handleTap(frame: Frame, screenX: Float, screenY: Float): Boolean {
        val hitResults = frame.hitTest(screenX, screenY)
        val planeHit = hitResults.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Plane &&
                trackable.isPoseInPolygon(hit.hitPose) &&
                trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING
        } ?: return false

        return addAnchorFromHit(planeHit, screenX, screenY)
    }

    private fun addAnchorFromHit(hit: HitResult, screenX: Float, screenY: Float): Boolean {
        val anchor = arSessionManager.createAnchor(hit.hitPose) ?: return false
        anchors.add(anchor)

        val pose = hit.hitPose
        val world = Point3D(pose.tx(), pose.ty(), pose.tz())
        val newPoint = AnchorPoint(world = world, screen = ScreenPoint(screenX, screenY))
        _points.value = _points.value + newPoint
        recalculateArea()
        return true
    }

    /** Adds a point that was NOT derived from a live ARCore hit-test (e.g. frozen-photo ray-plane projection). */
    fun addExternalPoint(world: Point3D, screen: ScreenPoint) {
        _points.value = _points.value + AnchorPoint(world = world, screen = screen)
        recalculateArea()
    }

    fun removeLastPoint() {
        val current = _points.value
        if (current.isEmpty()) return
        _points.value = current.dropLast(1)
        if (anchors.isNotEmpty()) {
            anchors.removeLast().detach()
        }
        recalculateArea()
    }

    fun clearAll() {
        anchors.forEach { it.detach() }
        anchors.clear()
        _points.value = emptyList()
        _liveAreaM2.value = 0f
    }

    /** Filters out anchors that ARCore has stopped tracking (e.g. lost mid-air). */
    fun pruneUntracked() {
        val stillTracking = anchors.filter { it.trackingState == TrackingState.TRACKING }
        if (stillTracking.size != anchors.size) {
            anchors.clear()
            anchors.addAll(stillTracking)
        }
    }

    private fun recalculateArea() {
        val worldPoints = _points.value.map { it.world }
        _liveAreaM2.value = GeometryUtils.round2(GeometryUtils.shoelaceArea(worldPoints))
    }

    /** Current polygon boundary, used for edge-distance measurements in Step 3. */
    fun currentBoundary(): List<Point3D> = _points.value.map { it.world }
}
