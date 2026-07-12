package com.example.landscapedesign.ar

import android.opengl.Matrix
import com.example.landscapedesign.model.Point3D
import com.google.ar.core.Plane
import com.google.ar.core.Pose

/**
 * Handles MODE 2: Frozen Photo Interaction.
 *
 * When the camera frame is frozen, we no longer get live ARCore HitResults
 * (those require an active Frame each render tick). Instead we reconstruct the
 * exact camera ray that would have produced that screen pixel — using the
 * SAVED View and Projection matrices from the moment of capture — and
 * intersect that ray with the cached horizontal plane equation. This lets the
 * user tap anywhere on the still image and get back an accurate 3D world
 * point on the same ground plane ARCore already located.
 */
object FrozenFrameProjector {

    /**
     * Converts a 2D screen touch (in pixels) into a normalized device
     * coordinate (NDC) ray in world space, then intersects it with the
     * tracked plane to recover the real-world (x, y, z) meter position.
     *
     * @param screenX, screenY   Touch coordinates in pixels.
     * @param viewportWidth/Height  Size of the view the frozen photo is displayed in.
     * @param viewMatrix       The camera View matrix saved at capture time.
     * @param projectionMatrix The camera Projection matrix saved at capture time.
     * @param plane            The ARCore [Plane] that was tracked at capture time
     *                         (still valid — the session was never destroyed).
     * @return the intersected [Point3D] in world space, or null if the ray is
     *         parallel to the plane or misses it entirely.
     */
    fun projectTouchToPlane(
        screenX: Float,
        screenY: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        plane: Plane
    ): Point3D? {
        val ray = screenPointToWorldRay(
            screenX, screenY, viewportWidth, viewportHeight, viewMatrix, projectionMatrix
        ) ?: return null

        val planePose = plane.centerPose
        val planeNormal = planePose.yAxis // horizontal plane's "up" normal
        val planePoint = floatArrayOf(planePose.tx(), planePose.ty(), planePose.tz())

        val hit = rayPlaneIntersection(
            rayOrigin = ray.origin,
            rayDirection = ray.direction,
            planePoint = planePoint,
            planeNormal = planeNormal
        ) ?: return null

        // Reject hits outside the physically detected plane polygon to avoid
        // "phantom" points floating past real walls/edges.
        val hitPose = Pose.makeTranslation(hit[0], hit[1], hit[2])
        if (!plane.isPoseInPolygon(hitPose)) return null

        return Point3D(hit[0], hit[1], hit[2])
    }

    /** Simpler variant for when you just want to intersect with a known Y-height ground plane
     *  (e.g. reusing the same plane height recorded during Live mode, without a live Plane object). */
    fun projectTouchToGroundHeight(
        screenX: Float,
        screenY: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        groundY: Float
    ): Point3D? {
        val ray = screenPointToWorldRay(
            screenX, screenY, viewportWidth, viewportHeight, viewMatrix, projectionMatrix
        ) ?: return null

        val hit = rayPlaneIntersection(
            rayOrigin = ray.origin,
            rayDirection = ray.direction,
            planePoint = floatArrayOf(0f, groundY, 0f),
            planeNormal = floatArrayOf(0f, 1f, 0f)
        ) ?: return null

        return Point3D(hit[0], hit[1], hit[2])
    }

    private data class WorldRay(val origin: FloatArray, val direction: FloatArray)

    /**
     * Unprojects a screen pixel into a world-space ray using the inverse of the
     * combined View-Projection matrix, sampling both the near and far clip planes.
     */
    private fun screenPointToWorldRay(
        screenX: Float,
        screenY: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ): WorldRay? {
        // Convert pixel coords -> Normalized Device Coordinates (-1..1), flipping Y.
        val ndcX = (2f * screenX / viewportWidth) - 1f
        val ndcY = 1f - (2f * screenY / viewportHeight)

        val viewProjection = FloatArray(16)
        Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)

        val invViewProjection = FloatArray(16)
        if (!Matrix.invertM(invViewProjection, 0, viewProjection, 0)) {
            return null // Non-invertible matrix, camera in a degenerate state.
        }

        val nearPointClip = floatArrayOf(ndcX, ndcY, -1f, 1f)
        val farPointClip = floatArrayOf(ndcX, ndcY, 1f, 1f)

        val nearWorld = FloatArray(4)
        val farWorld = FloatArray(4)
        Matrix.multiplyMV(nearWorld, 0, invViewProjection, 0, nearPointClip, 0)
        Matrix.multiplyMV(farWorld, 0, invViewProjection, 0, farPointClip, 0)

        // Perspective divide.
        if (nearWorld[3] == 0f || farWorld[3] == 0f) return null
        for (i in 0..2) {
            nearWorld[i] /= nearWorld[3]
            farWorld[i] /= farWorld[3]
        }

        val origin = floatArrayOf(nearWorld[0], nearWorld[1], nearWorld[2])
        val direction = floatArrayOf(
            farWorld[0] - nearWorld[0],
            farWorld[1] - nearWorld[1],
            farWorld[2] - nearWorld[2]
        )
        normalize(direction)
        return WorldRay(origin, direction)
    }

    /** Standard ray-plane intersection: t = dot(planePoint - rayOrigin, normal) / dot(direction, normal). */
    private fun rayPlaneIntersection(
        rayOrigin: FloatArray,
        rayDirection: FloatArray,
        planePoint: FloatArray,
        planeNormal: FloatArray
    ): FloatArray? {
        val denom = dot(rayDirection, planeNormal)
        if (kotlin.math.abs(denom) < 1e-6f) return null // Ray parallel to plane.

        val diff = floatArrayOf(
            planePoint[0] - rayOrigin[0],
            planePoint[1] - rayOrigin[1],
            planePoint[2] - rayOrigin[2]
        )
        val t = dot(diff, planeNormal) / denom
        if (t < 0f) return null // Intersection is behind the camera.

        return floatArrayOf(
            rayOrigin[0] + rayDirection[0] * t,
            rayOrigin[1] + rayDirection[1] * t,
            rayOrigin[2] + rayDirection[2] * t
        )
    }

    private fun dot(a: FloatArray, b: FloatArray): Float = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun normalize(v: FloatArray) {
        val len = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (len > 1e-6f) {
            v[0] /= len; v[1] /= len; v[2] /= len
        }
    }

    /** Pose extension: local Y axis (plane normal) in world space. */
    private val Pose.yAxis: FloatArray
        get() {
            val q = floatArrayOf(qx(), qy(), qz(), qw())
            // Rotate the local (0,1,0) up-vector by this pose's rotation quaternion.
            return rotateVectorByQuaternion(floatArrayOf(0f, 1f, 0f), q)
        }

    private fun rotateVectorByQuaternion(v: FloatArray, q: FloatArray): FloatArray {
        // q = (x, y, z, w)
        val qx = q[0]; val qy = q[1]; val qz = q[2]; val qw = q[3]
        val ix = qw * v[0] + qy * v[2] - qz * v[1]
        val iy = qw * v[1] + qz * v[0] - qx * v[2]
        val iz = qw * v[2] + qx * v[1] - qy * v[0]
        val iw = -qx * v[0] - qy * v[1] - qz * v[2]

        return floatArrayOf(
            ix * qw + iw * -qx + iy * -qz - iz * -qy,
            iy * qw + iw * -qy + iz * -qx - ix * -qz,
            iz * qw + iw * -qz + ix * -qy - iy * -qx
        )
    }
}
