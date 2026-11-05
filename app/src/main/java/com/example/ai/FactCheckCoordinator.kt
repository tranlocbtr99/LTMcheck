package com.example.ai

import android.content.Context
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Điều phối kiểm chứng: chỉ dùng Gemini. Không dùng knowledge base cục bộ.
 * Nếu thiếu key hoặc lỗi mạng/API, trả về bản ghi nêu rõ nguyên nhân để người dùng xử lý.
 */
class FactCheckCoordinator private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val client = GeminiFactCheckClient()
    private val settings = AiSettingsStore.getInstance(appContext)

    val hasApiKey: Flow<Boolean> = settings.hasApiKeyFlow
    val apiKeyFlow: Flow<String> = settings.apiKeyFlow
    val modelFlow: Flow<String> = settings.modelFlow

    suspend fun verify(
        claim: String,
        sourceApp: String = "Nhập thủ công",
        rawScreenText: String = "",
        imageBytes: ByteArray? = null,
        imageMime: String = "image/jpeg"
    ): FactCheckRecord = withContext(Dispatchers.IO) {
        val apiKey = runCatching { settings.apiKey() }.getOrDefault("")
        val model = runCatching { settings.model() }.getOrDefault(AiSettingsStore.DEFAULT_MODEL)

        if (apiKey.isBlank()) {
            return@withContext errorRecord(
                claim = claim,
                sourceApp = sourceApp,
                rawScreenText = rawScreenText,
                message = "Chưa cấu hình API key Gemini. Vào tab Cài đặt → CẤU HÌNH AI GEMINI để nhập key (AIza…)."
            )
        }

        return@withContext try {
            client.analyze(
                apiKey = apiKey,
                model = model,
                claim = claim,
                sourceApp = sourceApp,
                rawScreenText = rawScreenText,
                imageBase64 = imageBytes?.let { GeminiFactCheckClient.encodeImage(it) },
                imageMime = imageMime
            )
        } catch (e: Exception) {
            errorRecord(
                claim = claim,
                sourceApp = sourceApp,
                rawScreenText = rawScreenText,
                message = "Không gọi được Gemini: ${e.message ?: "lỗi không xác định"}. Kiểm tra mạng và API key trong Cài đặt."
            )
        }
    }

    suspend fun chat(question: String, screenContext: String): Result<String> {
        val apiKey = runCatching { settings.apiKey() }.getOrDefault("")
        if (apiKey.isBlank()) {
            return Result.failure(GeminiException("Chưa cấu hình API key Gemini trong Cài đặt."))
        }
        val model = runCatching { settings.model() }.getOrDefault(AiSettingsStore.DEFAULT_MODEL)
        return runCatching { client.chat(apiKey, model, question, screenContext) }
    }

    suspend fun testConnection(apiKey: String, model: String): Result<Int> =
        runCatching {
            val key = apiKey.ifBlank { settings.apiKey() }
            require(key.isNotBlank()) { "Bạn chưa nhập API key." }
            client.validateKey(key)
        }

    suspend fun saveConfig(apiKey: String, model: String) = settings.save(apiKey, model)

    suspend fun clearApiKey() = settings.saveApiKey("")

    private fun errorRecord(
        claim: String,
        sourceApp: String,
        rawScreenText: String,
        message: String
    ): FactCheckRecord = FactCheckRecord(
        id = UUID.randomUUID().toString(),
        originalClaim = claim,
        normalizedClaim = claim,
        verdict = FactCheckVerdict.CHUA_DU_BANG_CHUNG,
        shortExplanation = message,
        detailedExplanation = message,
        evidence = "",
        counterEvidence = "",
        references = emptyList(),
        checkedAt = System.currentTimeMillis(),
        limitations = message,
        confidence = 0f,
        sourceApp = sourceApp,
        rawScreenText = rawScreenText,
        isAlerted = false
    )

    companion object {
        @Volatile
        private var instance: FactCheckCoordinator? = null

        fun getInstance(context: Context): FactCheckCoordinator =
            instance ?: synchronized(this) {
                instance ?: FactCheckCoordinator(context).also { instance = it }
            }
    }
}
