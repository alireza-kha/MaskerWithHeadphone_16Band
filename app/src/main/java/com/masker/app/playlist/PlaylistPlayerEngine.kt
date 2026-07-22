package com.masker.app.playlist

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import com.masker.app.audio.BiquadPeakingEq
import com.masker.app.audio.NotchFilter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.max

/**
 * موتور پخش موسیقی پلی‌لیست: فایل صوتی (mp3/aac/wav/ogg/…) را با [MediaExtractor] و
 * [MediaCodec] به PCM خام رمزگشایی می‌کند، اکولایزر چندباندهٔ گرافیکی و فیلتر حذف فرکانس
 * (Notch — دقیقاً مثل تب ماسکر نویزی) را روی همان نمونه‌های خام اعمال می‌کند و نتیجه را با
 * [AudioTrack] در حالت جریانی پخش می‌کند. تغییر سرعت پخش با [AudioTrack.setPlaybackParams]
 * (بدون تغییر PCM) انجام می‌شود.
 */
class PlaylistPlayerEngine {

    companion object {
        private const val TAG = "PlaylistPlayerEngine"
        private const val TIMEOUT_US = 10_000L

        const val EQ_BAND_COUNT = 7
        val EQ_BAND_FREQUENCIES = doubleArrayOf(60.0, 150.0, 400.0, 1000.0, 2400.0, 6000.0, 15000.0)
    }

    /** شدت (dB، از ۱۵- تا ۱۵+) هر یک از ۷ باند اکولایزر گرافیکی */
    val eqBandGainsDb = FloatArray(EQ_BAND_COUNT) { 0f }

    var notchEnabled = false
        private set
    var notchFrequencyHz = 4000.0
        private set
    var notchWidthOctaves = 0.5f
        private set

    @Volatile
    var speed: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            audioTrack?.let { applyPlaybackParams(it) }
        }

    @Volatile
    var isPlaying = false
        private set

    @Volatile
    var isPaused = false
        private set

    @Volatile
    var durationMs: Long = 0
        private set

    @Volatile
    var positionMs: Long = 0
        private set

    var onCompletion: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var currentFile: File? = null
    private var decodeThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private val pauseLock = Object()

    private var eqFilters: Array<Array<BiquadPeakingEq>>? = null
    private var notchFilterInstances: Array<NotchFilter>? = null
    private var activeSampleRate = 0
    private var activeChannelCount = 0

    private var focusContext: Context? = null
    private var focusRequest: AudioFocusRequest? = null

    /** شروع پخش یک فایل از ابتدا (یا از [startPositionMs] مشخص) */
    fun play(context: Context, file: File, startPositionMs: Long = 0) {
        stopInternal(resetPosition = startPositionMs == 0L)
        focusContext = context.applicationContext
        requestAudioFocus(context)
        currentFile = file
        isPlaying = true
        isPaused = false
        decodeThread = thread(name = "PlaylistDecodeThread") {
            runDecodeLoop(context, file, startPositionMs * 1000)
        }
    }

    fun pause() {
        if (!isPlaying) return
        isPaused = true
        audioTrack?.pause()
    }

    fun resume() {
        if (!isPlaying) return
        isPaused = false
        audioTrack?.play()
        synchronized(pauseLock) { pauseLock.notifyAll() }
    }

    fun stop() {
        stopInternal(resetPosition = true)
        focusContext?.let { abandonAudioFocus(it) }
        focusContext = null
    }

    private fun requestAudioFocus(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun stopInternal(resetPosition: Boolean) {
        isPlaying = false
        isPaused = false
        synchronized(pauseLock) { pauseLock.notifyAll() }
        decodeThread?.let {
            if (it != Thread.currentThread()) it.join(800)
        }
        decodeThread = null
        releaseAudioTrack()
        if (resetPosition) positionMs = 0
    }

    /** رفتن به یک نقطه زمانی مشخص از همان آهنگ در حال پخش */
    fun seekTo(context: Context, positionMs: Long) {
        val file = currentFile ?: return
        play(context, file, positionMs)
    }

    fun setEqBandGain(band: Int, gainDb: Float) {
        if (band !in 0 until EQ_BAND_COUNT) return
        eqBandGainsDb[band] = gainDb
        eqFilters?.let { filters ->
            for (ch in filters.indices) filters[ch][band].setGain(gainDb)
        }
    }

    /** فعال/غیرفعال کردن و تنظیم فیلتر Notch؛ در صورت تغییر حین پخش، بلافاصله اعمال می‌شود */
    fun setNotch(enabled: Boolean, frequencyHz: Double, widthOctaves: Float) {
        notchEnabled = enabled
        notchFrequencyHz = frequencyHz
        notchWidthOctaves = widthOctaves
        notchFilterInstances = if (enabled && activeChannelCount > 0) {
            Array(activeChannelCount) { NotchFilter(activeSampleRate, frequencyHz, widthOctaves) }
        } else {
            null
        }
    }

    private fun releaseAudioTrack() {
        audioTrack?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        audioTrack = null
    }

    private fun applyPlaybackParams(track: AudioTrack) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                track.playbackParams = track.playbackParams.setSpeed(speed)
            } catch (_: Exception) {
            }
        }
    }

    private fun runDecodeLoop(context: Context, file: File, startPositionUs: Long) {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var track: AudioTrack? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) {
                isPlaying = false
                onError?.invoke("این فایل صوتی قابل پخش نیست")
                return
            }
            extractor.selectTrack(trackIndex)
            if (startPositionUs > 0) {
                extractor.seekTo(startPositionUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                positionMs = startPositionUs / 1000
            }

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION) / 1000
            } else 0

            activeSampleRate = sampleRate
            activeChannelCount = channelCount
            eqFilters = Array(channelCount) { ch ->
                Array(EQ_BAND_COUNT) { band -> BiquadPeakingEq(sampleRate, EQ_BAND_FREQUENCIES[band], eqBandGainsDb[band]) }
            }
            notchFilterInstances = if (notchEnabled) {
                Array(channelCount) { NotchFilter(sampleRate, notchFrequencyHz, notchWidthOctaves) }
            } else null

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val newTrack = buildAudioTrack(sampleRate, channelCount)
            if (newTrack == null) {
                isPlaying = false
                onError?.invoke("راه‌اندازی خروجی صدا با خطا مواجه شد")
                return
            }
            track = newTrack
            audioTrack = newTrack
            applyPlaybackParams(newTrack)
            newTrack.play()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var sawOutputEos = false

            while (isPlaying && !sawOutputEos) {
                synchronized(pauseLock) {
                    while (isPaused && isPlaying) {
                        pauseLock.wait(200)
                    }
                }
                if (!isPlaying) break

                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null) {
                            val pcm = ByteArray(bufferInfo.size)
                            outputBuffer.get(pcm)
                            applyDsp(pcm, channelCount)
                            newTrack.write(pcm, 0, pcm.size)
                            positionMs = bufferInfo.presentationTimeUs / 1000
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                }
            }

            if (sawOutputEos && isPlaying) {
                isPlaying = false
                onCompletion?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Playlist playback error", e)
            isPlaying = false
            onError?.invoke(e.message ?: "خطا در پخش فایل")
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                extractor?.release()
            } catch (_: Exception) {
            }
            // اگر onCompletion از همین ترد بلافاصله آهنگ بعدی را با AudioTrack جدیدی شروع کرده
            // باشد، آن نمونه جدید نباید اینجا آزاد شود؛ فقط نمونه‌ای که خود این اجرا ساخته پاک می‌شود
            if (track != null && audioTrack === track) {
                releaseAudioTrack()
            }
        }
    }

    private fun buildAudioTrack(sampleRate: Int, channelCount: Int): AudioTrack? {
        val channelMask = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return null
        val bufferSize = max(minBuf, 4096)
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack creation failed", e)
            null
        }
    }

    /** اعمال اکولایزر ۷باندهٔ گرافیکی و (در صورت فعال بودن) فیلتر Notch روی PCM ۱۶بیتی، درجا */
    private fun applyDsp(pcm: ByteArray, channelCount: Int) {
        val filters = eqFilters ?: return
        val notch = notchFilterInstances

        val buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val shortCount = pcm.size / 2
        val frameCount = shortCount / channelCount

        for (frame in 0 until frameCount) {
            for (ch in 0 until channelCount) {
                val idx = (frame * channelCount + ch) * 2
                val sample = buffer.getShort(idx)
                var value = sample / 32768.0
                val chFilters = filters.getOrNull(ch) ?: filters[0]
                for (band in 0 until EQ_BAND_COUNT) {
                    value = chFilters[band].process(value)
                }
                notch?.let { value = it.getOrNull(ch)?.process(value) ?: value }
                value = value.coerceIn(-1.0, 1.0)
                buffer.putShort(idx, (value * Short.MAX_VALUE).toInt().toShort())
            }
        }
    }
}
