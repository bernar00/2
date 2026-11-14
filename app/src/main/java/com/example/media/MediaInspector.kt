package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class InspectedMedia(
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val hasAudio: Boolean,
    val thumbnails: List<Bitmap>,
    val waveform: List<Float>
)

object MediaInspector {

    suspend fun inspectMedia(
        context: Context,
        uri: Uri,
        isAudioOnly: Boolean
    ): InspectedMedia = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(context, uri)
        var durationMs = 0L
        var width = 1280
        var height = 720
        var hasAudio = isAudioOnly
        val thumbnails = mutableListOf<Bitmap>()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            if (!isAudioOnly) {
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 1280
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 720
                val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) {
                    width = h
                    height = w
                } else {
                    width = w
                    height = h
                }
                val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                hasAudio = hasAudioStr == null || hasAudioStr.equals("yes", ignoreCase = true)

                if (durationMs > 0L) {
                    val frameCount = 8
                    for (i in 0 until frameCount) {
                        val timeUs = ((durationMs * 1000L) * i / frameCount.coerceAtLeast(1))
                        val rawFrame = retriever.getFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        if (rawFrame != null) {
                            val targetH = 88
                            val aspect = rawFrame.width.toFloat() / rawFrame.height.coerceAtLeast(1)
                            val targetW = (targetH * aspect).toInt().coerceIn(48, 180)
                            val scaled = Bitmap.createScaledBitmap(rawFrame, targetW, targetH, true)
                            if (scaled != rawFrame) {
                                rawFrame.recycle()
                            }
                            thumbnails.add(scaled)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            if (durationMs <= 0L) durationMs = 5000L
        } finally {
            runCatching { retriever.release() }
        }

        if (durationMs <= 0L) {
            durationMs = probeDurationViaExtractor(context, uri).coerceAtLeast(1000L)
        }

        val waveform = if (hasAudio) {
            extractWaveform(context, uri, durationMs, bars = 56)
        } else {
            emptyList()
        }

        InspectedMedia(
            displayName = displayName,
            durationMs = durationMs.coerceAtLeast(250L),
            width = width.coerceAtLeast(64),
            height = height.coerceAtLeast(64),
            hasAudio = hasAudio,
            thumbnails = thumbnails,
            waveform = waveform
        )
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: "Clip"
        }
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Media Clip"
    }

    private fun probeDurationViaExtractor(context: Context, uri: Uri): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    return format.getLong(MediaFormat.KEY_DURATION) / 1000L
                }
            }
            3000L
        } catch (_: Throwable) {
            3000L
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun extractWaveform(
        context: Context,
        uri: Uri,
        durationMs: Long,
        bars: Int
    ): List<Float> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) {
                return List(bars) { 0.25f }
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return List(bars) { 0.3f }
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val buckets = DoubleArray(bars)
            val bucketCounts = IntArray(bars)
            val durationUs = (durationMs * 1000L).coerceAtLeast(1L)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var sampleIterations = 0
            val maxIterations = 260 // keep fast on mobile

            // Seek across intervals for quick representative sampling
            var targetBucket = 0
            while (!outputDone && sampleIterations < maxIterations) {
                sampleIterations++
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(4000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val size = extractor.readSampleData(inBuf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, size, pts, 0)
                                targetBucket++
                                if (durationUs > 2_000_000L && targetBucket % 2 == 0) {
                                    val nextStepUs = (targetBucket.toLong() * durationUs) / (maxIterations / 2)
                                    if (nextStepUs < durationUs) {
                                        extractor.seekTo(nextStepUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                                    } else {
                                        extractor.advance()
                                    }
                                } else {
                                    extractor.advance()
                                }
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 4000L)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val bucketIdx = ((bufferInfo.presentationTimeUs.coerceAtLeast(0L) * bars) / durationUs)
                            .toInt().coerceIn(0, bars - 1)
                        var sumSq = 0.0
                        var count = 0
                        val step = maxOf(1, shortBuf.remaining() / 64)
                        var pos = shortBuf.position()
                        val limit = shortBuf.limit()
                        while (pos < limit) {
                            val s = shortBuf.get(pos) / 32768.0
                            sumSq += s * s
                            count++
                            pos += step
                        }
                        if (count > 0) {
                            val rms = sqrt(sumSq / count)
                            buckets[bucketIdx] += rms
                            bucketCounts[bucketIdx]++
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }

            val raw = List(bars) { i ->
                if (bucketCounts[i] > 0) {
                    (buckets[i] / bucketCounts[i]).toFloat()
                } else {
                    0f
                }
            }
            val maxVal = raw.maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
            // Fill any unsampled sparse gaps smoothly
            raw.mapIndexed { idx, v ->
                val value = if (v > 0f) v else {
                    val prev = raw.getOrNull(idx - 1) ?: 0.15f
                    val next = raw.getOrNull(idx + 1) ?: 0.15f
                    (prev + next) * 0.5f
                }
                (value / maxVal).coerceIn(0.12f, 1.0f)
            }
        } catch (_: Throwable) {
            List(bars) { idx ->
                val wave = 0.35f + 0.45f * abs(kotlin.math.sin(idx * 0.45f))
                wave.coerceIn(0.15f, 0.95f)
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
