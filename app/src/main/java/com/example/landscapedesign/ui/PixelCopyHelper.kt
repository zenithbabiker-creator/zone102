package com.example.landscapedesign.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window

/**
 * Asynchronously reads the pixels currently displayed by any on-screen [View]
 * (SceneView's `ArSceneView` included) into a [Bitmap], used by Step 1 to
 * "freeze" the live AR camera feed into a still photo when the user presses
 * Capture. Must be called from the main thread; delivers the result on the
 * main thread via [onResult].
 */
object PixelCopyHelper {

    fun copyFromView(view: View, width: Int, height: Int, onResult: (Bitmap?) -> Unit) {
        val window = findWindow(view)
        if (window == null) {
            onResult(null)
            return
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val location = IntArray(2)
        view.getLocationInWindow(location)
        val rect = Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height
        )

        PixelCopy.request(
            window,
            rect,
            bitmap,
            { copyResult ->
                onResult(if (copyResult == PixelCopy.SUCCESS) bitmap else null)
            },
            Handler(Looper.getMainLooper())
        )
    }

    private fun findWindow(view: View): Window? {
        val activity = view.context as? android.app.Activity ?: return null
        return activity.window
    }
}
