package com.masker.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * موتور تولید ماسکر «تونال» (Tonal Masker).
 * برخلاف [NoiseEngine] که از نویز فیلترشده استفاده می‌کند، این موتور برای هر یک
 * از ۱۶ فرکانس، یک تن خالص (موج سینوسی) تولید می‌کند. ماسکرهای تونال معمولاً برای
 * تطبیق دقیق با پرده صدای وزوز گوش (Tinnitus Pitch) کاربر استفاده می‌شوند.
 */
class TonalEngine {

    companion object {
        private const val TAG = "TonalEngine"
        const val SAMPLE_RATE = NoiseEngine.SAMPLE_RATE
        const val TONE_COUNT = NoiseEngine.BAND_COUNT

        // از همان فرکانس‌های مرجع نویز استفاده می‌شود تا بین دو حالت هماهنگی وجود داشته باشد
        val TONE_FREQUENCIES = NoiseEngine.BAND_FREQUENCIES
    }

    // شدت هر تن، بین ۰ (خاموش) تا ۱ (حداکثر)
    val toneGains = FloatArray(TONE_COUNT) { 0f }

    var masterVolume: Float = 0.7f
    var leftVolume: Float = 1.0f
    var rightVolume: Float = 1.0f

    @Volatile
    var isPlaying: Boolean = false
        private set

    private var playbackThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null

    private val livePhases = DoubleArray(TONE_COUNT)
    private val phaseIncrements = DoubleArray(TONE_COUNT) { 2.0 * PI * TONE_FREQUENCIES[it] / SAMPLE_RATE }

    fun start(context: Context? = null) {
        if (isPlaying) return
        isPlaying = true
        for (i in livePhases.indices) livePhases[i] = 0.0
        if (context != null) requestAudioFocus(context)
        playbackThread = thread(name = "MaskerTonalPlaybackThread") { playLoop() }
    }

    fun stop(context: Context? = null) {
        isPlaying = false
        playbackThread?.join(500)
        playbackThread = null
        audioTrack?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        audioTrack = null
        if (context != null) abandonAudioFocus(context)
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

    private fun buildMonoSample(phases: DoubleArray): Double {
        var sum = 0.0
        for (i in 0 until TONE_COUNT) {
            if (toneGains[i] > 0f) {
                sum += sin(phases[i]) * toneGains[i]
            }
            phases[i] += phaseIncrements[i]
            if (phases[i] > 2.0 * PI) phases[i] -= 2.0 * PI
        }
        return sum / TONE_COUNT
    }

    private fun playLoop() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBuf, 4096)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack creation failed", e)
            isPlaying = false
            return
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack failed to initialize (state=${track.state})")
            isPlaying = false
            track.release()
            return
        }

        audioTrack = track
        track.play()

        val chunkFrames = 1024
        val chunk = ShortArray(chunkFrames * 2)

        while (isPlaying) {
            var idx = 0
            for (i in 0 until chunkFrames) {
                val mono = buildMonoSample(livePhases) * masterVolume
                val l = (mono * leftVolume).coerceIn(-1.0, 1.0)
                val r = (mono * rightVolume).coerceIn(-1.0, 1.0)
                chunk[idx++] = (l * Short.MAX_VALUE).toInt().toShort()
                chunk[idx++] = (r * Short.MAX_VALUE).toInt().toShort()
            }
            track.write(chunk, 0, chunk.size)
        }
    }

    /**
     * رندر آفلاین صدای تونال و ذخیره در فایل WAV.
     */
    fun renderToFile(outputFile: File, durationSeconds: Int): Boolean {
        return try {
            val renderPhases = DoubleArray(TONE_COUNT)
            val totalFrames = SAMPLE_RATE * durationSeconds
            val dataBytes = totalFrames * 2 * 2

            BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
                WavWriter.writeHeader(out, SAMPLE_RATE, 2, 16, dataBytes)

                val frameBuf = ByteArray(4)
                for (i in 0 until totalFrames) {
                    val mono = buildMonoSample(renderPhases) * masterVolume
                    val l = (mono * leftVolume).coerceIn(-1.0, 1.0)
                    val r = (mono * rightVolume).coerceIn(-1.0, 1.0)
                    val lShort = (l * Short.MAX_VALUE).toInt().toShort()
                    val rShort = (r * Short.MAX_VALUE).toInt().toShort()

                    frameBuf[0] = (lShort.toInt() and 0xff).toByte()
                    frameBuf[1] = ((lShort.toInt() shr 8) and 0xff).toByte()
                    frameBuf[2] = (rShort.toInt() and 0xff).toByte()
                    frameBuf[3] = ((rShort.toInt() shr 8) and 0xff).toByte()
                    out.write(frameBuf)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
