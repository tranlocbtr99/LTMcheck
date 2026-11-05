package com.example.engine

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Xử lý OCR cục bộ từ Bitmap màn hình bằng Google ML Kit
 */
class OcrProcessor {

    private val tag = "OcrProcessor"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    init {
        // Khởi động trước (pre-warm) mô hình OCR để giảm độ trễ cho khung hình đầu tiên
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val dummyBitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                val dummyImage = InputImage.fromBitmap(dummyBitmap, 0)
                recognizer.process(dummyImage)
                    .addOnCompleteListener {
                        dummyBitmap.recycle()
                        Log.d(tag, "ML Kit OCR pre-warmed successfully")
                    }
            } catch (e: Exception) {
                Log.w(tag, "ML Kit pre-warm skipped: ${e.message}")
            }
        }
    }

    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        if (bitmap.isRecycled) {
            if (continuation.isActive) continuation.resume("")
            return@suspendCancellableCoroutine
        }

        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text.trim()
                    Log.d(tag, "OCR success: ${fullText.length} chars recognized")
                    if (continuation.isActive) {
                        continuation.resume(fullText)
                    }
                }
                .addOnFailureListener { error ->
                    Log.e(tag, "OCR failure: ${error.message}", error)
                    if (continuation.isActive) {
                        continuation.resume("")
                    }
                }
        } catch (e: Exception) {
            Log.e(tag, "OCR exception: ${e.message}", e)
            if (continuation.isActive) {
                continuation.resume("")
            }
        }
    }

    fun close() {
        try {
            recognizer.close()
        } catch (_: Exception) { }
    }
}

