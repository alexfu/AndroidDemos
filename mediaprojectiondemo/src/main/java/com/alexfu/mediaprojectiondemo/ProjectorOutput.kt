package com.alexfu.mediaprojectiondemo

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.File

/**
 * This is where the screen recording gets saved as an MP4. The basic process for this is, taking
 * the input (Surface) and sending it to a MediaCodec (for encoding) and then passing the resulting
 * data to a MediaMuxer where the data will written to a file.
 *
 *    +-------+        +------------+        +------------+
 *    + Input +  --->  + MediaCodec +  --->  + MediaMuxer +
 *    +-------+        +------------+        +------------+
 *
 * The first thing we do is to prepare the encoder (MediaCodec) by giving it some parameters about
 * our recording, such as width/height and MIME type and also setting up a callback for processing
 * the resulting data. This is also where we initialize our MediaMuxer.
 *
 * Once the encoder is prepared and initialized
 * (MediaCodec.createEncoderByType) we request a Surface from the encoder to use as input in place
 * of buffers.
 *
 * +-------------------------------------------------------------------------+
 * + NOTE: The Surface that we requested here, gets passed to our Projector. +
 * +-------------------------------------------------------------------------+
 *
 * With the encoder ready to go, we can start the encoder by calling MediaCodec#start. From here,
 * all we need to do it to respond to callback events from the codec.
 *
 * Whenever MediaCodec.Callback#onOutputFormatChanged is called, we add the new format to our
 * MediaMuxer. This is also where we start the MediaMuxer. This method should only be triggered
 * once.
 *
 * Whenever MediaCodec.Callback#onOutputBufferAvailable is called, we pass the output buffer to our
 * MediaMuxer for writing (MediaMuxer#writeSampleData).
 *
 * We ignore MediaCodec.Callback#onInputBufferAvailable since our Projector will be the one filling
 * the input buffer (Surface) with data.
 */
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