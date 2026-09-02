package com.riding.companion.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.riding.companion.data.AppConfig
import com.riding.companion.music.MusicController
import java.util.Locale

/**
 * 语音控制器：STT（系统语音识别）+ TTS（系统语音合成）+ 音频闪避（TTS 说话时压低音乐）。
 */
object VoiceController {

    private var app: Context? = null
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false

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
        MusicController.setDuck(false)
        onSpeakingChange?.invoke(false)
    }

    fun shutdown() {
        try { recognizer?.destroy() } catch (_: Exception) {}
        try { tts?.shutdown() } catch (_: Exception) {}
        recognizer = null
        tts = null
        ttsReady = false
    }
}
