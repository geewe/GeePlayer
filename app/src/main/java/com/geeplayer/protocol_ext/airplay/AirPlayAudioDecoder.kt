package com.geeplayer.protocol_ext.airplay

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * AirPlay ALAC 音频解码器 — ALAC → PCM
 *
 * 使用 Android MediaCodec 解码 ALAC (Apple Lossless Audio Codec)
 * API 23+ 原生支持 audio/alac
 */
class AirPlayAudioDecoder {
    private companion object {
        private const val TAG = "AirPlayAudioDecoder"
        private const val MIME_ALAC = "audio/alac"
    }

    enum class DecoderState { IDLE, INITIALIZED, DECODING, DRAINED, ERROR }

    private var decoder: MediaCodec? = null
    private var state: DecoderState = DecoderState.IDLE
    private var sampleRate: Int = 44100
    private var channels: Int = 2

    var onPcmData: ((ByteBuffer, MediaCodec.BufferInfo) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * 初始化 ALAC 解码器
     */
    fun init(sampleRate: Int = 44100, channels: Int = 2, cookie: ByteArray? = null): Boolean {
        try {
            this.sampleRate = sampleRate
            this.channels = channels

            val format = MediaFormat.createAudioFormat(MIME_ALAC, sampleRate, channels)
            cookie?.let { format.setByteBuffer("csd-0", ByteBuffer.wrap(it)) }

            decoder = MediaCodec.createDecoderByType(MIME_ALAC).apply {
                configure(format, null, null, 0)
                start()
            }
            state = DecoderState.INITIALIZED
            Log.i(TAG, "ALAC decoder initialized: ${sampleRate}Hz, ${channels}ch")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init ALAC decoder", e)
            state = DecoderState.ERROR
            onError?.invoke("ALAC init failed: ${e.message}")
            return false
        }
    }

    /**
     * 解码一帧 ALAC 数据 → PCM
     */
    fun decodeFrame(input: ByteArray): ByteArray? {
        val codec = decoder ?: return null
        if (state == DecoderState.ERROR) return null

        state = DecoderState.DECODING
        val output = ByteArrayOutputStream()

        try {
            val inputIdx = codec.dequeueInputBuffer(10000)
            if (inputIdx >= 0) {
                val buf = codec.getInputBuffer(inputIdx) ?: return null
                buf.clear()
                buf.put(input)
                codec.queueInputBuffer(inputIdx, 0, input.size, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            val info = MediaCodec.BufferInfo()
            var done = false
            while (!done) {
                val outIdx = codec.dequeueOutputBuffer(info, 10000)
                when {
                    outIdx >= 0 -> {
                        val ob = codec.getOutputBuffer(outIdx) ?: break
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val pcm = ByteArray(info.size)
                        ob.get(pcm)
                        output.write(pcm)
                        onPcmData?.invoke(ob, info)
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) done = true
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> done = true
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* ignore */ }
                }
            }
            return output.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Decode error", e)
            onError?.invoke("Decode error: ${e.message}")
            state = DecoderState.ERROR
            return null
        }
    }

    fun decodeBuffer(input: ByteArray, frameSize: Int = 1024): ByteArray {
        val all = ByteArrayOutputStream()
        var offset = 0
        while (offset + frameSize <= input.size) {
            decodeFrame(input.copyOfRange(offset, offset + frameSize))?.let { all.write(it) }
            offset += frameSize
        }
        if (offset < input.size) {
            decodeFrame(input.copyOfRange(offset, input.size))?.let { all.write(it) }
        }
        return all.toByteArray()
    }

    fun release() {
        try {
            decoder?.apply {
                if (state != DecoderState.ERROR) stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Release error", e)
        }
        decoder = null
        state = DecoderState.IDLE
        Log.d(TAG, "ALAC decoder released")
    }

    val isInitialized: Boolean get() = state != DecoderState.IDLE && state != DecoderState.ERROR
    val currentState: DecoderState get() = state
}
