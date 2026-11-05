package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FactCheckApplication
import com.example.ai.AiSettingsStore
import com.example.ai.FactCheckCoordinator
import com.example.data.repository.FactCheckRepository
import com.example.engine.FactCheckEngine
import com.example.engine.FactCheckServiceManager
import com.example.model.AssistantState
import com.example.model.ChatMessage
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import com.example.model.MonitoringSettings
import com.example.service.FloatingAssistantService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FactCheckViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as FactCheckApplication).repository
    private val engine = FactCheckEngine()
    private val coordinator = FactCheckCoordinator.getInstance(application)

    /** Cấu hình AI (key nhập trong Cài đặt, không nhúng vào APK). */
    val aiKeyConfigured: StateFlow<Boolean> = coordinator.hasApiKey.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )
    val aiModel: StateFlow<String> = coordinator.modelFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AiSettingsStore.DEFAULT_MODEL
    )

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

    private val _aiStatusMessage = MutableStateFlow<String?>(null)
    val aiStatusMessage: StateFlow<String?> = _aiStatusMessage.asStateFlow()

    private val _aiTestInProgress = MutableStateFlow(false)
    val aiTestInProgress: StateFlow<Boolean> = _aiTestInProgress.asStateFlow()

    val assistantState: StateFlow<AssistantState> = FactCheckServiceManager.assistantState
    val settings: StateFlow<MonitoringSettings> = FactCheckServiceManager.settings
    val chatMessages: StateFlow<List<ChatMessage>> = FactCheckServiceManager.chatMessages

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedVerdictFilter = MutableStateFlow<FactCheckVerdict?>(null)
    val selectedVerdictFilter: StateFlow<FactCheckVerdict?> = _selectedVerdictFilter.asStateFlow()

    val allHistory: StateFlow<List<FactCheckRecord>> = repository.allRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredHistory: StateFlow<List<FactCheckRecord>> = combine(
        allHistory,
        _searchQuery,
        _selectedVerdictFilter
    ) { records, query, filter ->
        records.filter { record ->
            val matchQuery = query.isBlank() ||
                    record.normalizedClaim.contains(query, ignoreCase = true) ||
                    record.shortExplanation.contains(query, ignoreCase = true)
            val matchFilter = filter == null || record.verdict == filter
            matchQuery && matchFilter
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedDetailRecord = MutableStateFlow<FactCheckRecord?>(null)
    val selectedDetailRecord: StateFlow<FactCheckRecord?> = _selectedDetailRecord.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setVerdictFilter(filter: FactCheckVerdict?) {
        _selectedVerdictFilter.value = filter
    }

    fun selectRecordForDetail(recordId: String) {
        viewModelScope.launch {
            val record = allHistory.value.firstOrNull { it.id == recordId }
            _selectedDetailRecord.value = record
        }
    }

    fun selectRecordDirect(record: FactCheckRecord) {
        _selectedDetailRecord.value = record
    }

    fun toggleFloatingAssistant(context: Context, enable: Boolean) {
        if (enable) {
            FactCheckServiceManager.startService(context)
        } else {
            FactCheckServiceManager.stopService(context)
        }
    }

    fun pauseMonitoring() {
        FactCheckServiceManager.sendUserEvent(FactCheckServiceManager.UserActionEvent.RequestPauseMonitoring)
    }

    fun resumeMonitoring() {
        FactCheckServiceManager.sendUserEvent(FactCheckServiceManager.UserActionEvent.RequestResumeMonitoring)
    }

    fun stopMonitoring() {
        FactCheckServiceManager.sendUserEvent(FactCheckServiceManager.UserActionEvent.RequestStopMonitoring)
    }

    fun verifyManualClaim(claimText: String, sourceApp: String = "Nhập thủ công") {
        if (claimText.isBlank() || _isVerifying.value) return
        viewModelScope.launch {
            _isVerifying.value = true
            _aiStatusMessage.value = null
            try {
                val record = coordinator.verify(
                    claim = claimText,
                    sourceApp = sourceApp
                )
                repository.saveRecord(record)
                FactCheckServiceManager.onNewFactCheckRecord(record)
                _selectedDetailRecord.value = record
                if (!aiKeyConfigured.value) {
                    _aiStatusMessage.value = "Chưa có API key: kết quả đang dùng dữ liệu mẫu cục bộ. Vào Cài đặt để bật AI thật."
                }
            } catch (e: Exception) {
                _aiStatusMessage.value = e.message ?: "Không kiểm chứng được nội dung này."
            } finally {
                _isVerifying.value = false
            }
        }
    }

    /** Lưu API key + model người dùng nhập trong Cài đặt. */
    fun saveAiConfig(apiKey: String, model: String) {
        viewModelScope.launch {
            _aiStatusMessage.value = "Đang lưu cấu hình…"
            coordinator.saveConfig(apiKey, model)
            _aiStatusMessage.value = if (apiKey.isBlank()) {
                "Đã xoá API key. Ứng dụng sẽ dùng dữ liệu mẫu cục bộ."
            } else {
                "Đã lưu cấu hình AI. Nhấn \"Kiểm tra kết nối\" để xác nhận key hoạt động."
            }
        }
    }

    /** Gọi thử DeepSeek để xác nhận key/model hợp lệ. */
    fun testAiConnection(apiKey: String, model: String) {
        if (_aiTestInProgress.value) return
        viewModelScope.launch {
            _aiTestInProgress.value = true
            _aiStatusMessage.value = null
            val result = coordinator.testConnection(apiKey, model)
            _aiTestInProgress.value = false
            _aiStatusMessage.value = result.fold(
                onSuccess = { count -> "Kết nối thành công. Key hợp lệ, truy cập được $count model DeepSeek." },
                onFailure = { e -> "Kết nối thất bại: ${e.message}" }
            )
        }
    }

    fun clearAiStatus() {
        _aiStatusMessage.value = null
    }

    fun askAiAboutScreen(question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            val screenContext = assistantState.value.currentScreenText
            val userMsg = ChatMessage(
                isUser = true,
                text = question,
                onScreenContext = screenContext.takeIf { it.isNotBlank() }
            )
            FactCheckServiceManager.addChatMessage(userMsg)

            // Fact-checking / AI answer synthesis with source grounding
            val aiResponse = coordinator.chat(question, screenContext).getOrNull()
                ?.let { answer ->
                    ChatMessage(
                        isUser = false,
                        text = answer,
                        onScreenContext = screenContext.takeIf { it.isNotBlank() },
                        relatedSources = assistantState.value.latestRecord?.references ?: emptyList()
                    )
                } ?: generateGroundedAiAnswer(question, screenContext)
            FactCheckServiceManager.addChatMessage(aiResponse)
        }
    }

    private fun generateGroundedAiAnswer(question: String, screenContext: String): ChatMessage {
        val qLower = question.lowercase()

        // 1. Phân tích nội dung màn hình đang có
        val topRecord = assistantState.value.latestRecord
        val isScreenAvailable = screenContext.isNotBlank()

        val answerText = when {
            qLower.contains("nguồn") || qLower.contains("ở đâu") -> {
                if (topRecord != null && topRecord.references.isNotEmpty()) {
                    val ref = topRecord.references.first()
                    "Dữ liệu kiểm chứng dựa trên công bố chính thức từ ${ref.publisher} (${ref.publishDate}). Trích dẫn: \"${ref.supportingSnippet}\". Bạn có thể mở trực tiếp đường dẫn tham khảo bên dưới."
                } else {
                    "Theo tiêu chuẩn IFCN, nguồn tham khảo uy tín bao gồm các cơ quan chuyên môn (WHO, Bộ Y Tế, TTXVN, Reuters Fact Check). Hiện trên màn hình chưa trích dẫn rõ nguồn sơ cấp."
                }
            }
            qLower.contains("đúng không") || qLower.contains("sự thật") -> {
                if (topRecord != null) {
                    "Kết luận của hệ thống đối với thông tin này là: [${topRecord.verdict.titleVi}]. ${topRecord.shortExplanation} ${topRecord.detailedExplanation}"
                } else if (isScreenAvailable) {
                    val temp = engine.verifyClaim(screenContext.take(100), "Hỏi đáp AI")
                    "Dựa trên nội dung màn hình bạn đang xem, kết luận sơ bộ là: [${temp.verdict.titleVi}]. ${temp.shortExplanation}"
                } else {
                    "Hiện chưa có phiên đọc màn hình nào đang hoạt động. Vui lòng bật nút nổi và cấp quyền để AI đọc được ngữ cảnh bài viết."
                }
            }
            qLower.contains("phản bác") || qLower.contains("mâu thuẫn") -> {
                if (topRecord != null && topRecord.counterEvidence.isNotBlank()) {
                    "Bằng chứng đối chiếu và phản bác: ${topRecord.counterEvidence}"
                } else {
                    "Không ghi nhận bằng chứng phản biện khoa học nào hỗ trợ cho luận điểm sai lệch này."
                }
            }
            qLower.contains("tóm tắt") || qLower.contains("điểm chính") -> {
                if (topRecord != null) {
                    "Tóm tắt 3 điểm cốt lõi đã xác thực:\n1. Tuyên bố: ${topRecord.normalizedClaim}\n2. Kết luận: ${topRecord.verdict.titleVi}\n3. Khuyến nghị: ${topRecord.shortExplanation}"
                } else {
                    "Vui lòng chọn hoặc quét một bài viết để AI trích xuất các luận điểm cần tóm tắt."
                }
            }
            else -> {
                if (topRecord != null) {
                    "Đối với nội dung \"${topRecord.normalizedClaim}\", hệ thống xác định kết luận [${topRecord.verdict.titleVi}]. ${topRecord.shortExplanation} Khuyến nghị độc giả không chia sẻ các thông tin chưa được kiểm chứng từ cơ quan chức năng."
                } else {
                    "Tôi là trợ lý LTM FactCheck AI. Tôi có thể kiểm chứng các tuyên bố trên màn hình của bạn (TikTok, YouTube, Facebook, Báo chí), giải thích bằng chứng và dẫn nguồn trực tiếp."
                }
            }
        }

        return ChatMessage(
            isUser = false,
            text = answerText,
            onScreenContext = screenContext,
            relatedSources = topRecord?.references ?: emptyList()
        )
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            repository.deleteById(id)
            if (_selectedDetailRecord.value?.id == id) {
                _selectedDetailRecord.value = null
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
            _selectedDetailRecord.value = null
        }
    }

    fun updateSettings(newSettings: MonitoringSettings) {
        FactCheckServiceManager.updateSettings(newSettings)
    }

    fun dismissAlert() {
        FactCheckServiceManager.clearAlert()
    }

    fun simulateAudioSpeech(phrase: String) {
        FactCheckServiceManager.simulateAudioSpeech(getApplication(), phrase)
    }
}
