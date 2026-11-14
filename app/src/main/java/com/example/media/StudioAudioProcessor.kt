package com.example.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.model.AudioFxPreset
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlin.math.tanh

@UnstableApi
class StudioAudioProcessor(
    @Volatile var volume: Float = 1.0f,
    @Volatile var isMuted: Boolean = false,
    @Volatile var fadeInMs: Long = 0L,
    @Volatile var fadeOutMs: Long = 0L,
    @Volatile var clipDurationUs: Long = 0L,
    @Volatile var fxPreset: AudioFxPreset = AudioFxPreset.NONE
) : BaseAudioProcessor() {

    private var processedFrames: Long = 0L
    private var lowPassStateL = 0f
    private var lowPassStateR = 0f
    private var prevSampleL = 0f
    private var prevSampleR = 0f
    private var echoBuffer = FloatArray(0)
    private var echoWriteIdx = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val delaySamples = (inputAudioFormat.sampleRate * 0.16f).toInt().coerceAtLeast(512)
        echoBuffer = FloatArray(delaySamples * inputAudioFormat.channelCount.coerceAtLeast(1))
        echoWriteIdx = 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)

        val currentVol = if (isMuted) 0f else volume.coerceIn(0f, 2f)
        val fx = fxPreset
        val fadeInFrames = (fadeInMs * sampleRate) / 1000L
        val totalFrames = (clipDurationUs * sampleRate) / 1_000_000L
        val fadeOutFrames = (fadeOutMs * sampleRate) / 1000L

        while (inputBuffer.remaining() >= 2 * channels) {
            var envelope = 1.0f
            if (fadeInFrames > 0 && processedFrames < fadeInFrames) {
                envelope *= (processedFrames.toFloat() / fadeInFrames.toFloat()).coerceIn(0f, 1f)
            }
            if (fadeOutFrames > 0 && totalFrames > 0) {
                val framesLeft = totalFrames - processedFrames
                if (framesLeft < fadeOutFrames) {
                    envelope *= (framesLeft.toFloat() / fadeOutFrames.toFloat()).coerceIn(0f, 1f)
                }
            }

            val totalGain = currentVol * envelope

            for (ch in 0 until channels) {
                val rawSample = inputBuffer.short.toFloat() / 32768f
                var processed = when (fx) {
                    AudioFxPreset.NONE -> rawSample
                    AudioFxPreset.BASS_BOOST -> {
                        val alpha = 0.08f
                        if (ch == 0) {
                            lowPassStateL += alpha * (rawSample - lowPassStateL)
                            rawSample + lowPassStateL * 0.95f
                        } else {
                            lowPassStateR += alpha * (rawSample - lowPassStateR)
                            rawSample + lowPassStateR * 0.95f
                        }
                    }
                    AudioFxPreset.VOCAL_CLARITY -> {
                        val hp = if (ch == 0) {
                            val diff = rawSample - prevSampleL
                            prevSampleL = rawSample
                            diff
                        } else {
                            val diff = rawSample - prevSampleR
                            prevSampleR = rawSample
                            diff
                        }
                        rawSample * 0.85f + hp * 0.55f
                    }
                    AudioFxPreset.LOFI_RADIO -> {
                        val alpha = 0.22f
                        val filtered = if (ch == 0) {
                            lowPassStateL += alpha * (rawSample - lowPassStateL)
                            lowPassStateL
                        } else {
                            lowPassStateR += alpha * (rawSample - lowPassStateR)
                            lowPassStateR
                        }
                        // Mild bit-crushing quantization for vintage radio texture
                        (filtered * 14f).roundToInt() / 14f
                    }
                    AudioFxPreset.ECHO_CHAMBER -> {
                        if (echoBuffer.isNotEmpty()) {
                            val delayed = echoBuffer[echoWriteIdx]
                            val mixed = rawSample + delayed * 0.42f
                            echoBuffer[echoWriteIdx] = (rawSample + delayed * 0.35f).coerceIn(-1f, 1f)
                            echoWriteIdx = (echoWriteIdx + 1) % echoBuffer.size
                            mixed
                        } else {
                            rawSample
                        }
                    }
                    AudioFxPreset.CRISP_TREBLE -> {
                        val high = if (ch == 0) {
                            val diff = rawSample - prevSampleL
                            prevSampleL = rawSample
                            diff
                        } else {
                            val diff = rawSample - prevSampleR
                            prevSampleR = rawSample
                            diff
                        }
                        rawSample + high * 0.75f
                    }
                }

                processed *= totalGain
                // Soft-clipping limiter when boosted > 100%
                if (processed > 0.95f || processed < -0.95f) {
                    processed = tanh(processed.toDouble()).toFloat()
                }
                val outShort = (processed.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
                outputBuffer.putShort(outShort)
            }
            processedFrames++
        }

        // Flush any remaining single bytes if odd-aligned
        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }
        outputBuffer.flip()
    }

    override fun onFlush() {
        processedFrames = 0L
        lowPassStateL = 0f
        lowPassStateR = 0f
        prevSampleL = 0f
        prevSampleR = 0f
        echoBuffer.fill(0f)
        echoWriteIdx = 0
    }

    override fun onReset() {
        onFlush()
        echoBuffer = FloatArray(0)
    }
}
