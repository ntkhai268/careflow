# Use Cases - CareFlow AI Clinical Assistant Service (`careflow-ai-service`)

Tài liệu tả các Use Case chính của Phân hệ Trợ lý Lâm sàng AI trong Hệ thống Quản lý Bệnh viện CareFlow.

---

## 1. Danh sách Use Cases (UC Summary)

| UC ID | Tên Use Case | Actor chính | Mô tả tóm tắt |
|---|---|---|---|
| **UC-AI-01** | Yêu cầu Gợi ý Chẩn đoán Phân biệt | Bác sĩ (`DOCTOR`) | Tự động phân tích triệu chứng, sinh hiệu, bệnh nền từ EMR để đưa ra gợi ý khả năng chẩn đoán phân biệt kèm bằng chứng. |
| **UC-AI-02** | Cảnh báo Lâm sàng & Tương tác | Bác sĩ (`DOCTOR`) | Rà soát tiền sử dị ứng thuốc (Penicillin, NSAID...) và đưa ra cảnh báo chống chỉ định trong quá trình khám. |
| **UC-AI-03** | Phát hiện Dữ liệu Lâm sàng Còn thiếu | Bác sĩ (`DOCTOR`) | Liệt kê các chỉ số sinh hiệu, xét nghiệm hoặc câu hỏi tiền sử còn thiếu để hỗ trợ bác sĩ chẩn đoán chính xác hơn. |
| **UC-AI-04** | Tương tác & Hỏi đáp Lâm sàng theo Ngữ cảnh | Bác sĩ (`DOCTOR`) | Cho phép bác sĩ chat tương tác trực tiếp với AI trong ngữ cảnh phiên khám hiện tại. |
| **UC-AI-05** | Truy vấn Audit Log Tương tác AI | Auditor / Doctor | Xem lịch sử các lượt tương tác AI, model name, version, latency và bằng chứng tham chiếu để đảm bảo tính pháp lý. |

---

## 2. Chi tiết từng Use Case

### UC-AI-01: Yêu cầu Gợi ý Chẩn đoán Phân biệt
- **Pre-condition**: Bác sĩ đang trong phiên khám hợp lệ (`IN_PROGRESS`).
- **Main Flow**:
  1. Bác sĩ mở tab **🤖 Trợ lý AI Lâm sàng** trên trang khám bệnh `/consultation/[id]`.
  2. Hệ thống gửi `consultationId` và câu hỏi gợi ý tới `POST /api/ai/clinical-suggestions`.
  3. AI Service lọc ngữ cảnh an toàn (chỉ lấy triệu chứng, sinh hiệu, bệnh nền; loại bỏ PII/BHYT).
  4. Rule Engine / Model phân tích và trả danh sách Chẩn đoán Phân biệt kèm thẻ Bằng chứng (`sourceType`, `field`).
  5. Bác sĩ bấm nút **[ + Áp dụng vào Chẩn đoán ]** để sao chép nội dung gợi ý vào ô ICD-10/Chẩn đoán lâm sàng.
- **Post-condition**: Dữ liệu chẩn đoán chỉ ghi chính thức khi Bác sĩ xác nhận lưu.

---

### UC-AI-02: Cảnh báo Lâm sàng & Tương tác
- **Pre-condition**: Hồ sơ bệnh nhân có ghi nhận tiền sử dị ứng hoặc bệnh nền.
- **Main Flow**:
  1. AI Service quét tiền sử dị ứng (`CRITICAL` / `WARNING`) từ Patient Service / EMR Service.
  2. Tổng hợp danh sách cảnh báo (chống chỉ định thuốc Beta-blocker, Nitrat, NSAIDs...).
  3. Hiển thị khung cảnh báo màu đỏ/vàng trên bảng điều khiển AI để Bác sĩ lưu ý trước khi kê đơn.

---

### UC-AI-03: Tương tác & Hỏi đáp Lâm sàng theo Ngữ cảnh
- **Pre-condition**: Bác sĩ đã khởi tạo tương tác AI trong ca khám.
- **Main Flow**:
  1. Bác sĩ gõ câu hỏi mở rộng vào ô chat (vd: *"Có cần chỉ định xét nghiệm máu khẩn không?"*).
  2. Frontend gửi `POST /api/ai/clinical-chat` kèm `parentInteractionId`.
  3. AI Service phân tích câu hỏi trong ngữ cảnh ca khám và phản hồi kết quả trực quan.
