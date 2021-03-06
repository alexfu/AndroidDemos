package com.alexfu.mediaprojectiondemo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

/**
 * This is where all of the screen recording work is done. Screen recording is done via
 * MediaProjection#createVirtualDisplay, where the contents of the screen will be sent to a given
 * Surface.
 *
 * Prior to calling MediaProjection#createVirtualDisplay, you need to obtain an instance of
 * MediaProjection, which can be obtained from a MediaProjectionManager.
 *
 * Once you have a MediaProjectionManager, you must request permission to record the screen.
 * This is done by calling MediaProjectionManager#createScreenCaptureIntent. The resulting Intent
 * must be used to start an Activity for result (Activity#startActivityForResult).
 *
 * After you get the Activity result (from Activity#onActivityResult), you will use both resultCode
 * and resultData to obtain an instance of MediaProjection. This is done by calling
 * MediaProjectionManager#getMediaProjection.
 *
 * Now screen recording can be started by calling MediaProjection#createVirtualDisplay.
 */
class Projector(context: Context) {
    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private val displayMetrics = context.resources.displayMetrics
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    lateinit var output: ProjectorOutput
    val isProjecting: Boolean
        get() = virtualDisplay != null

    companion object {
        val REQ_SCREEN_CAPTURE_PERMISSION = 112
    }

    fun requestScreenCapturePermission(activity: Activity) {
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(intent, REQ_SCREEN_CAPTURE_PERMISSION)
    }

    fun setScreenCapturePermissionResult(resultCode: Int, resultData: Intent) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
    }

    fun startScreenCapture() {
        if (mediaProjection == null) {
            val message = "Oops! Looks like you're attempting to start screen capture BEFORE requesting permission.\nPlease call requestScreenCapturePermission before calling startScreenCapture."
            throw RuntimeException(message)
        }

        output.width = displayMetrics.widthPixels
        output.height = displayMetrics.heightPixels
        output.start()

        mediaProjection?.apply {
            virtualDisplay = this.createVirtualDisplay(
                    "ScreenCapture",
                    displayMetrics.widthPixels,
                    displayMetrics.heightPixels,
                    displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    output.surface,
                    null,
                    null
            )
        }
    }

    fun stopScreenCapture() {
        output.stop()

        mediaProjection?.stop()
        mediaProjection = null

        virtualDisplay?.release()
        virtualDisplay = null
    }
}