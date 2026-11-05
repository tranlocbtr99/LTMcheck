package com.example.ai

import android.util.Base64
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import com.example.model.ReferenceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Lỗi gọi Gemini kèm thông điệp thân thiện để hiển thị cho người dùng. */
class GeminiException(message: String, val httpCode: Int = 0) : Exception(message)

/**
 * Client gọi Gemini API (generateContent) để kiểm chứng thông tin.
 * - Ưu tiên bật Google Search grounding để lấy nguồn đối chiếu thật.
 * - Ép mô hình trả JSON, có hàm sửa lỗi khi mô hình bọc JSON trong markdown.
 * - Không lưu key ở bất kỳ đâu trong code; key được truyền từ AiSettingsStore.
 */
class GeminiFactCheckClient(
    private val httpClient: OkHttpClient = defaultClient()
) {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun analyze(
        apiKey: String,
        model: String,
        claim: String,
        sourceApp: String = "Nhập thủ công",
        rawScreenText: String = "",
        imageBase64: String? = null,
        imageMime: String = "image/jpeg"
    ): FactCheckRecord {
        require(apiKey.isNotBlank()) { "Chưa cấu hình API key Gemini" }
        val trimmedClaim = claim.trim().take(MAX_CLAIM_CHARS)
        if (trimmedClaim.isEmpty() && imageBase64 == null) {
            throw GeminiException("Nội dung cần kiểm chứng đang trống.")
        }

        // Gửi thẳng nội dung đọc từ màn hình cho Gemini, không dùng search/grounding.
        val requestBody = buildRequestJson(trimmedClaim, imageBase64, imageMime, jsonMime = true)
        val (text, groundingSources) = callGenerate(apiKey, model, requestBody, useTools = false)

        val parsed = parseModelJson(text)
        return toRecord(
            json = parsed,
            claim = trimmedClaim.ifBlank { "Hình ảnh được gửi để kiểm chứng" },
            sourceApp = sourceApp,
            rawScreenText = rawScreenText.ifBlank { trimmedClaim },
            groundingSources = groundingSources
        )
    }

    /** Kiểm tra key có hợp lệ không (gọi endpoint nhẹ, không tiêu quota đáng kể). */
    suspend fun validateKey(apiKey: String): Int = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/models?key=${apiKey.trim()}"
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).await().use { response ->
            if (response.isSuccessful) {
                response.body?.string()?.let { body ->
                    JSONObject(body).optJSONArray("models")?.length() ?: 0
                } ?: 0
            } else {
                throw GeminiException(humanError(response.code, response.body?.string()), response.code)
            }
        }
    }

    /** Hỏi đáp tự do dựa trên ngữ cảnh màn hình (dùng cho tab Chat AI). */
    suspend fun chat(
        apiKey: String,
        model: String,
        question: String,
        screenContext: String
    ): String {
        require(apiKey.isNotBlank()) { "Chưa cấu hình API key Gemini" }
        val prompt = buildString {
            appendLine("Bạn là trợ lý kiểm chứng thông tin tiếng Việt của LTM FactCheck AI.")
            appendLine("Trả lời ngắn gọn (tối đa 6 câu), nêu rõ nếu thông tin chưa đủ cơ sở.")
            if (screenContext.isNotBlank()) {
                appendLine("Ngữ cảnh người dùng đang xem trên màn hình:")
                appendLine("\"\"\"${screenContext.take(4000)}\"\"\"")
            }
            appendLine("Câu hỏi: $question")
        }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 1024)
            })
        }
        val (text, _) = callGenerate(apiKey, model, body, useTools = true)
        return text.trim().ifBlank { "Xin lỗi, tôi chưa có câu trả lời cho nội dung này." }
    }

    // ---------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------

    private suspend fun callGenerate(
        apiKey: String,
        model: String,
        body: JSONObject,
        useTools: Boolean
    ): Pair<String, List<ReferenceSource>> = withContext(Dispatchers.IO) {
        val modelId = model.trim().ifBlank { AiSettingsStore.DEFAULT_MODEL }
        val url = "$BASE_URL/$modelId:generateContent?key=${apiKey.trim()}"
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        httpClient.newCall(request).await().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GeminiException(humanError(response.code, raw), response.code)
            }
            parseGenerateResponse(raw)
        }
    }

    private fun parseGenerateResponse(raw: String): Pair<String, List<ReferenceSource>> {
        val root = runCatching { JSONObject(raw) }.getOrElse {
            throw GeminiException("Phản hồi từ Gemini không hợp lệ.")
        }
        root.optJSONObject("error")?.let {
            throw GeminiException(it.optString("message", "Gemini trả về lỗi."))
        }
        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw GeminiException("Gemini không trả về kết quả (có thể do bộ lọc an toàn).")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                    if (t.isNotBlank()) append(t)
                }
            }
        }
        if (text.isBlank()) {
            throw GeminiException("Gemini trả về nội dung rỗng. Vui lòng thử lại.")
        }
        return text to extractGroundingSources(candidate)
    }

    private fun extractGroundingSources(candidate: JSONObject): List<ReferenceSource> {
        val chunks = candidate.optJSONObject("groundingMetadata")
            ?.optJSONArray("groundingChunks") ?: return emptyList()
        val result = mutableListOf<ReferenceSource>()
        for (i in 0 until chunks.length()) {
            val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
            val uri = web.optString("uri").orEmpty()
            if (uri.isBlank()) continue
            result += ReferenceSource(
                title = web.optString("title").ifBlank { uri },
                url = uri,
                publisher = runCatching { java.net.URI(uri).host ?: "Nguồn web" }
                    .getOrDefault("Nguồn web")
                    .removePrefix("www."),
                publishDate = "Truy xuất tự động",
                supportingSnippet = "Nguồn do Google Search grounding trả về trong lần kiểm chứng này."
            )
        }
        return result.distinctBy { it.url }
    }

    // ---------------------------------------------------------------------
    // Prompt + JSON
    // ---------------------------------------------------------------------

    private fun buildRequestJson(
        claim: String,
        imageBase64: String?,
        imageMime: String,
        jsonMime: Boolean = false
    ): JSONObject {
        val parts = JSONArray().apply {
            put(JSONObject().put("text", "NỘI DUNG CẦN KIỂM CHỨNG:\n$claim"))
            if (imageBase64 != null) {
                put(JSONObject().put("text",
                    "Ảnh chụp màn hình đính kèm là ngữ cảnh bổ sung. Hãy đọc cả chữ trong ảnh."))
                put(JSONObject().put("inlineData", JSONObject().apply {
                    put("mimeType", imageMime)
                    put("data", imageBase64)
                }))
            }
        }

        return JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", parts)
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", 2048)
                put("responseMimeType", "application/json")
            })
        }
    }

    private fun parseModelJson(text: String): JSONObject {
        val cleaned = text.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        runCatching { return JSONObject(cleaned) }

        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) {
            runCatching { return JSONObject(cleaned.substring(start, end + 1)) }
        }
        throw GeminiException("Không đọc được kết quả JSON từ Gemini. Vui lòng thử lại.")
    }

    private fun toRecord(
        json: JSONObject,
        claim: String,
        sourceApp: String,
        rawScreenText: String,
        groundingSources: List<ReferenceSource>
    ): FactCheckRecord {
        val verdict = mapVerdict(json.optString("verdict"))
        val confidenceRaw = json.optDouble("confidence", 0.0)
        val confidence = when {
            confidenceRaw > 1.0 -> (confidenceRaw / 100.0).toFloat()
            confidenceRaw > 0.0 -> confidenceRaw.toFloat()
            else -> 0.5f
        }.coerceIn(0f, 1f)

        val redFlags = json.optJSONArray("red_flags")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it, "").takeIf(String::isNotBlank) }
        }.orEmpty()

        val modelSources = json.optJSONArray("sources")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url").takeIf { it.startsWith("http") } ?: return@mapNotNull null
                ReferenceSource(
                    title = o.optString("title").ifBlank { url },
                    url = url,
                    publisher = o.optString("publisher").ifBlank { hostOf(url) },
                    publishDate = o.optString("date").ifBlank { "Không rõ" },
                    supportingSnippet = o.optString("snippet").ifBlank { "Nguồn do AI đề xuất, cần đối chiếu." }
                )
            }
        }.orEmpty()

        // Nguồn grounding (Google Search) là nguồn thật, ưu tiên đứng trước.
        val references = (groundingSources + modelSources)
            .distinctBy { it.url }
            .take(8)

        val summary = json.optString("summary").ifBlank { verdict.description }
        val explanation = json.optString("explanation").ifBlank { summary }
        val evidence = json.optString("evidence").ifBlank { "Chưa thu thập được bằng chứng cụ thể." }
        val counter = json.optString("counter_evidence")
        val limitations = json.optString("limitations")
            .ifBlank { "Kết quả do AI tổng hợp, chỉ mang tính tham khảo, không thay thế kết luận của cơ quan chức năng." }

        val detailBuilder = StringBuilder(explanation)
        if (redFlags.isNotEmpty()) {
            detailBuilder.append("\n\nDấu hiệu đáng ngờ:\n")
            redFlags.forEach { detailBuilder.append("• ").append(it).append('\n') }
        }

        return FactCheckRecord(
            originalClaim = claim,
            normalizedClaim = json.optString("normalized_claim").ifBlank { claim },
            verdict = verdict,
            shortExplanation = summary,
            detailedExplanation = detailBuilder.toString().trim(),
            evidence = evidence,
            counterEvidence = counter,
            references = references,
            checkedAt = System.currentTimeMillis(),
            limitations = limitations,
            confidence = confidence,
            sourceApp = sourceApp,
            rawScreenText = rawScreenText,
            isAlerted = verdict == FactCheckVerdict.SAI || verdict == FactCheckVerdict.GAY_HIEU_LAM
        )
    }

    private fun mapVerdict(raw: String): FactCheckVerdict {
        val normalized = raw.trim().uppercase()
        FactCheckVerdict.entries.firstOrNull { it.name == normalized }?.let { return it }
        return when {
            normalized.contains("ĐÚNG MỘT PHẦN") -> FactCheckVerdict.DUNG_MOT_PHAN
            normalized.contains("CHƯA ĐỦ") -> FactCheckVerdict.CHUA_DU_BANG_CHUNG
            normalized.contains("Ý KIẾN") -> FactCheckVerdict.KHONG_THE_KIEM_CHUNG
            normalized.contains("GÂY HIỂU") || normalized.contains("SAI LỆCH") -> FactCheckVerdict.GAY_HIEU_LAM
            normalized.contains("SAI") -> FactCheckVerdict.SAI
            normalized.contains("ĐÚNG") -> FactCheckVerdict.DUNG
            else -> FactCheckVerdict.CHUA_DU_BANG_CHUNG
        }
    }

    private fun humanError(code: Int, raw: String?): String {
        val apiMessage = runCatching {
            JSONObject(raw.orEmpty()).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return when (code) {
            400 -> "Yêu cầu không hợp lệ. Kiểm tra lại tên model trong Cài đặt.${apiMessage.suffix()}"
            401, 403 -> "API key không hợp lệ hoặc chưa được cấp quyền cho Gemini API."
            404 -> "Không tìm thấy model đã chọn. Hãy chọn lại model trong Cài đặt."
            429 -> "Đã hết hạn mức (quota) của API key. Vui lòng thử lại sau hoặc dùng key khác."
            in 500..599 -> "Máy chủ Gemini đang bận. Vui lòng thử lại sau ít phút."
            else -> "Không gọi được Gemini (HTTP $code).${apiMessage.suffix()}"
        }
    }

    private fun String.suffix(): String = if (isBlank()) "" else " Chi tiết: ${take(180)}"

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host?.removePrefix("www.") ?: "Nguồn web" }
            .getOrDefault("Nguồn web")

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MAX_CLAIM_CHARS = 6000

        private const val SYSTEM_PROMPT = """
Bạn là trợ lý kiểm chứng thông tin tiếng Việt.
Nhiệm vụ: người dùng gửi nội dung đọc được từ màn hình (văn bản). Hãy trả lời: thông tin này ĐÚNG hay SAI, kèm giải thích ngắn gọn và các nguồn liên quan (link) nếu biết.

QUY TẮC:
- Chỉ kết luận dựa trên kiến thức của bạn. Nếu không đủ dữ liệu, chọn CHUA_DU_BANG_CHUNG.
- Trung lập, không suy diễn.
- Không bịa link: chỉ đưa URL bạn thực sự biết; nếu không chắc, để "sources": [].

ĐỊNH DẠNG ĐẦU RA: chỉ trả về DUY NHẤT một đối tượng JSON hợp lệ, không markdown:
{
  "verdict": "DUNG" | "SAI" | "CHUA_DU_BANG_CHUNG",
  "confidence": 0.0-1.0,
  "summary": "kết luận ngắn 1 câu tiếng Việt",
  "explanation": "giải thích 2-4 câu tiếng Việt",
  "sources": [{"title": "...", "url": "https://..."}]
}
Tất cả nội dung bằng tiếng Việt.
"""


        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        fun encodeImage(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

        fun today(): String =
            SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(Date())
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(
                GeminiException("Không kết nối được tới Gemini. Kiểm tra mạng rồi thử lại.")
            )
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
