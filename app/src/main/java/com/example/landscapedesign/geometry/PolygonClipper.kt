package com.example.landscapedesign.geometry

import com.example.landscapedesign.model.BorderElement
import com.example.landscapedesign.model.Point3D
import com.example.landscapedesign.model.ShapeElement
import com.example.landscapedesign.model.ShapeType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Handles subtractive area math for Step 4: cutting border-strip areas and
 * major-tree-hedge "no lawn" zones out of the base garden polygon.
 *
 * Approach:
 * 1. Approximate every Circle/Arc shape as an N-sided polygon (for accurate area
 *    and, if needed, visual clipping against the base polygon).
 * 2. Sum enclosed areas of all tree zones (Circle/Arc/Polygon hedge rings).
 * 3. Sum border-strip footprint areas (net length x thickness).
 * 4. Net Lawn Area = Base Area - SUM(tree zone areas) - SUM(border footprint areas),
 *    clamped to zero. Overlapping zones are assumed non-overlapping by design
 *    convention (hedge rings are placed apart); if visual overlap detection is
 *    required later, a full Sutherland-Hodgman boolean union can be layered on
 *    top of approximatedPolygon() below.
 */
object PolygonClipper {

    /** Turns a Circle or Arc into a polygon approximation (for area/visual clip use). */
    fun approximatePolygon(shape: ShapeElement, segments: Int = 32): List<Point3D> {
        if (shape.type == ShapeType.POLYGON) return shape.polygonVertices
        val center = shape.centerWorld ?: return emptyList()
        val radius = shape.radiusMeters ?: return emptyList()
        val startDeg = shape.startAngleDeg ?: 0f
        val sweepDeg = if (shape.type == ShapeType.ARC) (shape.sweepAngleDeg ?: 360f) else 360f

        val points = mutableListOf<Point3D>()
        val steps = if (shape.type == ShapeType.CIRCLE) segments else (segments * (sweepDeg / 360f)).toInt().coerceAtLeast(2)
        for (i in 0..steps) {
            val angleDeg = startDeg + (sweepDeg * i / steps)
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val x = center.x + radius * cos(angleRad).toFloat()
            val z = center.z + radius * sin(angleRad).toFloat()
            points.add(Point3D(x, center.y, z))
        }
        // For an arc, close the fan back to the center so the enclosed area is a pie slice.
        if (shape.type == ShapeType.ARC) {
            points.add(center)
        }
        return points
    }

    /** Total area (m²) occupied by "no-lawn" tree zones (hedge rings, circles, arcs, polygons). */
    fun totalTreeZoneArea(shapes: List<ShapeElement>): Float {
        return shapes.sumOf { it.enclosedAreaMeters2().toDouble() }.toFloat()
    }

    /** Total footprint area (m²) occupied by the 3-tier borders. */
    fun totalBorderFootprintArea(borders: List<BorderElement>): Float {
        return borders.sumOf { it.footprintAreaMeters2().toDouble() }.toFloat()
    }

    /**
     * Computes the Net Lawn Area following the strict hierarchy:
     * Base Area - (Border Footprint Areas + Tree Zone Areas), clamped at 0.
     */
    fun netLawnArea(
        baseAreaMeters2: Float,
        borders: List<BorderElement>,
        treeZoneShapes: List<ShapeElement>
    ): Float {
        val borderArea = totalBorderFootprintArea(borders)
        val treeZoneArea = totalTreeZoneArea(treeZoneShapes)
        val net = baseAreaMeters2 - (borderArea + treeZoneArea)
        return net.coerceAtLeast(0f)
    }

    /**
     * Simple Sutherland-Hodgman clip: clips subjectPolygon against a CONVEX
     * clipPolygon, returning the intersection polygon. Provided for future
     * visual "cut-out" rendering of tree zones directly on the base garden
     * polygon outline (base garden polygons captured in Step 1 are typically
     * convex-ish user-drawn boundaries).
     */
    fun clipPolygon(subject: List<Point3D>, clip: List<Point3D>): List<Point3D> {
        if (subject.isEmpty() || clip.isEmpty()) return emptyList()
        var output = subject
        for (i in clip.indices) {
            if (output.isEmpty()) break
            val a = clip[i]
            val b = clip[(i + 1) % clip.size]
            output = clipEdge(output, a, b)
        }
        return output
    }

    private fun clipEdge(poly: List<Point3D>, a: Point3D, b: Point3D): List<Point3D> {
        val result = mutableListOf<Point3D>()
        for (i in poly.indices) {
            val current = poly[i]
            val prev = poly[(i - 1 + poly.size) % poly.size]
            val currentInside = isInside(current, a, b)
            val prevInside = isInside(prev, a, b)
            if (currentInside) {
                if (!prevInside) result.add(intersect(prev, current, a, b))
                result.add(current)
            } else if (prevInside) {
                result.add(intersect(prev, current, a, b))
            }
        }
        return result
    }

    private fun isInside(p: Point3D, a: Point3D, b: Point3D): Boolean {
        return (b.x - a.x) * (p.z - a.z) - (b.z - a.z) * (p.x - a.x) >= 0
    }

    private fun intersect(p1: Point3D, p2: Point3D, a: Point3D, b: Point3D): Point3D {
        val a1 = b.z - a.z
        val b1 = a.x - b.x
        val c1 = a1 * a.x + b1 * a.z

        val a2 = p2.z - p1.z
        val b2 = p1.x - p2.x
        val c2 = a2 * p1.x + b2 * p1.z

        val det = a1 * b2 - a2 * b1
        return if (kotlin.math.abs(det) < 1e-6f) {
            p2
        } else {
            val x = (b2 * c1 - b1 * c2) / det
            val z = (a1 * c2 - a2 * c1) / det
            Point3D(x, p1.y, z)
        }
    }
}
