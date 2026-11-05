package com.example.model

import androidx.compose.ui.graphics.Color

/**
 * 3 Trụ cột kết luận bắt buộc:
 * 1. ĐÚNG (True)
 * 2. SAI (False / Sai lệch / Gây hiểu lầm)
 * 3. CHƯA ĐỦ CƠ SỞ (Chưa đủ bằng chứng để khẳng định)
 */
enum class PrimaryVerdict(val titleVi: String, val color: Color) {
    DUNG("ĐÚNG", Color(0xFF10B981)),
    SAI("SAI", Color(0xFFEF4444)),
    CHUA_DU_CO_SO("CHƯA ĐỦ DỮ LIỆU", Color(0xFFA855F7));

    companion object {
        val CHUA_DU_DU_LIEU = CHUA_DU_CO_SO
    }
}

/**
 * Hệ thống Fact-Checking của LTM FactCheck AI
 */
enum class FactCheckVerdict(
    val titleVi: String,
    val titleEn: String,
    val color: Color,
    val primaryVerdict: PrimaryVerdict,
    val description: String
) {
    DUNG(
        titleVi = "Đúng",
        titleEn = "True",
        color = Color(0xFF10B981), // Emerald Green
        primaryVerdict = PrimaryVerdict.DUNG,
        description = "Thông tin chính xác, phù hợp với các nguồn dữ liệu khoa học hoặc cơ quan chuyên môn uy tín."
    ),
    SAI(
        titleVi = "Sai",
        titleEn = "False",
        color = Color(0xFFEF4444), // Ruby Red
        primaryVerdict = PrimaryVerdict.SAI,
        description = "Tuyên bố có bằng chứng xác thực chứng minh là sai lệch, thông tin bịa đặt hoặc ngụy tạo."
    ),
    DUNG_MOT_PHAN(
        titleVi = "Đúng một phần",
        titleEn = "Partially True",
        color = Color(0xFFF59E0B), // Amber Yellow
        primaryVerdict = PrimaryVerdict.CHUA_DU_CO_SO,
        description = "Một phần thông tin có cơ sở thực tế, nhưng một số chi tiết khác không chính xác hoặc chưa kiểm chứng."
    ),
    GAY_HIEU_LAM(
        titleVi = "Sai lệch / Gây hiểu lầm",
        titleEn = "Misleading",
        color = Color(0xFFF97316), // Vivid Orange
        primaryVerdict = PrimaryVerdict.SAI,
        description = "Thông tin bị cắt ghép, giật tít, đặt ngoài ngữ cảnh gốc hoặc dễ gây suy diễn sai sự thật."
    ),
    CHUA_DU_BANG_CHUNG(
        titleVi = "Chưa đủ dữ liệu",
        titleEn = "Insufficient Data",
        color = Color(0xFFA855F7), // Purple
        primaryVerdict = PrimaryVerdict.CHUA_DU_CO_SO,
        description = "Hiện chưa có đủ tài liệu, số liệu hoặc nghiên cứu độc lập đáng tin cậy để xác thực hoặc bác bỏ."
    ),
    KHONG_THE_KIEM_CHUNG(
        titleVi = "Chưa đủ dữ liệu (Ý kiến cá nhân)",
        titleEn = "Unverifiable",
        color = Color(0xFF94A3B8), // Slate Gray
        primaryVerdict = PrimaryVerdict.CHUA_DU_CO_SO,
        description = "Nội dung mang tính quan điểm cá nhân, cảm xúc hoặc phỏng đoán tương lai chưa có dữ liệu định lượng."
    );


    companion object {
        fun fromString(name: String?): FactCheckVerdict {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CHUA_DU_BANG_CHUNG
        }
    }
}
