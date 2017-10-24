package com.alexfu.mediaprojectiondemo

import android.app.Activity
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.widget.Button
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var projector: Projector
    private val screenCaptureButton: Button by lazy { findViewById(R.id.screen_capture) as Button  }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setUpProjector()
        screenCaptureButton.setOnClickListener {
            toggleScreenCapture()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            Projector.REQ_SCREEN_CAPTURE_PERMISSION -> {
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        projector.setScreenCapturePermissionResult(resultCode = resultCode, resultData = data)
                        startScreenCapture()
                    }
                }
            }
            else -> {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
    }

    private fun toggleScreenCapture() {
        if (projector.isProjecting) {
            stopScreenCapture()
        } else {
            projector.requestScreenCapturePermission(this)
        }
    }

    private fun startScreenCapture() {
        screenCaptureButton.text = "Stop recording"
        projector.startScreenCapture()
    }

    private fun stopScreenCapture() {
        screenCaptureButton.text = "Start recording"
        projector.stopScreenCapture()
    }

    private fun setUpProjector() {
        val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val outputFile = File(outputDir, "screen_record_${System.currentTimeMillis()}.mp4")

        projector = Projector(this)
        projector.output = ProjectorOutput(outputFile)
    }
}
