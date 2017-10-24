package com.alexfu.mediaprojectiondemo

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.File

class ProjectorOutput(private val outputFile: File) {
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex: Int = -1

    var surface: Surface? = null
    var width = 0
    var height = 0

    companion object {
        private val LOG_TAG = "ProjectorOutput"
        val MIME_TYPE = "video/avc"
    }

    fun start() {
        setUpEncoder()
        startEncoder()
    }

    fun stop() {
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
        codec.setCallback(object : MediaCodec.Callback() {
            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                val data = codec.getOutputBuffer(index)
                if (info.size != 0) {
                    muxer?.writeSampleData(trackIndex, data, info)
                    Log.d(LOG_TAG, "Wrote ${info.size} bytes")
                }
                codec.releaseOutputBuffer(index, false)
            }

            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                // No op
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                val newFormat = codec.outputFormat
                muxer?.apply {
                    trackIndex = this.addTrack(newFormat)
                    this.start()
                }
            }

            override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) {
                error.printStackTrace()
            }
        })
        surface = codec.createInputSurface()

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        encoder = codec
    }

    private fun startEncoder() {
        encoder?.start()
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
}