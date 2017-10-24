package com.alexfu.mediaprojectiondemo

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.util.Log
import android.view.Surface
import java.io.File

class ProjectorOutput(private val outputFile: File) {
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex: Int = -1
    private val encodingHandler: Handler = Handler()
    private val encodingRunnable: Runnable = Runnable {
        encodingLoop()
    }

    var surface: Surface? = null
    var width = 0
    var height = 0

    companion object {
        private val LOG_TAG = "ProjectorOutput"
        private val ENCODE_TIMEOUT: Long = 100
        val MIME_TYPE = "video/avc"
    }

    fun start() {
        setUpEncoder()
        startEncoder()
        startEncodingLoop()
    }

    fun stop() {
        stopEncodingLoop()
        stopEncoder()
        cleanUpResources()
    }

    private fun setUpEncoder() {
        val videoFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height)
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 2000000)
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15)
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)

        val codec = MediaCodec.createEncoderByType(MIME_TYPE)
        codec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = codec.createInputSurface()
        encoder = codec
    }

    private fun startEncoder() {
        encoder?.start()
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        trackIndex = -1
    }

    private fun stopEncoder() {
        muxer?.stop()
        encoder?.stop()
    }

    private fun cleanUpResources() {
        encoder = null

        surface?.release()
        surface = null

        muxer?.release()
        muxer = null
    }

    private fun startEncodingLoop() {
        encodingHandler.post(encodingRunnable)
    }

    private fun stopEncodingLoop() {
        encodingHandler.removeCallbacks(encodingRunnable)
    }

    private fun encodingLoop() {
        try {
            encoder?.apply {
                val bufferInfo = MediaCodec.BufferInfo()
                var outputBuffers = this.outputBuffers

                while (true) {
                    val encoderStatus = this.dequeueOutputBuffer(bufferInfo, 10000)
                    when {
                        encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            Log.d(LOG_TAG, "Encoder status = INFO_TRY_AGAIN_LATER")
                            return
                        }
                        encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                            Log.d(LOG_TAG, "Encoder status = INFO_OUTPUT_BUFFERS_CHANGED")
                            outputBuffers = this.outputBuffers
                        }
                        encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            Log.d(LOG_TAG, "Encoder status = INFO_OUTPUT_FORMAT_CHANGED")
                            val newFormat = this.outputFormat
                            muxer?.apply {
                                trackIndex = this.addTrack(newFormat)
                                this.start()
                            }
                        }
                        encoderStatus < 0 -> {
                            Log.d(LOG_TAG, "Encoder status = UNKNOWN ($encoderStatus)")
                        }
                        else -> {
                            val data = outputBuffers[encoderStatus]
                            if (bufferInfo.onlyContainsCodecConfig) {
                                Log.d(LOG_TAG, "Encoder data only contains codec config data, skipping")
                                bufferInfo.size = 0
                            }

                            if (bufferInfo.size != 0) {
                                muxer?.writeSampleData(trackIndex, data, bufferInfo)
                                Log.d(LOG_TAG, "Wrote ${bufferInfo.size} bytes")
                            }

                            this.releaseOutputBuffer(encoderStatus, false)

                            if (bufferInfo.endOfStream) {
                                Log.d(LOG_TAG, "End of stream")
                                return
                            }
                        }
                    }
                }
            }
        } finally {
            encodingHandler.postDelayed(encodingRunnable, ENCODE_TIMEOUT)
        }
    }
}