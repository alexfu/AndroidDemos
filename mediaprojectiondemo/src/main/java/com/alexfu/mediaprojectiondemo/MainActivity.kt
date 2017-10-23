package com.alexfu.mediaprojectiondemo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.widget.Button
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var surfaceView: SurfaceView
    private lateinit var outputDir: File
    private lateinit var screenCaptureButton: Button
    private var trackIndex = -1
    private val consumeEncoderBufferHandler = Handler()
    private val consumeEncoderBufferRunnable = object : Runnable {
        override fun run() {
            consumeEncoderBuffer()
            scheduleConsumeEncoderBuffer()
        }
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null
    private var surface: Surface? = null
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null

    companion object {
        val REQUEST_MEDIA_PROJECTION = 112
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surface_view) as SurfaceView
        outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        screenCaptureButton = findViewById(R.id.screen_capture) as Button
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

        screenCaptureButton.text = "Stop recording"

        setUpEncoder()

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

        scheduleConsumeEncoderBuffer()
    }

    private fun stopScreenCapture() {
        screenCaptureButton.text = "Start recording"

        mediaCodec?.signalEndOfInputStream()
        unScheduleConsumeEncoderBuffer()

        tearDownEncoder()
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

    private fun setUpEncoder() {
        val videoFormat = MediaFormat.createVideoFormat("video/avc", 320, 240)
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 2000000)
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15)
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)

        mediaCodec = MediaCodec.createEncoderByType("video/avc")
        mediaCodec?.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = mediaCodec?.createInputSurface()
        mediaCodec?.start()

        val outputFile = File(outputDir, "screen_record_${System.currentTimeMillis()}.mp4")
        mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        trackIndex = -1
    }

    private fun tearDownEncoder() {
        mediaCodec?.stop()
        mediaCodec?.release()
        mediaCodec = null

        surface?.release()
        surface = null

        mediaMuxer?.stop()
        mediaMuxer?.release()
        mediaMuxer = null
    }

    private fun consumeEncoderBuffer() {
        mediaCodec?.apply {
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBuffers = this.outputBuffers

            while (true) {
                val encoderStatus = this.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        Log.d("MediaProjectionDemo", "Encoder status = INFO_TRY_AGAIN_LATER")
                        return
                    }
                    encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                        Log.d("MediaProjectionDemo", "Encoder status = INFO_OUTPUT_BUFFERS_CHANGED")
                        outputBuffers = this.outputBuffers
                    }
                    encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        Log.d("MediaProjectionDemo", "Encoder status = INFO_OUTPUT_FORMAT_CHANGED")
                        val newFormat = this.outputFormat
                        mediaMuxer?.apply {
                            trackIndex = this.addTrack(newFormat)
                            this.start()
                        }
                    }
                    encoderStatus < 0 -> {
                        Log.d("MediaProjectionDemo", "Encoder status = UNKNOWN ($encoderStatus)")
                    }
                    else -> {
                        val data = outputBuffers[encoderStatus]
                        if (bufferInfo.onlyContainsCodecConfig) {
                            Log.d("MediaProjectionDemo", "Encoder data only contains codec config data, skipping")
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size != 0) {
                            mediaMuxer?.writeSampleData(trackIndex, data, bufferInfo)
                            Log.d("MediaProjectionDemo", "Wrote ${bufferInfo.size} bytes")
                        }

                        this.releaseOutputBuffer(encoderStatus, false)

                        if (bufferInfo.endOfStream) {
                            Log.d("MediaProjectionDemo", "End of stream")
                            return
                        }
                    }
                }
            }
        }
    }

    private fun scheduleConsumeEncoderBuffer() {
        consumeEncoderBufferHandler.postDelayed(consumeEncoderBufferRunnable, 10)
    }

    private fun unScheduleConsumeEncoderBuffer() {
        consumeEncoderBufferHandler.removeCallbacks(consumeEncoderBufferRunnable)
    }
}
