package com.riding.companion.audio

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.riding.companion.data.AppConfig
import com.riding.companion.music.MusicController
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 语音控制器：STT（系统语音识别）+ TTS（SiliconFlow 高质量TTS / 系统TTS降级）+ 音频闪避。
 */
object VoiceController {

    private var app: Context? = null
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false
    private var apiPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    var onResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onListeningChange: ((Boolean) -> Unit)? = null
    var onSpeakingChange: ((Boolean) -> Unit)? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
        if (tts == null) {
            tts = TextToSpeech(app) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINESE
                    tts?.setSpeechRate(AppConfig.ttsRate)
                    ttsReady = true
                }
            }
        }
    }

    fun applyTtsRate() {
        if (ttsReady) {
            tts?.setSpeechRate(AppConfig.ttsRate)
        }
    }

    @Synchronized
    fun isListening(): Boolean = listening

    @Synchronized
    fun startListening() {
        val ctx = app ?: return
        if (listening) return
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
                recognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        listening = false
                        onListeningChange?.invoke(false)
                    }

                    override fun onError(error: Int) {
                        listening = false
                        onListeningChange?.invoke(false)
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "没有听清，请再说一次"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到声音"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别繁忙，请稍后再试"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
                            else -> "识别出错（$error）"
                        }
                        onError?.invoke(msg)
                    }

                    override fun onResults(results: Bundle?) {
                        listening = false
                        onListeningChange?.invoke(false)
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (text.isNullOrBlank()) {
                            onError?.invoke("没有听清，请再说一次")
                        } else {
                            onResult?.invoke(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            listening = true
            onListeningChange?.invoke(true)
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            listening = false
            onError?.invoke("语音识别不可用：${e.message}")
        }
    }

    @Synchronized
    fun stopListening() {
        try { recognizer?.stopListening() } catch (_: Exception) {}
        listening = false
        onListeningChange?.invoke(false)
    }

    fun cancelListening() {
        try { recognizer?.cancel() } catch (_: Exception) {}
        listening = false
        onListeningChange?.invoke(false)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        // 优先 SiliconFlow 高质量 TTS（需要已配置 key + baseUrl）
        if (AppConfig.ttsMode == 0 &&
            AppConfig.llmApiKey.isNotBlank() &&
            AppConfig.llmBaseUrl.isNotBlank()
        ) {
            speakViaApi(text, onDone)
            return
        }
        speakSystem(text, onDone)
    }

    /** SiliconFlow /audio/speech 高质量 TTS，失败自动降级系统 TTS */
    private fun speakViaApi(text: String, onDone: (() -> Unit)? = null) {
        val ctx = app ?: run { onDone?.invoke(); return }
        val base = AppConfig.llmBaseUrl.trim().trimEnd('/')
        val key = AppConfig.llmApiKey.trim()
        Thread {
            try {
                val url = URL("$base/audio/speech")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $key")
                val body = JSONObject()
                body.put("model", AppConfig.ttsModel)
                body.put("input", text)
                body.put("voice", AppConfig.ttsVoice)
                body.put("response_format", "mp3")
                body.put("speed", AppConfig.ttsRate.toDouble())
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    Log.w("TTS", "API TTS 失败($code): $err")
                    handler.post { speakSystem(text, onDone) }
                    return@Thread
                }
                val bytes = conn.inputStream.use { it.readBytes() }
                val f = File(ctx.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                f.writeBytes(bytes)
                handler.post { playMp3(f, onDone) }
            } catch (e: Exception) {
                Log.w("TTS", "API TTS 异常: ${e.message}")
                handler.post { speakSystem(text, onDone) }
            }
        }.start()
    }

    private fun playMp3(f: File, onDone: (() -> Unit)?) {
        stopApiPlayer()
        val p = MediaPlayer()
        apiPlayer = p
        try {
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener {
                apiPlayer = null
                finishSpeak(); onDone?.invoke(); f.delete()
            }
            p.setOnErrorListener { _, _, _ ->
                apiPlayer = null
                finishSpeak(); onDone?.invoke(); f.delete(); true
            }
            p.prepare()
            p.start()
            onSpeakingChange?.invoke(true)
        } catch (e: Exception) {
            apiPlayer = null
            finishSpeak(); onDone?.invoke(); f.delete()
        }
    }

    private fun stopApiPlayer() {
        try { apiPlayer?.stop() } catch (_: Exception) {}
        try { apiPlayer?.release() } catch (_: Exception) {}
        apiPlayer = null
    }

    private fun finishSpeak() {
        MusicController.setDuck(false)
        onSpeakingChange?.invoke(false)
    }

    /** 系统 TTS（兜底） */
    private fun speakSystem(text: String, onDone: (() -> Unit)? = null) {
        if (!ttsReady || tts == null) {
            onDone?.invoke()
            return
        }
        onSpeakingChange?.invoke(true)
        MusicController.setDuck(true)
        val utteranceId = "utt_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                MusicController.setDuck(false)
                onSpeakingChange?.invoke(false)
                onDone?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                MusicController.setDuck(false)
                onSpeakingChange?.invoke(false)
                onDone?.invoke()
            }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        try { tts?.stop() } catch (_: Exception) {}
        stopApiPlayer()
        MusicController.setDuck(false)
        onSpeakingChange?.invoke(false)
    }

    fun shutdown() {
        try { recognizer?.destroy() } catch (_: Exception) {}
        try { tts?.shutdown() } catch (_: Exception) {}
        stopApiPlayer()
        recognizer = null
        tts = null
        ttsReady = false
    }
}
