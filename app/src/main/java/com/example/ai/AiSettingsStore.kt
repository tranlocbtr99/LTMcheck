package com.example.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aiSettingsDataStore by preferencesDataStore(name = "ltm_ai_settings")

/**
 * Lưu cấu hình AI ngay trên thiết bị (thư mục riêng của ứng dụng, không nằm trong APK).
 * API key do người dùng nhập trong màn hình Cài đặt, không bị nhúng vào bản build.
 */
class AiSettingsStore private constructor(context: Context) {

    private val appContext = context.applicationContext

    val apiKeyFlow: Flow<String> = appContext.aiSettingsDataStore.data
        .map { it[KEY_API_KEY].orEmpty() }

    val modelFlow: Flow<String> = appContext.aiSettingsDataStore.data
        .map { it[KEY_MODEL]?.takeIf(String::isNotBlank) ?: DEFAULT_MODEL }

    val hasApiKeyFlow: Flow<Boolean> = apiKeyFlow.map { it.isNotBlank() }

    suspend fun apiKey(): String = apiKeyFlow.first()

    suspend fun model(): String = modelFlow.first()

    suspend fun save(apiKey: String, model: String) {
        appContext.aiSettingsDataStore.edit { prefs ->
            prefs[KEY_API_KEY] = apiKey.trim()
            prefs[KEY_MODEL] = model.trim().ifBlank { DEFAULT_MODEL }
        }
    }

    suspend fun saveApiKey(apiKey: String) {
        appContext.aiSettingsDataStore.edit { it[KEY_API_KEY] = apiKey.trim() }
    }

    suspend fun saveModel(model: String) {
        appContext.aiSettingsDataStore.edit {
            it[KEY_MODEL] = model.trim().ifBlank { DEFAULT_MODEL }
        }
    }

    suspend fun clear() {
        appContext.aiSettingsDataStore.edit { it.clear() }
    }

    companion object {
        /** Model mặc định: nhanh, rẻ, hỗ trợ cả ảnh (vision) và JSON mode. */
        const val DEFAULT_MODEL = "deepseek-flash"

        /** Các model DeepSeek hiện có. `deepseek-flash` là model duy nhất đọc được ảnh. */
        val SUGGESTED_MODELS = listOf(
            "deepseek-flash",
            "deepseek-v4-pro"
        )

        private val KEY_API_KEY = stringPreferencesKey("deepseek_api_key")
        private val KEY_MODEL = stringPreferencesKey("deepseek_model")

        @Volatile
        private var instance: AiSettingsStore? = null

        fun getInstance(context: Context): AiSettingsStore =
            instance ?: synchronized(this) {
                instance ?: AiSettingsStore(context).also { instance = it }
            }
    }
}
