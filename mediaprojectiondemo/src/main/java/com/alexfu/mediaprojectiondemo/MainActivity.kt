package com.alexfu.mediaprojectiondemo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.widget.Button

class MainActivity : AppCompatActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var surfaceView: SurfaceView
    private lateinit var surface: Surface
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null

    companion object {
        val REQUEST_MEDIA_PROJECTION = 112
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surface_view) as SurfaceView
        surface = surfaceView.holder.surface

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val screenCaptureButton = findViewById(R.id.screen_capture) as Button
        screenCaptureButton.setOnClickListener {
            toggleScreenCapture()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_MEDIA_PROJECTION -> {
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        setUpMediaProjection(resultCode = resultCode, resultData = data)
                        startScreenCapture()
                    }
                }
            }
            else -> {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopScreenCapture()
    }

    override fun onDestroy() {
        super.onDestroy()
        tearDownMediaProjection()
    }

    private fun toggleScreenCapture() {
        if (virtualDisplay == null) {
            startScreenCapture()
        } else {
            stopScreenCapture()
        }
    }

    private fun startScreenCapture() {
        if (mediaProjection == null) {
            requestScreenCapturePermission()
            return
        }

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        mediaProjection?.apply {
            virtualDisplay = this.createVirtualDisplay(
                    "ScreenCapture",
                    surfaceView.width,
                    surfaceView.height,
                    displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    null
            )
        }
    }

    private fun stopScreenCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun requestScreenCapturePermission() {
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    private fun setUpMediaProjection(resultCode: Int, resultData: Intent) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
    }

    private fun tearDownMediaProjection() {
        mediaProjection?.stop()
        mediaProjection = null
    }
}
