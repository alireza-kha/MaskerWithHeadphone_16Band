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
import java.util.Random
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * موتور تولید صدای ماسکر شنوایی.
 * از ۱۶ باند نویز فیلترشده (مانند ماسکرهای پیشرفته سمعک) استفاده می‌کند.
 * هر باند دارای یک slider جداگانه برای تنظیم شدت است، به علاوه:
 *  - ولوم کلی (master volume)
 *  - ولوم جداگانه گوش چپ و راست
 */
class NoiseEngine {

    companion object {
        private const val TAG = "NoiseEngine"
        const val SAMPLE_RATE = 44100
        const val BAND_COUNT = 16

        // مدولاسیون دامنه ۱۰ هرتز، بر پایه پژوهش‌های Neff et al. (2017) و مطالعه ۲۰۲۵ در
        // Hearing Research: صداهای مدوله‌شده نسبت به صدای ثابت، سرکوب مؤثرتری روی وزوز نشان دادند
        const val MODULATION_RATE_HZ = 10.0

        // فرکانس مرکزی هر یک از ۱۶ باند (هرتز) - طیفی از ۱۲۵ تا ۱۴۰۰۰ هرتز
        val BAND_FREQUENCIES = doubleArrayOf(
            125.0, 175.0, 250.0, 350.0, 500.0, 700.0, 1000.0, 1400.0,
            2000.0, 2800.0, 4000.0, 5600.0, 8000.0, 10000.0, 12000.0, 14000.0
        )
    }

    // میزان هر باند، بین ۰ (خاموش) تا ۱ (حداکثر)
    val bandGains = FloatArray(BAND_COUNT) { 0.6f }

    var masterVolume: Float = 0.7f
    var leftVolume: Float = 1.0f   // ولوم اختصاصی گوش چپ (۰..۱)
    var rightVolume: Float = 1.0f  // ولوم اختصاصی گوش راست (۰..۱)

    // ---- حذف فرکانس وزوز از نویز (Notched Sound، بر پایه پژوهش Pantev et al., 2012) ----
    var notchEnabled: Boolean = false
        private set
    var notchFrequencyHz: Double = 4000.0
        private set
    var notchWidthOctaves: Float = 0.5f
        private set

    /** فعال/غیرفعال کردن و تنظیم پارامترهای فیلتر Notch؛ بلافاصله برای پخش زنده اعمال می‌شود */
    fun setNotch(enabled: Boolean, frequencyHz: Double, widthOctaves: Float) {
        notchEnabled = enabled
        notchFrequencyHz = frequencyHz
        notchWidthOctaves = widthOctaves
        liveNotchFilter = if (enabled) NotchFilter(SAMPLE_RATE, frequencyHz, widthOctaves) else null
    }

    // ---- مدولاسیون دامنه ۱۰ هرتز (بر پایه پژوهش‌های Neff et al., 2017 و ۲۰۲۵) ----
    var modulationEnabled: Boolean = false
    var modulationDepth: Float = 0.6f // ۰ تا ۱ (چه میزان دامنه در پایین‌ترین نقطه افت کند)

    private var modPhase = 0.0
    private val modPhaseIncrement = 2.0 * PI * MODULATION_RATE_HZ / SAMPLE_RATE

    @Volatile
    var isPlaying: Boolean = false
        private set

    private var playbackThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private val liveFilters = Array(BAND_COUNT) { BiquadBandPass(SAMPLE_RATE, BAND_FREQUENCIES[it]) }
    private var liveNotchFilter: NotchFilter? = null
    private val random = Random()

    /** شروع پخش زنده صدای ماسکر از بلندگو/هدفون */
    fun start(context: Context? = null) {
        if (isPlaying) return
        isPlaying = true
        liveFilters.forEach { it.reset() }
        liveNotchFilter?.reset()
        modPhase = 0.0
        if (context != null) requestAudioFocus(context)
        playbackThread = thread(name = "MaskerPlaybackThread") { playLoop() }
    }

    /** توقف پخش زنده */
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

    private fun buildMonoSample(filters: Array<BiquadBandPass>, notch: NotchFilter?): Double {
        val white = random.nextDouble() * 2.0 - 1.0
        var sum = 0.0
        for (i in 0 until BAND_COUNT) {
            sum += filters[i].process(white) * bandGains[i]
        }
        var mono = sum / BAND_COUNT
        if (notch != null) mono = notch.process(mono)

        if (modulationEnabled) {
            // پوش سینوسی نرم بین (۱ - عمق) و ۱، با نرخ ۱۰ هرتز
            val envelope = 1.0 - modulationDepth * (0.5 - 0.5 * cos(modPhase))
            mono *= envelope
            modPhase += modPhaseIncrement
            if (modPhase > 2.0 * PI) modPhase -= 2.0 * PI
        }

        return mono
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
                        // نکته مهم: USAGE_MEDIA باید با CONTENT_TYPE_MUSIC همراه باشد تا صدا
                        // روی استریم صحیح (Media Volume) پخش شود؛ ترکیب اشتباه باعث سکوت می‌شد.
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
                val mono = buildMonoSample(liveFilters, liveNotchFilter) * masterVolume
                val l = (mono * leftVolume).coerceIn(-1.0, 1.0)
                val r = (mono * rightVolume).coerceIn(-1.0, 1.0)
                chunk[idx++] = (l * Short.MAX_VALUE).toInt().toShort()
                chunk[idx++] = (r * Short.MAX_VALUE).toInt().toShort()
            }
            track.write(chunk, 0, chunk.size)
        }
    }

    /**
     * تولید (رندر) فایل صوتی WAV از تنظیمات فعلی و ذخیره آن در مسیر مشخص شده.
     * این تابع باید در یک ترد پس‌زمینه (نه ترد اصلی UI) فراخوانی شود.
     */
    fun renderToFile(outputFile: File, durationSeconds: Int): Boolean {
        return try {
            val renderFilters = Array(BAND_COUNT) { BiquadBandPass(SAMPLE_RATE, BAND_FREQUENCIES[it]) }
            val renderNotch = if (notchEnabled) NotchFilter(SAMPLE_RATE, notchFrequencyHz, notchWidthOctaves) else null
            val totalFrames = SAMPLE_RATE * durationSeconds
            val dataBytes = totalFrames * 2 /* channels */ * 2 /* bytes per sample */

            BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
                WavWriter.writeHeader(out, SAMPLE_RATE, 2, 16, dataBytes)

                val frameBuf = ByteArray(4) // 2 bytes L + 2 bytes R
                for (i in 0 until totalFrames) {
                    val mono = buildMonoSample(renderFilters, renderNotch) * masterVolume
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
