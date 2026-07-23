package com.masker.app.hearingaid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import com.masker.app.audio.BiquadPeakingEq
import kotlin.concurrent.thread
import kotlin.math.max

/**
 * موتور شبیه‌سازی سمعک پیشرفته: صدای میکروفون گوشی را به‌صورت بلادرنگ دریافت، برای هر گوش
 * جداگانه با اکولایزر گرافیکی هفت‌باندی (که می‌تواند از نتیجه اودیوگرام فراخوانی شود) و
 * بهره کلی تقویت می‌کند و هم‌زمان با ماسکر (نویزی/تونال) از طریق هدفون پخش می‌کند.
 *
 * نکته: چون میکروفون و خروجی هم‌زمان فعال هستند، برای جلوگیری از زوزه بازخورد (feedback)
 * استفاده از هدفون ضروری است؛ در صورت وجود، حذف‌کننده اکو/نویز سیستم‌عامل هم فعال می‌شود.
 */
class HearingAidEngine {

    companion object {
        private const val TAG = "HearingAidEngine"
        private const val SAMPLE_RATE = 44100

        const val EQ_BAND_COUNT = 7
        val EQ_BAND_FREQUENCIES = doubleArrayOf(60.0, 150.0, 400.0, 1000.0, 2400.0, 6000.0, 15000.0)

        const val MIN_MASTER_GAIN = 0.5f
        const val MAX_MASTER_GAIN = 4.0f
    }

    val leftEqBandGainsDb = FloatArray(EQ_BAND_COUNT) { 0f }
    val rightEqBandGainsDb = FloatArray(EQ_BAND_COUNT) { 0f }

    @Volatile
    var leftVolume: Float = 1.0f
    @Volatile
    var rightVolume: Float = 1.0f

    /** بهره کلی تقویت ورودی میکروفون؛ شبیه چرخ‌دنده «صدای سمعک» */
    @Volatile
    var masterGain: Float = 1.5f
        set(value) {
            field = value.coerceIn(MIN_MASTER_GAIN, MAX_MASTER_GAIN)
        }

    @Volatile
    var isRunning = false
        private set

    private var captureThread: Thread? = null
    private var leftEqFilters: Array<BiquadPeakingEq>? = null
    private var rightEqFilters: Array<BiquadPeakingEq>? = null

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun setLeftEqBandGain(band: Int, gainDb: Float) {
        if (band !in leftEqBandGainsDb.indices) return
        leftEqBandGainsDb[band] = gainDb
        leftEqFilters?.getOrNull(band)?.setGain(gainDb)
    }

    fun setRightEqBandGain(band: Int, gainDb: Float) {
        if (band !in rightEqBandGainsDb.indices) return
        rightEqBandGainsDb[band] = gainDb
        rightEqFilters?.getOrNull(band)?.setGain(gainDb)
    }

    fun start(context: Context) {
        if (isRunning || !hasPermission(context)) return
        isRunning = true
        captureThread = thread(name = "HearingAidThread") {
            runCaptureLoop()
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        captureThread?.join(500)
        captureThread = null
    }

    private fun runCaptureLoop() {
        val minRecBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minRecBuf <= 0) {
            Log.w(TAG, "invalid min record buffer size")
            isRunning = false
            return
        }
        val recBufSize = max(minRecBuf, 2048) * 2

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recBufSize
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "record audio permission denied", e)
            isRunning = false
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord init failed")
            record.release()
            isRunning = false
            return
        }

        val echoCanceler = if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true }
        } else null
        val noiseSuppressor = if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true }
        } else null

        val minPlayBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        val playBufSize = max(minPlayBuf, 4096) * 2

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(playBufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        leftEqFilters = Array(EQ_BAND_COUNT) { i ->
            BiquadPeakingEq(SAMPLE_RATE, EQ_BAND_FREQUENCIES[i], leftEqBandGainsDb[i])
        }
        rightEqFilters = Array(EQ_BAND_COUNT) { i ->
            BiquadPeakingEq(SAMPLE_RATE, EQ_BAND_FREQUENCIES[i], rightEqBandGainsDb[i])
        }

        val chunkSamples = recBufSize / 2
        val inputBuf = ShortArray(chunkSamples)
        val outputBuf = ShortArray(chunkSamples * 2)

        try {
            record.startRecording()
            track.play()

            while (isRunning) {
                val read = record.read(inputBuf, 0, inputBuf.size)
                if (read <= 0) continue

                val leftFilters = leftEqFilters ?: continue
                val rightFilters = rightEqFilters ?: continue

                for (i in 0 until read) {
                    val sample = inputBuf[i] / 32768.0

                    var left = sample
                    for (f in leftFilters) left = f.process(left)
                    left *= (masterGain * leftVolume).toDouble()

                    var right = sample
                    for (f in rightFilters) right = f.process(right)
                    right *= (masterGain * rightVolume).toDouble()

                    outputBuf[i * 2] = clampToShort(left)
                    outputBuf[i * 2 + 1] = clampToShort(right)
                }

                track.write(outputBuf, 0, read * 2)
            }
        } catch (e: Exception) {
            Log.w(TAG, "capture loop error", e)
        } finally {
            try {
                record.stop()
            } catch (_: Exception) {
            }
            record.release()
            echoCanceler?.release()
            noiseSuppressor?.release()
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
            leftEqFilters = null
            rightEqFilters = null
            isRunning = false
        }
    }

    private fun clampToShort(value: Double): Short {
        val scaled = (value * 32767.0).toInt()
        return scaled.coerceIn(-32768, 32767).toShort()
    }
}
