package com.masker.app.audiogram

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** گوش مورد آزمایش (یا هر دو گوش، برای تن آزمایشیِ تنظیم ولوم) */
enum class Ear {
    RIGHT, LEFT, BOTH
}

/**
 * پخش یک‌بارِ یک تن خالص (Pure Tone) کوتاه، فقط در یک گوش، برای استفاده در آزمون شنوایی.
 *
 * از AudioTrack با حالت STREAM (نه STATIC) و روی یک ترد پس‌زمینه استفاده می‌کند؛ همان الگویی
 * که در NoiseEngine/TonalEngine برای پخش زنده امتحان و تأیید شده کار می‌کند. حالت STATIC در
 * برخی دستگاه‌ها به دلیل گرد شدن اندازه بافر داخلی، write() را بی‌سروصدا با شکست مواجه می‌کرد
 * و باعث سکوت کامل می‌شد.
 */
object HearingTestTonePlayer {

    private const val TAG = "HearingTestTonePlayer"
    private const val SAMPLE_RATE = 44100
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * @param attenuationDb میزان کاهش نسبت به حداکثر خروجی دستگاه (۰ = بلندترین حالت،
     *   عدد بزرگ‌تر یعنی صدای آرام‌تر). این یک مقیاس نسبیِ غیرکالیبره است، نه dB HL بالینی.
     * @param onComplete روی ترد اصلی (UI) فراخوانی می‌شود.
     */
    fun playTone(
        context: Context,
        ear: Ear,
        frequencyHz: Double,
        attenuationDb: Float,
        durationMs: Int = 1200,
        onComplete: () -> Unit
    ) {
        thread(name = "MaskerTonePlayerThread") {
            playBlocking(context, ear, frequencyHz, attenuationDb, durationMs)
            mainHandler.post { onComplete() }
        }
    }

    private fun playBlocking(context: Context, ear: Ear, frequencyHz: Double, attenuationDb: Float, durationMs: Int) {
        val amplitude = dbToAmplitude(attenuationDb)
        val totalFrames = SAMPLE_RATE * durationMs / 1000
        val fadeFrames = (SAMPLE_RATE * 0.02).toInt().coerceAtLeast(1) // ۲۰ میلی‌ثانیه فید، برای جلوگیری از کلیک صدا
        val buffer = ShortArray(totalFrames * 2)

        var idx = 0
        for (i in 0 until totalFrames) {
            val angle = 2.0 * PI * frequencyHz * i / SAMPLE_RATE
            var sample = sin(angle) * amplitude
            if (i < fadeFrames) sample *= i.toDouble() / fadeFrames
            if (i > totalFrames - fadeFrames) sample *= (totalFrames - i).toDouble() / fadeFrames
            val shortVal = (sample * Short.MAX_VALUE).toInt().toShort()

            when (ear) {
                Ear.RIGHT -> { buffer[idx++] = 0; buffer[idx++] = shortVal }
                Ear.LEFT -> { buffer[idx++] = shortVal; buffer[idx++] = 0 }
                Ear.BOTH -> { buffer[idx++] = shortVal; buffer[idx++] = shortVal }
            }
        }

        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSizeBytes = max(minBuf, 4096)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }

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
                .setBufferSizeInBytes(bufferSizeBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack creation failed", e)
            return
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack failed to initialize (state=${track.state})")
            track.release()
            return
        }

        track.play()
        val written = track.write(buffer, 0, buffer.size)
        if (written < 0) {
            Log.e(TAG, "AudioTrack.write failed with error code $written")
        }

        // اجازه می‌دهیم باقیمانده بافر داخلی هم کامل پخش شود
        try { Thread.sleep(80) } catch (_: InterruptedException) { }

        try { track.stop() } catch (_: Exception) { }
        track.release()
    }

    private fun dbToAmplitude(attenuationDb: Float): Double {
        // attenuationDb=۰ یعنی دامنه کامل (۱٫۰)؛ هر ۲۰ واحد افزایش یعنی دامنه یک‌دهم می‌شود
        return 10.0.pow((-attenuationDb / 20.0).toDouble()).coerceIn(0.0, 1.0)
    }
}
