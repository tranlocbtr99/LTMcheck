package com.example.ai

import android.util.Base64
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import com.example.model.ReferenceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Lỗi gọi DeepSeek kèm thông điệp thân thiện để hiển thị cho người dùng. */
class DeepSeekException(message: String, val httpCode: Int = 0) : Exception(message)

/**
 * Client gọi DeepSeek API để kiểm chứng thông tin.
 *
 * Luồng:
 *  1. Gọi Search API (`/v1/tools/search`) để lấy nguồn web thật (title, url, summary, ngày, độ tin cậy).
 *  2. Nhồi kết quả tìm kiếm vào prompt, yêu cầu model chỉ trích dẫn các URL đó và trả JSON.
 *  3. Gọi `/chat/completions` (OpenAI-compatible) với `response_format = json_object`.
 *
 * Model `deepseek-flash` hỗ trợ cả ảnh (vision) nên vẫn đọc được ảnh chụp màn hình.
 * API key do người dùng nhập trong Cài đặt, không nhúng vào APK.
 */
class DeepSeekFactCheckClient(
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
        require(apiKey.isNotBlank()) { "Chưa cấu hình API key DeepSeek" }
        val trimmedClaim = claim.trim().take(MAX_CLAIM_CHARS)
        if (trimmedClaim.isEmpty() && imageBase64 == null) {
            throw DeepSeekException("Nội dung cần kiểm chứng đang trống.")
        }

        val search = runCatching { searchWeb(apiKey, trimmedClaim) }
        val hits = search.getOrDefault(emptyList())
        val searchNote = search.exceptionOrNull()
            ?.takeIf { hits.isEmpty() }
            ?.let { "Không truy vấn được nguồn web (${it.message})." }

        // Chỉ deepseek-flash đọc được ảnh; nếu người dùng chọn model khác mà có ảnh
        // thì tự chuyển sang deepseek-flash thay vì để API trả lỗi.
        val effectiveModel = if (imageBase64 != null && !supportsVision(model)) {
            AiSettingsStore.DEFAULT_MODEL
        } else {
            model
        }

        val body = buildChatBody(
            claim = trimmedClaim,
            imageBase64 = imageBase64,
            imageMime = imageMime,
            hits = hits
        )
        val text = callChat(apiKey, effectiveModel, body)

        val parsed = parseModelJson(text)
        return toRecord(
            json = parsed,
            claim = trimmedClaim.ifBlank { "Hình ảnh được gửi để kiểm chứng" },
            sourceApp = sourceApp,
            rawScreenText = rawScreenText.ifBlank { trimmedClaim },
            hits = hits,
            searchNote = searchNote
        )
    }

    /** Hỏi đáp tự do dựa trên ngữ cảnh màn hình (dùng cho tab Chat AI). */
    suspend fun chat(
        apiKey: String,
        model: String,
        question: String,
        screenContext: String
    ): String {
        require(apiKey.isNotBlank()) { "Chưa cấu hình API key DeepSeek" }
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
            put("model", model.trim().ifBlank { AiSettingsStore.DEFAULT_MODEL })
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", CHAT_SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)
            put("max_tokens", 1200)
            put("stream", false)
            put("thinking", JSONObject().put("type", "disabled"))
        }
        val text = callChat(apiKey, model, body)
        return text.trim().ifBlank { "Xin lỗi, tôi chưa có câu trả lời cho nội dung này." }
    }

    /** Kiểm tra key có hợp lệ không bằng endpoint nhẹ `/models`. */
    suspend fun validateKey(apiKey: String): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/models")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        httpClient.newCall(request).await().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw DeepSeekException(humanError(response.code, raw), response.code)
            }
            JSONObject(raw).optJSONArray("data")?.length() ?: 0
        }
    }

    // ---------------------------------------------------------------------
    // Search API
    // ---------------------------------------------------------------------

    private suspend fun searchWeb(apiKey: String, claim: String): List<WebHit> =
        withContext(Dispatchers.IO) {
            val query = claim.replace(Regex("\\s+"), " ").trim().take(QUERY_MAX_CHARS)
            if (query.isEmpty()) return@withContext emptyList()

            val body = JSONObject().apply {
                put("query", query)
                put("count", SEARCH_COUNT)
                put("need_content", false)
            }
            val request = Request.Builder()
                .url("$BASE_URL/v1/tools/search")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

            httpClient.newCall(request).await().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw DeepSeekException(
                        "Tìm kiếm web thất bại: ${humanError(response.code, raw)}",
                        response.code
                    )
                }
                val results = JSONObject(raw).optJSONArray("results") ?: return@withContext emptyList()
                (0 until results.length()).mapNotNull { i ->
                    val o = results.optJSONObject(i) ?: return@mapNotNull null
                    val url = o.optString("url").takeIf { it.startsWith("http") } ?: return@mapNotNull null
                    WebHit(
                        title = o.optString("title").ifBlank { url },
                        url = url,
                        siteName = o.optString("site_name").ifBlank { hostOf(url) },
                        date = formatDate(o.optString("date")),
                        summary = o.optString("summary")
                            .ifBlank { o.optString("snippet") }
                            .take(600),
                        authority = o.optInt("authority", -1)
                    )
                }
            }
        }

    // ---------------------------------------------------------------------
    // Chat Completions
    // ---------------------------------------------------------------------

    private fun buildChatBody(
        claim: String,
        imageBase64: String?,
        imageMime: String,
        hits: List<WebHit>
    ): JSONObject {
        val userText = buildString {
            appendLine(SYSTEM_PROMPT)
            appendLine()
            appendLine("NỘI DUNG CẦN KIỂM CHỨNG:")
            appendLine(claim)
            if (hits.isNotEmpty()) {
                appendLine()
                appendLine("KẾT QUẢ TÌM KIẾM WEB (nguồn thật). CHỈ được trích dẫn các URL trong danh sách này:")
                hits.forEachIndexed { index, hit ->
                    appendLine("${index + 1}. ${hit.title} — ${hit.url}")
                    appendLine("   Nguồn: ${hit.siteName}${if (hit.date.isNotBlank()) " · ${hit.date}" else ""}")
                    if (hit.summary.isNotBlank()) appendLine("   Tóm tắt: ${hit.summary}")
                }
                appendLine()
                appendLine("Nếu kết quả tìm kiếm không liên quan tới tuyên bố, hãy chọn CHUA_DU_BANG_CHUNG thay vì suy đoán.")
            } else {
                appendLine()
                appendLine("Không có kết quả tìm kiếm web cho nội dung này. Hãy thận trọng: nếu không chắc, chọn CHUA_DU_BANG_CHUNG và để \"sources\" là [].")
            }
        }

        val userContent: Any = if (imageBase64 != null) {
            JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", userText)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().put("url", "data:$imageMime;base64,$imageBase64"))
                })
            }
        } else {
            userText
        }

        return JSONObject().apply {
            put("model", AiSettingsStore.DEFAULT_MODEL) // ghi đè sau bằng model người dùng chọn
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            })
            put("temperature", 0.2)
            put("max_tokens", 2400)
            put("stream", false)
            put("response_format", JSONObject().put("type", "json_object"))
            put("thinking", JSONObject().put("type", "disabled"))
        }
    }

    private suspend fun callChat(
        apiKey: String,
        model: String,
        body: JSONObject
    ): String = withContext(Dispatchers.IO) {
        body.put("model", model.trim().ifBlank { AiSettingsStore.DEFAULT_MODEL })
        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        var attempt = 0
        while (true) {
            try {
                httpClient.newCall(request).await().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw DeepSeekException(humanError(response.code, raw), response.code)
                    }
                    return@withContext parseCompletion(raw)
                }
            } catch (e: DeepSeekException) {
                if (e.httpCode in TRANSIENT_CODES && attempt == 0) {
                    attempt++
                    delay(800)
                } else {
                    throw e
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw DeepSeekException("Không gọi được DeepSeek.")
    }

    private fun parseCompletion(raw: String): String {
        val root = runCatching { JSONObject(raw) }.getOrElse {
            throw DeepSeekException("Phản hồi từ DeepSeek không hợp lệ.")
        }
        root.optJSONObject("error")?.let {
            throw DeepSeekException(it.optString("message", "DeepSeek trả về lỗi."))
        }
        val choice = root.optJSONArray("choices")?.optJSONObject(0)
            ?: throw DeepSeekException("DeepSeek không trả về kết quả (có thể do bộ lọc nội dung).")
        val content = choice.optJSONObject("message")?.optString("content").orEmpty()
        if (content.isBlank()) {
            if (choice.optString("finish_reason") == "length") {
                throw DeepSeekException("Kết quả bị cắt do giới hạn token. Vui lòng thử lại với nội dung ngắn hơn.")
            }
            throw DeepSeekException("DeepSeek trả về nội dung rỗng. Vui lòng thử lại.")
        }
        return content
    }

    // ---------------------------------------------------------------------
    // Prompt + JSON
    // ---------------------------------------------------------------------

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
        throw DeepSeekException("Không đọc được kết quả JSON từ DeepSeek. Vui lòng thử lại.")
    }

    private fun toRecord(
        json: JSONObject,
        claim: String,
        sourceApp: String,
        rawScreenText: String,
        hits: List<WebHit>,
        searchNote: String?
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

        // Chỉ dùng nguồn thật từ Search API; URL model tự nghĩ ra bị loại bỏ.
        val references = hits.map { hit ->
            ReferenceSource(
                title = hit.title,
                url = hit.url,
                publisher = hit.siteName,
                publishDate = hit.date.ifBlank { "Không rõ" },
                supportingSnippet = hit.summary.ifBlank { "Nguồn từ tìm kiếm web DeepSeek." },
                isPrimarySource = hit.authority >= 3
            )
        }.distinctBy { it.url }.take(8)

        val summary = json.optString("summary").ifBlank { verdict.description }
        val explanation = json.optString("explanation").ifBlank { summary }
        val evidence = json.optString("evidence").ifBlank { "Chưa thu thập được bằng chứng cụ thể." }
        val counter = json.optString("counter_evidence")

        val baseLimitations = json.optString("limitations")
            .ifBlank { "Kết quả do AI tổng hợp, chỉ mang tính tham khảo, không thay thế kết luận của cơ quan chức năng." }
        val limitations = buildString {
            append(baseLimitations)
            if (references.isEmpty()) {
                append("\n\nKhông tìm được nguồn web đối chiếu cho tuyên bố này, nên kết luận chủ yếu dựa trên kiến thức của model.")
            }
            searchNote?.let { append("\n\n").append(it) }
        }

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
            limitations = limitations.trim(),
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
            400 -> "Yêu cầu không hợp lệ. Kiểm tra lại model trong Cài đặt.${apiMessage.suffix()}"
            401 -> "API key DeepSeek không hợp lệ hoặc đã bị thu hồi."
            402 -> "Tài khoản DeepSeek không đủ số dư. Nạp thêm tại platform.deepseek.com/top_up."
            404 -> "Không tìm thấy endpoint/model. Hãy chọn lại model trong Cài đặt."
            422 -> "Tham số không hợp lệ.${apiMessage.suffix()}"
            429 -> "Vượt giới hạn tần suất (QPS) của DeepSeek. Vui lòng thử lại sau vài giây."
            in 500..599 -> "Máy chủ DeepSeek đang bận. Vui lòng thử lại sau ít phút."
            else -> "Không gọi được DeepSeek (HTTP $code).${apiMessage.suffix()}"
        }
    }

    private fun String.suffix(): String = if (isBlank()) "" else " Chi tiết: ${take(180)}"

    /** Model có nhận ảnh đầu vào hay không (hiện chỉ deepseek-flash). */
    private fun supportsVision(model: String): Boolean =
        !model.trim().lowercase().contains("v4-pro")

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host?.removePrefix("www.") ?: "Nguồn web" }
            .getOrDefault("Nguồn web")

    /** RFC3339 → dd/MM/yyyy, trả về chuỗi rỗng nếu không đọc được. */
    private fun formatDate(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }.getOrElse { raw.take(10) }
    }

    /** Một kết quả từ Search API của DeepSeek. */
    private data class WebHit(
        val title: String,
        val url: String,
        val siteName: String,
        val date: String,
        val summary: String,
        val authority: Int
    )

    companion object {
        private const val BASE_URL = "https://api.deepseek.com"
        private const val MAX_CLAIM_CHARS = 6000
        private const val QUERY_MAX_CHARS = 120
        private const val SEARCH_COUNT = 6

        /** Mã lỗi tạm thời, đáng thử lại một lần. */
        private val TRANSIENT_CODES = setOf(500, 502, 503, 504)

        private const val CHAT_SYSTEM_PROMPT =
            "Bạn là trợ lý kiểm chứng thông tin người Việt. Trả lời trung lập, ngắn gọn, " +
                "nêu rõ khi thiếu dữ liệu. Không bịa nguồn."

        private const val SYSTEM_PROMPT = """Bạn là chuyên gia kiểm chứng thông tin (fact-checker) người Việt, làm việc theo chuẩn IFCN.
Nhiệm vụ: đánh giá một tuyên bố/tin nhắn/bài viết do người dùng gửi, có thể kèm ảnh chụp màn hình.

QUY TẮC BẮT BUỘC:
1. Chỉ kết luận dựa trên bằng chứng kiểm chứng được. Nếu thiếu dữ liệu, chọn CHUA_DU_BANG_CHUNG, tuyệt đối không bịa.
2. Trung lập về chính trị, tôn giáo, cá nhân. Không suy diễn động cơ.
3. Ưu tiên nguồn sơ cấp: cơ quan nhà nước, WHO, báo cáo khoa học, tổ chức fact-check uy tín.
4. Nêu rõ dấu hiệu đáng ngờ (giật tít, khẩn cấp, lãi suất phi thực tế, yêu cầu chuyển tiền, link lạ...).
5. Không hướng dẫn hành vi nguy hiểm, không đưa lời khuyên y tế/pháp lý thay chuyên gia.

ĐỊNH DẠNG ĐẦU RA: chỉ trả về DUY NHẤT một đối tượng json hợp lệ, không markdown, không giải thích ngoài json:
{
  "verdict": "DUNG" | "SAI" | "DUNG_MOT_PHAN" | "GAY_HIEU_LAM" | "CHUA_DU_BANG_CHUNG" | "KHONG_THE_KIEM_CHUNG",
  "confidence": 0.0-1.0,
  "normalized_claim": "phát biểu lại tuyên bố trong 1 câu",
  "summary": "kết luận ngắn 1-2 câu tiếng Việt",
  "explanation": "lập luận 3-6 câu, nêu lý do và bối cảnh",
  "evidence": "bằng chứng chính, trích nguồn cụ thể nếu có",
  "counter_evidence": "bằng chứng phản bác nếu có, nếu không thì chuỗi rỗng",
  "red_flags": ["dấu hiệu đáng ngờ 1", "..."],
  "limitations": "giới hạn của kết luận này"
}
Tất cả nội dung bằng tiếng Việt. Không tự tạo URL; chỉ dùng URL có trong phần KẾT QUẢ TÌM KIẾM WEB."""

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        fun encodeImage(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(
                DeepSeekException("Không kết nối được tới DeepSeek. Kiểm tra mạng rồi thử lại.")
            )
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
