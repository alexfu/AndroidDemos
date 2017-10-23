package com.alexfu.mediaprojectiondemo

import android.media.MediaCodec

val MediaCodec.BufferInfo.endOfStream: Boolean
    get() = (flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

val MediaCodec.BufferInfo.onlyContainsCodecConfig: Boolean
    get() = (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0