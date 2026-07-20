package com.masker.app.audiogram

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** گوش مورد آزمایش (یا هر دو گوش، برای تن آزمایشیِ تنظیم ولوم) */
enum class Ear {
    RIGHT, LEFT, BOTH
}

/**
 * پخش یک‌بارِ یک تن خالص (Pure Tone) کوتاه، فقط در یک گوش، برای استفاده در آزمون شنوایی.
 * از AudioTrack با حالت STATIC استفاده می‌کند تا زمان‌بندی پخش دقیق و قابل پیش‌بینی باشد.
 */
object HearingTestTonePlayer {

    private const val SAMPLE_RATE = 44100

    /**
     * @param attenuationDb میزان کاهش نسبت به حداکثر خروجی دستگاه (۰ = بلندترین حالت،
     *   عدد بزرگ‌تر یعنی صدای آرام‌تر). این یک مقیاس نسبیِ غیرکالیبره است، نه dB HL بالینی.
     */
    fun playTone(
        context: Context,
        ear: Ear,
        frequencyHz: Double,
        attenuationDb: Float,
        durationMs: Int = 1200,
        onComplete: () -> Unit
    ) {
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
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            onComplete()
            return
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            onComplete()
            return
        }

        track.write(buffer, 0, buffer.size)
        track.play()

        Handler(Looper.getMainLooper()).postDelayed({
            try { track.stop() } catch (_: Exception) { }
            track.release()
            onComplete()
        }, durationMs.toLong() + 50)
    }

    private fun dbToAmplitude(attenuationDb: Float): Double {
        // attenuationDb=۰ یعنی دامنه کامل (۱٫۰)؛ هر ۲۰ واحد افزایش یعنی دامنه یک‌دهم می‌شود
        return 10.0.pow((-attenuationDb / 20.0).toDouble()).coerceIn(0.0, 1.0)
    }
}
