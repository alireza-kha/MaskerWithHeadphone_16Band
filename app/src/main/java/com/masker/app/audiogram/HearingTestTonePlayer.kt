package com.masker.app.audiogram

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.masker.app.audio.BiquadBandPass
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
 * پخش پیوسته (تا زمان توقف صریح) یک تن خالص در یک گوش، برای استفاده در آزمون شنوایی.
 * برخلاف نسخه قبلی که یک بار کوتاه پخش می‌شد، اینجا صدا مرتب و بدون قطعی ادامه پیدا می‌کند
 * تا کاربر با فرصت کافی یکی از دو دکمه «می‌شنوم» / «نمی‌شنوم» را بزند.
 *
 * همچنین امکان پخش هم‌زمان نویز باریک‌باند ماسک‌کننده در گوش مقابل را دارد — دقیقاً همان
 * روش استاندارد ماسکینگ در اودیومتری بالینی برای بررسی «اثر سایه» (Cross-hearing)، که در
 * آن گوش غیرآزمایشی با نویز اشغال می‌شود تا در نتیجه آزمون گوش مقابل دخالت نکند.
 */
object HearingTestTonePlayer {

    private const val TAG = "HearingTestTonePlayer"
    private const val SAMPLE_RATE = 44100

    @Volatile
    private var isPlaying = false
    private var playbackThread: Thread? = null

    /**
     * شروع پخش پیوسته. اگر پخش قبلی در جریان باشد، ابتدا متوقف می‌شود.
     * @param testEar گوشی که تن خالص در آن پخش می‌شود
     * @param attenuationDb میزان کاهش نسبت به حداکثر خروجی دستگاه (۰ = بلندترین حالت)
     * @param maskingEnabled اگر true باشد، هم‌زمان نویز باریک‌باند ماسک‌کننده در گوش مقابل پخش می‌شود
     * @param maskingAttenuationDb سطح نویز ماسک‌کننده (۰ = بلندترین حالت)
     */
    fun start(
        context: Context,
        testEar: Ear,
        frequencyHz: Double,
        attenuationDb: Float,
        maskingEnabled: Boolean = false,
        maskingAttenuationDb: Float = 20f
    ) {
        stop()
        isPlaying = true
        playbackThread = thread(name = "MaskerTestTonePlayerThread") {
            playContinuous(context, testEar, frequencyHz, attenuationDb, maskingEnabled, maskingAttenuationDb)
        }
    }

    /** توقف پخش پیوسته فعلی (اگر در جریان باشد) */
    fun stop() {
        isPlaying = false
        playbackThread?.join(300)
        playbackThread = null
    }

    /** پخش یک‌بارِ کوتاه، فقط برای تن آزمایشیِ تنظیم ولوم (نه برای آزمون واقعی) */
    fun playBrief(context: Context, ear: Ear, frequencyHz: Double, attenuationDb: Float, durationMs: Int, onComplete: () -> Unit) {
        thread(name = "MaskerBriefTonePlayerThread") {
            val stopFlag = booleanArrayOf(true)
            playContinuous(context, ear, frequencyHz, attenuationDb, false, 20f, durationMs, stopFlag)
            onComplete()
        }
    }

    private fun playContinuous(
        context: Context,
        testEar: Ear,
        frequencyHz: Double,
        attenuationDb: Float,
        maskingEnabled: Boolean,
        maskingAttenuationDb: Float,
        fixedDurationMs: Int? = null,
        briefModeFlag: BooleanArray? = null
    ) {
        val toneAmplitude = dbToAmplitude(attenuationDb)
        val maskAmplitude = dbToAmplitude(maskingAttenuationDb)

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

        var tonePhase = 0.0
        val phaseIncrement = 2.0 * PI * frequencyHz / SAMPLE_RATE
        val maskFilter = if (maskingEnabled) BiquadBandPass(SAMPLE_RATE, frequencyHz, 1.4) else null
        val random = java.util.Random()

        val fadeFrames = (SAMPLE_RATE * 0.02).toInt().coerceAtLeast(1)
        var frameCounter = 0
        val totalFramesForBrief = fixedDurationMs?.let { SAMPLE_RATE * it / 1000 }

        val chunkFrames = 512
        val chunk = ShortArray(chunkFrames * 2)

        val running: () -> Boolean = { if (briefModeFlag != null) frameCounter < (totalFramesForBrief ?: 0) else isPlaying }

        while (running()) {
            var idx = 0
            for (i in 0 until chunkFrames) {
                if (!running()) break

                var toneSample = sin(tonePhase) * toneAmplitude
                tonePhase += phaseIncrement
                if (tonePhase > 2.0 * PI) tonePhase -= 2.0 * PI

                // فید ورود/خروج نرم برای پخش کوتاه تن آزمایشی (جلوگیری از کلیک صدا)
                if (totalFramesForBrief != null) {
                    if (frameCounter < fadeFrames) toneSample *= frameCounter.toDouble() / fadeFrames
                    val remaining = totalFramesForBrief - frameCounter
                    if (remaining in 0 until fadeFrames) toneSample *= remaining.toDouble() / fadeFrames
                }

                var maskSample = 0.0
                if (maskFilter != null) {
                    val white = random.nextDouble() * 2.0 - 1.0
                    maskSample = maskFilter.process(white) * maskAmplitude
                }

                val leftVal: Double
                val rightVal: Double
                when (testEar) {
                    Ear.RIGHT -> { leftVal = maskSample; rightVal = toneSample }
                    Ear.LEFT -> { leftVal = toneSample; rightVal = maskSample }
                    Ear.BOTH -> { leftVal = toneSample; rightVal = toneSample }
                }

                chunk[idx++] = (leftVal.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                chunk[idx++] = (rightVal.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                frameCounter++
            }
            if (idx > 0) track.write(chunk, 0, idx)
        }

        try { track.stop() } catch (_: Exception) { }
        track.release()
    }

    private fun dbToAmplitude(attenuationDb: Float): Double {
        // attenuationDb=۰ یعنی دامنه کامل (۱٫۰)؛ هر ۲۰ واحد افزایش یعنی دامنه یک‌دهم می‌شود
        return 10.0.pow((-attenuationDb / 20.0).toDouble()).coerceIn(0.0, 1.0)
    }
}
