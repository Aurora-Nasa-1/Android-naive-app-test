package com.ncm.player.service

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

class FadeAudioProcessor : BaseAudioProcessor() {
    private var fadeDurationFrames: Long = 0
    private var currentFrameCount: Long = 0
    private var isFadingIn = false
    private var isFadingOut = false
    private var fadeDurationMs: Long = 2000

    fun setFadeDuration(durationMs: Long) {
        this.fadeDurationMs = durationMs
        updateFadeDurationFrames()
    }

    private fun updateFadeDurationFrames() {
        if (inputAudioFormat != AudioFormat.NOT_SET) {
            fadeDurationFrames = (fadeDurationMs * inputAudioFormat.sampleRate) / 1000
        }
    }

    fun startFadeIn() {
        currentFrameCount = 0
        isFadingIn = true
        isFadingOut = false
    }

    fun startFadeOut() {
        currentFrameCount = 0
        isFadingIn = false
        isFadingOut = true
    }

    override fun onFlush() {
        super.onFlush()
        // ExoPlayer flushes processors on seek or when starting a new item.
        // We trigger fade-in here to ensure it aligns perfectly with the first audio samples.
        startFadeIn()
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
             // We only support 16-bit and Float for now
             return AudioFormat.NOT_SET
        }
        updateFadeDurationFrames()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isFadingIn && !isFadingOut) {
            val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val remaining = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(remaining)

        val bytesPerFrame = inputAudioFormat.bytesPerFrame
        val framesToProcess = remaining / bytesPerFrame

        val durationF = fadeDurationFrames.toFloat()

        for (i in 0 until framesToProcess) {
            if (isFadingIn || isFadingOut) {
                val volumeScale = if (durationF > 0f) {
                    currentFrameCount.toFloat() / durationF
                } else {
                    1.0f
                }

                val finalScale = when {
                    isFadingIn -> min(1.0f, volumeScale)
                    isFadingOut -> max(0.0f, 1.0f - volumeScale)
                    else -> 1.0f
                }

                if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
                    for (channel in 0 until inputAudioFormat.channelCount) {
                        val sample = inputBuffer.getShort()
                        outputBuffer.putShort((sample * finalScale).toInt().toShort())
                    }
                } else if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
                    for (channel in 0 until inputAudioFormat.channelCount) {
                        val sample = inputBuffer.getFloat()
                        outputBuffer.putFloat(sample * finalScale)
                    }
                }

                currentFrameCount++
                if (currentFrameCount >= fadeDurationFrames) {
                    isFadingIn = false
                    isFadingOut = false
                }
            } else {
                // If fade ended while processing this buffer, just copy remaining samples
                if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
                    outputBuffer.putShort(inputBuffer.getShort())
                } else if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
                    outputBuffer.putFloat(inputBuffer.getFloat())
                }
            }
        }
        outputBuffer.flip()
    }
}
