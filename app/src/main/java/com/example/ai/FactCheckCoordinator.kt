package com.example.ai

import android.content.Context
import com.example.engine.FactCheckEngine
import com.example.model.FactCheckRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Điều phối kiểm chứng: gọi DeepSeek khi đã có API key, tự động quay về
 * bộ máy cục bộ (knowledge base + heuristic) khi chưa cấu hình hoặc khi mạng/API lỗi.
 */
class FactCheckCoordinator private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val localEngine = FactCheckEngine()
    private val client = DeepSeekFactCheckClient()
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
            return@withContext localFallback(
                claim = claim,
                sourceApp = sourceApp,
                rawScreenText = rawScreenText,
                note = "Chưa cấu hình API key DeepSeek nên kết quả dưới đây chỉ đến từ bộ dữ liệu mẫu có sẵn của ứng dụng."
            )
        }

        try {
            client.analyze(
                apiKey = apiKey,
                model = model,
                claim = claim,
                sourceApp = sourceApp,
                rawScreenText = rawScreenText,
                imageBase64 = imageBytes?.let { DeepSeekFactCheckClient.encodeImage(it) },
                imageMime = imageMime
            )
        } catch (e: Exception) {
            localFallback(
                claim = claim,
                sourceApp = sourceApp,
                rawScreenText = rawScreenText,
                note = "Không gọi được DeepSeek (${e.message ?: "lỗi không xác định"}). Kết quả dưới đây là ước lượng cục bộ, cần kiểm chứng lại."
            )
        }
    }

    suspend fun chat(question: String, screenContext: String): Result<String> {
        val apiKey = runCatching { settings.apiKey() }.getOrDefault("")
        if (apiKey.isBlank()) {
            return Result.failure(DeepSeekException("Chưa cấu hình API key DeepSeek trong Cài đặt."))
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

    private fun localFallback(
        claim: String,
        sourceApp: String,
        rawScreenText: String,
        note: String
    ): FactCheckRecord {
        val local = localEngine.verifyClaim(claim, sourceApp, rawScreenText)
        return local.copy(
            limitations = "${local.limitations}\n\n$note",
            sourceApp = sourceApp
        )
    }

    companion object {
        @Volatile
        private var instance: FactCheckCoordinator? = null

        fun getInstance(context: Context): FactCheckCoordinator =
            instance ?: synchronized(this) {
                instance ?: FactCheckCoordinator(context).also { instance = it }
            }
    }
}
