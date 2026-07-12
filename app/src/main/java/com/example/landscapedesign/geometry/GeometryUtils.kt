package com.example.landscapedesign.geometry

import com.example.landscapedesign.model.Point3D
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Unified geometry math used by BOTH Live AR mode and Frozen Photo mode.
 * All functions operate on real-world meter coordinates (Point3D), ignoring
 * the Y (vertical) axis since every point is assumed to lie on the same
 * horizontal ground plane.
 */
object GeometryUtils {

    /**
     * Shoelace (Gauss) formula applied to the (X, Z) plane.
     * Works identically whether points came from a live HitTest or a
     * frozen-frame ray-plane projection — both produce Point3D in world space.
     */
    fun shoelaceArea(points: List<Point3D>): Float {
        if (points.size < 3) return 0f
        var sum = 0.0
        val n = points.size
        for (i in 0 until n) {
            val a = points[i]
            val b = points[(i + 1) % n]
            sum += (a.x.toDouble() * b.z.toDouble()) - (b.x.toDouble() * a.z.toDouble())
        }
        return abs(sum / 2.0).toFloat()
    }

    /** Rounds a value to 2 decimal places for professional display. */
    fun round2(value: Float): Float {
        return (Math.round(value * 100.0) / 100.0).toFloat()
    }

    fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    /** Euclidean distance between two world points on the (X,Z) plane, in meters. */
    fun distanceXZ(a: Point3D, b: Point3D): Float {
        val dx = b.x - a.x
        val dz = b.z - a.z
        return sqrt(dx * dx + dz * dz)
    }

    /**
     * Given a touch point and a set of boundary edges (each edge = pair of Point3D
     * forming the outer polygon), returns the distance in meters from the point to
     * the two nearest edges. Used for the dynamic measurement-line feature in Step 3.
     */
    fun nearestTwoEdgeDistances(
        point: Point3D,
        boundaryPolygon: List<Point3D>
    ): List<EdgeDistance> {
        if (boundaryPolygon.size < 2) return emptyList()
        val distances = mutableListOf<EdgeDistance>()
        val n = boundaryPolygon.size
        for (i in 0 until n) {
            val a = boundaryPolygon[i]
            val b = boundaryPolygon[(i + 1) % n]
            val (dist, closest) = distancePointToSegment(point, a, b)
            distances.add(EdgeDistance(edgeStart = a, edgeEnd = b, distanceMeters = dist, closestPoint = closest))
        }
        return distances.sortedBy { it.distanceMeters }.take(2)
    }

    /** Perpendicular distance (X,Z plane) from a point to a line segment, plus the closest point on it. */
    private fun distancePointToSegment(p: Point3D, a: Point3D, b: Point3D): Pair<Float, Point3D> {
        val abx = b.x - a.x
        val abz = b.z - a.z
        val apx = p.x - a.x
        val apz = p.z - a.z
        val abLenSq = abx * abx + abz * abz
        val t = if (abLenSq > 1e-6f) ((apx * abx + apz * abz) / abLenSq).coerceIn(0f, 1f) else 0f
        val closestX = a.x + abx * t
        val closestZ = a.z + abz * t
        val closest = Point3D(closestX, a.y, closestZ)
        val dist = distanceXZ(p, closest)
        return dist to closest
    }

    /** Round up to nearest whole integer (used for plant/tree counts). */
    fun ceilToInt(value: Float): Int = kotlin.math.ceil(value.toDouble()).toInt()
    fun ceilToInt(value: Double): Int = kotlin.math.ceil(value).toInt()
}

data class EdgeDistance(
    val edgeStart: Point3D,
    val edgeEnd: Point3D,
    val distanceMeters: Float,
    val closestPoint: Point3D
)
