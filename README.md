# LTM FactCheck AI

Trợ lý kiểm chứng thông tin (fact-check) chạy trên Android: nhập văn bản, chụp màn hình hoặc bật trợ lý nổi để AI đọc nội dung đang xem (TikTok, YouTube, Facebook, báo chí) và đưa ra kết luận kèm bằng chứng, nguồn đối chiếu.

> Kết quả do AI tổng hợp, **chỉ mang tính tham khảo**, không thay thế kết luận của cơ quan chức năng hay chuyên gia.

## Tính năng

- **Kiểm chứng bằng Gemini**: gọi `generateContent` với Google Search grounding để lấy nguồn thật, trả về JSON có cấu trúc (kết luận, độ tin cậy, bằng chứng, dấu hiệu đáng ngờ, nguồn).
- **3 trụ cột kết luận**: ĐÚNG / SAI / CHƯA ĐỦ DỮ LIỆU (kèm các mức Đúng một phần, Gây hiểu lầm, Ý kiến cá nhân).
- **Trợ lý nổi**: nút nổi trên ứng dụng khác, chụp màn hình bằng MediaProjection, OCR bằng ML Kit, cảnh báo khi phát hiện nội dung sai lệch.
- **Nhận diện lời nói**: SpeechRecognizer tiếng Việt để kiểm chứng nội dung video đang phát.
- **Lịch sử kiểm chứng**: lưu cục bộ bằng Room, tìm kiếm và lọc theo kết luận.
- **Chat AI**: hỏi đáp dựa trên ngữ cảnh màn hình hiện tại.
- **Fallback offline**: khi chưa có API key hoặc mất mạng, ứng dụng dùng bộ dữ liệu mẫu cục bộ và ghi rõ giới hạn trong kết quả.

## Yêu cầu

- Android 7.0 (API 24) trở lên.
- Một **Gemini API key** lấy miễn phí tại [Google AI Studio](https://aistudio.google.com/apikey).

## Cài đặt & cấu hình

1. Tải APK ở mục **Releases** và cài đặt (cho phép "Cài từ nguồn không xác định").
2. Mở app → tab **Cài đặt** → mục **CẤU HÌNH AI GEMINI**.
3. Dán API key, giữ model `gemini-3.6-flash` (mặc định, nhanh và rẻ) hoặc đổi sang `gemini-3.8-flash` (phân tích sâu hơn), nhấn **Lưu cấu hình**.
4. Nhấn **Kiểm tra kết nối** để xác nhận key hợp lệ.

API key chỉ được lưu trong bộ nhớ riêng của ứng dụng trên thiết bị (`DataStore`), **không nhúng vào APK**, không gửi đi đâu ngoài Google Gemini API.

## Build từ mã nguồn

Yêu cầu: JDK 21, Android SDK Platform 36 + Build-Tools 36.0.0.

```bash
# 1. Trỏ tới Android SDK
echo "sdk.dir=$HOME/android-sdk" > local.properties

# 2. Build bản debug
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

# 3. Build bản release (cần keystore)
export KEYSTORE_PATH=/duong/dan/upload-key.jks
export STORE_PASSWORD=...
export KEY_PASSWORD=...
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Tạo keystore mới:

```bash
keytool -genkeypair -v -keystore upload-key.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$STORE_PASSWORD" -keypass "$KEY_PASSWORD" \
  -dname "CN=LTM FactCheck, O=LTM, C=VN"
```

## Kiến trúc

```
app/src/main/java/com/example/
├── ai/                  # Tích hợp Gemini
│   ├── AiSettingsStore.kt        # DataStore: API key + model (không nằm trong APK)
│   ├── GeminiFactCheckClient.kt  # OkHttp → generateContent, parse JSON, grounding
│   └── FactCheckCoordinator.kt   # Điều phối AI + fallback cục bộ
├── engine/              # OCR, trích xuất claim, phát hiện khung hình, giọng nói
├── service/             # FloatingAssistantService (overlay + MediaProjection)
├── ui/                  # Compose: Dashboard, Chat, History, Detail, Settings
├── data/                # Room: entity, dao, database, repository
└── model/               # FactCheckRecord, verdict, settings
```

Luồng kiểm chứng:

```
Người dùng / Màn hình (OCR) ─→ ClaimExtractor ─→ FactCheckCoordinator
                                                      ├─ có API key → Gemini (grounding)
                                                      └─ lỗi/không key → Knowledge base cục bộ
                                                 ─→ FactCheckRecord ─→ Room + UI
```

## Quyền riêng tư

- Không ghi hình liên tục: khung hình chỉ nằm trong RAM để OCR rồi hủy.
- Không đọc nội dung `FLAG_SECURE` (ngân hàng, mật khẩu).
- Không dùng AccessibilityService.
- Dữ liệu lịch sử nằm trên máy, xóa được từ Cài đặt.

## Giới hạn đã biết

- AI có thể sai hoặc bỏ sót ngữ cảnh; luôn đối chiếu nguồn gốc.
- Một số model không cho phép bật Google Search grounding cùng lúc với JSON mode — client tự thử lại không kèm tools.
- Ảnh chụp màn hình được gửi dạng JPEG nén (chất lượng 70) để giảm băng thông.
- Giám sát nền tiêu tốn pin; nên dùng tần suất quét "Tiết kiệm" khi không cần phản hồi tức thì.

## Đóng góp

Mở issue hoặc pull request. Mọi thay đổi cần build được bằng `./gradlew :app:assembleDebug`.

## Giấy phép

MIT
