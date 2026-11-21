package com.wordcool.app

import android.content.Context
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
        val samples = audio.samples
        val rate = audio.sampleRate
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
