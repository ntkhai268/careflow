# CareFlow AI Clinical Assistant Service (`careflow-ai-service`)

Tài liệu tổng quan kiến trúc và API Reference của Microservice `careflow-ai-service`.

---

## 1. Thông tin chung

- **Service Name**: `careflow-ai-service`
- **Module Artifact**: `careflow-ai-service`
- **Port**: `8091`
- **Contract ID**: `CF-SVC-12` (v1.0)
- **Database**: In-memory Interaction Audit Store / Rule Engine

---

## 2. Google AI Studio (Gemini 1.5 Flash) Integration & Guardrails

- **API Key Env Var**: `GEMINI_API_KEY` (hoặc cấu hình `gemini.api-key` trong `application.yml`).
- **Endpoint Provider**: `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${GEMINI_API_KEY}`
- **Fallback**: Nếu chưa cấu hình `GEMINI_API_KEY` hoặc mạng gián đoạn, tự động chuyển về chế độ `MOCK_RULE_BASED` giúp hệ thống hoạt động liên tục không bị gián đoạn ca khám.
- **Strict Guardrails**:
  1. Chỉ hỗ trợ các vấn đề y tế, chẩn đoán lâm sàng, dược lý và vận hành bệnh viện CareFlow.
  2. Tự động từ chối lịch sự các câu hỏi ngoài luồng (thời tiết, thể thao, chính trị, game...).
  3. Mọi phản hồi đều đính kèm Tuyên bố miễn trừ trách nhiệm y tế (Medical Disclaimer).

## 3. API Endpoints Reference

| Method | Path | Security Scope | Description |
|---|---|---|---|
| `POST` | `/api/ai/clinical-suggestions` | Assigned `DOCTOR` | Phân tích ca khám, trả chẩn đoán phân biệt, cảnh báo & dữ liệu thiếu |
| `POST` | `/api/ai/clinical-chat` | Assigned `DOCTOR` | Tương tác hỏi đáp mở rộng theo ngữ cảnh phiên khám |
| `GET` | `/api/ai/interactions/{interactionId}` | Doctor / Auditor | Truy vấn lịch sử tương tác AI và audit metadata |

---

## 3. Quy chuẩn An toàn Y tế (Clinical Safety Guardrails)

1. **Human-in-the-Loop**: AI đóng vai trò người trợ lý gợi ý tham khảo. Mọi quyết định chẩn đoán, kê đơn, chỉ định CLS phải do Bác sĩ chủ động bấm nút xác nhận.
2. **Medical Disclaimer**: 100% phản hồi từ AI Service đều có đính kèm tuyên bố miễn trừ trách nhiệm y tế chuẩn.
3. **Traceable Evidence**: Mỗi gợi ý chẩn đoán phân biệt đều gắn kèm thẻ Bằng chứng trỏ nguồn (`CONSULTATION`, `EMR`, `LAB`).
