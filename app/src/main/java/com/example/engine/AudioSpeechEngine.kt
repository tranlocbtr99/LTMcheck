package com.example.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Bộ chuyển đổi âm thanh video trực tiếp thành văn bản (Speech-to-Text)
 * Hỗ trợ nhận diện tiếng nói liên tục từ video TikTok, YouTube, Facebook khi phát qua loa hoặc micro
 */
class AudioSpeechEngine(
    private val context: Context,
    private val onSpeechRecognized: (text: String, isFinal: Boolean) -> Unit,
    private val onRmsChanged: (rmsDb: Float) -> Unit = {},
    private val onStatusChanged: (status: String) -> Unit = {},
    private val onErrorOccurred: (errorMsg: String) -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tag = "AudioSpeechEngine"

    private var currentAccumulatedText = StringBuilder()
    private var restartAttempts = 0

    fun isListening(): Boolean = isListening

    fun startListening() {
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    val msg = "Dịch vụ nhận dạng tiếng nói (Google Speech Services) chưa được cài đặt trên thiết bị."
                    Log.w(tag, msg)
                    onStatusChanged("Chưa có dịch vụ SpeechRecognizer trên thiết bị. Bạn vẫn có thể sử dụng đọc chữ/phụ đề màn hình!")
                    onErrorOccurred(msg)
                    return@post
                }

                stopListeningInternal()
                isListening = true
                restartAttempts = 0
                createAndStartRecognizer()
            } catch (e: Exception) {
                Log.e(tag, "startListening exception: ${e.message}", e)
                onStatusChanged("Không thể khởi động bộ thu âm video: ${e.message}")
                onErrorOccurred("Không thể khởi động bộ thu âm video: ${e.message}")
            }
        }
    }

    private fun createAndStartRecognizer() {
        if (!isListening) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(tag, "Sẵn sàng thu âm thanh video")
                        onStatusChanged("🎙️ Đang lắng nghe âm thanh từ video...")
                        restartAttempts = 0
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(tag, "Bắt đầu phát hiện giọng nói trong video")
                        onStatusChanged("🔊 Đã phát hiện giọng nói/lời thoại trong video...")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Cung cấp chỉ số dB thời gian thực để UI vẽ biểu đồ sóng âm thanh
                        onRmsChanged(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(tag, "Kết thúc đoạn nói, đang chuyển đổi...")
                        onStatusChanged("⚡ Đang trích xuất từ ngữ từ âm thanh...")
                    }

                    override fun onError(error: Int) {
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Lỗi thu tín hiệu âm thanh"
                            SpeechRecognizer.ERROR_CLIENT -> "Client nhận diện đang khởi tạo lại"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Chưa cấp quyền RECORD_AUDIO"
                            SpeechRecognizer.ERROR_NETWORK -> "Đang đợi kết nối mạng nhận diện"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Hết thời gian mạng, đang thử lại"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Đang theo dõi đoạn video tiếp theo..."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Đang đồng bộ hóa bộ nhận dạng"
                            SpeechRecognizer.ERROR_SERVER -> "Máy chủ nhận diện bận, chuyển chế độ offline"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Đang lắng nghe âm thanh từ video..."
                            else -> "Đang theo dõi âm thanh video ($error)"
                        }
                        Log.d(tag, "SpeechRecognizer status: $errorMsg (code $error)")
                        onStatusChanged(errorMsg)

                        if (isListening) {
                            scheduleRestart(if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 300L else 1200L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].trim()
                            if (text.isNotBlank()) {
                                if (currentAccumulatedText.isNotEmpty()) {
                                    currentAccumulatedText.append(". ")
                                }
                                currentAccumulatedText.append(text)
                                onStatusChanged("✓ Đã nhận diện lời nói: \"$text\"")
                                onSpeechRecognized(text, true)
                            }
                        }

                        if (isListening) {
                            scheduleRestart(350L)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val partial = matches[0].trim()
                            if (partial.isNotBlank()) {
                                onStatusChanged("▶ Đang nói: \"$partial\"")
                                onSpeechRecognized(partial, false)
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
            Log.i(tag, "Bắt đầu lắng nghe âm thanh video liên tục...")
        } catch (e: Exception) {
            Log.e(tag, "createAndStartRecognizer error: ${e.message}", e)
            if (isListening) {
                scheduleRestart(1500L)
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!isListening) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (isListening) {
                createAndStartRecognizer()
            }
        }, delayMs)
    }

    fun stopListening() {
        isListening = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            stopListeningInternal()
            onStatusChanged("Đã tắt nhận diện âm thanh video")
            onRmsChanged(-20f)
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
    }

    fun clearBuffer() {
        currentAccumulatedText.clear()
    }

    /**
     * Giả lập một đoạn âm thanh video phục vụ mục đích kiểm thử trực tiếp trên máy
     */
    fun simulateSpeechInput(testPhrase: String) {
        mainHandler.post {
            onStatusChanged("▶ [Mô phỏng] Nhận diện từ video: \"$testPhrase\"")
            onRmsChanged(12f)
            onSpeechRecognized(testPhrase, true)
        }
    }
}

