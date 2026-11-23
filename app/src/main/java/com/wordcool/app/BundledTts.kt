package com.wordcool.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.tanh

/**
 * v1.11：随 APK 内置的离线 TTS。
 *
 * 引擎是 sherpa-onnx（Apache-2.0），模型是 Piper en_US-amy-low-int8（约 21MB），
 * 都放在 assets 里，不依赖手机系统语音包，也不需要联网。
 *
 * 只内置了美音模型：UK 按钮也会用这个声音朗读，避免「装完没声」。
 */
object BundledTts {

    private const val TAG = "WordCoolTts"
    private const val DIR = "tts/amy/"
    private const val MODEL = DIR + "en_US-amy-low.onnx"
    private const val TOKENS = DIR + "tokens.txt"
    private const val LEXICON = DIR + "lexicon-en-us.txt"

    @Volatile private var engine: OfflineTts? = null
    @Volatile private var initializing = false
    @Volatile private var failed = false
    @Volatile private var lastError = ""
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var track: AudioTrack? = null

    @Synchronized
    fun init(context: Context) {
        if (engine != null || initializing) return
        // Release 只打包 ARM 原生库；x86 模拟器/设备上直接回退系统 TTS，避免加载 .so 时崩溃
        val primaryAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable && !primaryAbi.startsWith("arm")) {
            failed = true
            lastError = "当前设备架构 $primaryAbi 不在内置语音支持范围"
            Log.w(TAG, lastError + "，将回退系统 TTS")
            return
        }
        initializing = true
        val app = context.applicationContext
        executor.execute {
            try {
                val vits = OfflineTtsVitsModelConfig(
                    model = MODEL,
                    lexicon = LEXICON,
                    tokens = TOKENS,
                    dataDir = "",
                    dictDir = "",
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
                val modelConfig = OfflineTtsModelConfig(
                    vits = vits,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
                val config = OfflineTtsConfig(
                    model = modelConfig,
                    ruleFsts = "",
                    ruleFars = "",
                    maxNumSentences = 1,
                    silenceScale = 0.2f
                )
                engine = OfflineTts(app.assets, config)
                Log.i(TAG, "ready: sampleRate=${engine?.sampleRate()}")
            } catch (t: Throwable) {
                failed = true
                lastError = t.message ?: t.javaClass.simpleName
                Log.e(TAG, "init failed", t)
            } finally {
                initializing = false
            }
        }
    }

    fun isReady(): Boolean = engine != null
    fun isInitializing(): Boolean = initializing

    fun statusText(): String = when {
        engine != null -> "内置离线语音已就绪（美音 Amy）"
        failed -> "内置语音加载失败：$lastError"
        else -> "内置语音加载中…"
    }

    /** 在后台线程生成并播放；返回 false 表示引擎还没好，调用方应回退系统 TTS。 */
    fun speak(text: String, speed: Float): Boolean {
        val e = engine ?: return false
        stopCurrent()
        executor.execute {
            try {
                val audio = e.generate(text, 0, speed)
                if (audio.samples.isNotEmpty()) play(audio)
            } catch (t: Throwable) {
                Log.e(TAG, "generate/play failed", t)
            }
        }
        return true
    }

    private fun play(audio: GeneratedAudio) {
        val raw = audio.samples
        val rate = audio.sampleRate
        if (raw.isEmpty()) return

        // 模型输出通常只有 0.1~0.3 峰值，直接播会很轻；峰值归一化 + 软限幅，最多放大 12 倍
        var peak = 0f
        var sumSq = 0.0
        for (s in raw) {
            val a = abs(s)
            if (a > peak) peak = a
            sumSq += a.toDouble() * a
        }
        val rms = if (raw.isNotEmpty()) kotlin.math.sqrt(sumSq / raw.size).toFloat() else 0f
        val peakGain = if (peak > 0.005f) 0.98f / peak else 1f
        // 目标 RMS 约 0.10；对很轻的短词再补一点响度，但不超过 12 倍
        val rmsGain = if (rms > 0.003f) 0.10f / rms else 1f
        // 1.25 倍响度补偿后再软限幅：比单纯峰值归一听起来更响，但不硬削波
        val gain = (maxOf(peakGain, rmsGain) * 1.25f).coerceIn(1f, 12f)
        val samples = FloatArray(raw.size) { i ->
            val x = raw[i] * gain
            // 软限幅：0.90 以下原样，超过的部分用 tanh 压回，避免硬削波的破音
            when {
                x > 0.90f -> 0.90f + 0.10f * tanh((x - 0.90f) / 0.10f)
                x < -0.90f -> -0.90f - 0.10f * tanh((-x - 0.90f) / 0.10f)
                else -> x
            }
        }
        var outPeak = 0f
        for (s in samples) {
            val a = abs(s)
            if (a > outPeak) outPeak = a
        }
        Log.i(TAG, "peak=$peak rms=$rms gain=$gain outPeak=$outPeak rate=$rate")

        val durationMs = (samples.size.toLong() * 1000L / rate).coerceAtLeast(50L)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track = t
        try {
            t.setVolume(1.0f)
        } catch (ignored: Exception) {
        }
        t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        t.play()
        try {
            Thread.sleep(durationMs + 150)
        } catch (ignored: InterruptedException) {
        }
        if (track === t) {
            try {
                t.stop()
            } catch (ignored: Exception) {
            }
            try {
                t.release()
            } catch (ignored: Exception) {
            }
            track = null
        }
    }

    @Synchronized
    private fun stopCurrent() {
        val t = track ?: return
        track = null
        try {
            t.pause()
            t.flush()
            t.release()
        } catch (ignored: Exception) {
        }
    }

    @Synchronized
    fun release() {
        stopCurrent()
        try {
            engine?.release()
        } catch (ignored: Exception) {
        }
        engine = null
    }
}
