# Medical Record — Phân biệt Hồ sơ Bệnh án Chủ & Lịch sử Khám Bệnh

Tài liệu này làm rõ vai trò của bảng `medical_records` trong `emr-service`, phân biệt nó với dữ liệu lịch sử khám bệnh, và mô tả phân công trách nhiệm lưu trữ giữa các service trong kiến trúc CareFlow.

---

## 1. `medical_records` là gì?

Bảng `medical_records` là **Hồ sơ Bệnh án Chủ (Master Health Record)** của bệnh nhân trong hệ thống EMR. Đây là một bản ghi **duy nhất, ổn định, tồn tại suốt đời bệnh nhân** trong bệnh viện — tương đương với "bìa hồ sơ" mà bệnh viện lưu trữ và cập nhật theo thời gian.

Dữ liệu trong bảng này mang tính **tĩnh hoặc ít thay đổi**, không phát sinh mới mỗi lần khám:

| Trường | Ý nghĩa | Tần suất thay đổi |
| :--- | :--- | :--- |
| `record_number` | Mã số hồ sơ bệnh viện | Không đổi |
| `blood_type` | Nhóm máu | Không đổi |
| `medical_history` | Tiền sử bệnh nền cá nhân & gia đình | Hiếm khi thay đổi |
| `allergies` | Ghi chú dị ứng tổng quát (legacy text) | Hiếm khi thay đổi |

> **Quy tắc:** 1 bệnh nhân = 1 bản ghi `medical_records`. Không bao giờ có 2 hàng cùng `patient_id`.

---

## 2. Lịch sử Khám Bệnh là gì? Nó khác gì Medical Record?

**Lịch sử khám bệnh** là tập hợp các **bản ghi động, phát sinh theo thời gian**, mỗi bản ghi tương ứng với một ca khám hoàn tất. Mỗi lần bác sĩ hoàn tất phiên khám, một bản ghi lịch sử mới được thêm vào.

```
Bệnh nhân Phạm Đức Anh
├── Lần khám 1 — 15/06/2026 — Cảm cúm (J00) — Đơn thuốc #1
├── Lần khám 2 — 20/07/2026 — Trào ngược dạ dày (K21.9) — Đơn thuốc #2
└── Lần khám 3 — 29/07/2026 — Ca khám đang diễn ra...
```

Đây là dữ liệu có cấu trúc phức tạp: bao gồm sinh hiệu, triệu chứng, chẩn đoán ICD-10, đơn thuốc, ghi chú lâm sàng — tất cả gắn với một **thời điểm** cụ thể.

---

## 3. Ai chịu trách nhiệm lưu trữ từng loại dữ liệu? (Source of Truth)

Trong kiến trúc microservices của CareFlow, mỗi loại dữ liệu có **một service duy nhất** làm nguồn sự thật (Source of Truth):

| Loại dữ liệu | Service chủ sở hữu | Bảng DB | Kiểu bản ghi |
| :--- | :--- | :--- | :--- |
| **Hồ sơ bệnh án chủ** | `emr-service` | `medical_records` | 1 bệnh nhân = 1 row |
| **Thông tin định danh bệnh nhân** | `patient-service` | `patients` | 1 bệnh nhân = 1 row |
| **Dị ứng cấu trúc hóa** | `patient-service` | `patient_allergies` | N dị ứng / bệnh nhân |
| **Lịch sử ca khám (từng lần)** | `consultation-service` | `consultations` | 1 ca khám = 1 row |
| **Đơn thuốc từng ca** | `prescription-service` | `prescriptions` | 1 đơn = 1 row |

> **Nguyên tắc quan trọng:** `emr-service` **không tự tạo ra** dữ liệu lịch sử khám hay đơn thuốc. Nó là service **tổng hợp và cung cấp góc nhìn thống nhất** (Aggregated View) bằng cách liên thông với các service kia.

---

## 4. Tại sao không lưu Lịch sử Khám vào `medical_records`?

Nếu nhồi lịch sử khám vào `medical_records` thì vi phạm nhiều nguyên tắc:

1. **Vi phạm Single Responsibility**: `emr-service` sẽ phải xử lý logic của `consultation-service` và `prescription-service`.
2. **Duplicate Data & Inconsistency**: Dữ liệu nguồn nằm ở `consultation-service`, nếu EMR tự lưu riêng thì sẽ có 2 bản dữ liệu — dễ lệch nhau.
3. **Tight Coupling**: Mọi thay đổi schema ở `consultation-service` sẽ ảnh hưởng tới EMR.

---

## 5. Cách CareFlow Hiện Tại Xử lý Lịch sử Khám

Thay vì EMR tự lưu lại lịch sử, Doctor Web App **gọi trực tiếp** sang các service nguồn khi cần hiển thị:

```
Doctor Web  →  GET /api/consultations/patient/{patientId}   →  consultation-service
Doctor Web  →  GET /api/prescriptions/consultation/{id}     →  prescription-service
```

Đây là **API Aggregation Pattern**: Frontend (hoặc một BFF layer) chịu trách nhiệm gọi nhiều service và ghép dữ liệu để hiển thị. Cách này phù hợp với quy mô đề án hiện tại vì:

- Đơn giản, không cần cơ chế đồng bộ.
- Luôn trả về dữ liệu mới nhất (real-time).
- Không cần EMR phải implement consumer RabbitMQ phức tạp.

---

## 6. Khi Nào Cần Thêm `emr_visit_records` vào EMR?

Nếu sau này hệ thống cần:
- **Tra cứu lịch sử offline** (khi `consultation-service` down).
- **Tổng hợp dữ liệu AI** (tóm tắt toàn bộ lịch sử cho AI Assistant).
- **Báo cáo liên viện** (chia sẻ hồ sơ giữa bệnh viện với bệnh viện khác).

Thì lúc đó mới cần thêm bảng `emr_visit_records` để lưu **snapshot** mỗi ca khám vào EMR database riêng — được đẩy vào thông qua event `consultation.completed` từ RabbitMQ.

Sơ đồ bảng khi đó:

```dbml
Table emr_visit_records {
  id uuid [pk]
  medical_record_id uuid [not null, ref: > medical_records.id]  -- liên kết hồ sơ chủ
  consultation_id uuid [not null, unique]  -- mã phiên khám gốc từ consultation-service
  appointment_id uuid
  doctor_id uuid [not null]
  icd10_code varchar(20)
  icd10_name varchar(255)
  diagnosis text
  symptoms text
  clinical_notes text
  temperature decimal
  blood_pressure varchar(20)
  heart_rate int
  spo2 int
  started_at timestamptz
  completed_at timestamptz
  created_at timestamptz [not null]
}
```

---

## 7. Lưu ý về Trường `allergies` trong `medical_records`

Trường `allergies text` trong `medical_records` là thiết kế **legacy (text tự do)**, sinh ra trước khi hệ thống có bảng `patient_allergies` cấu trúc hóa trong `patient-service`.

**Trạng thái hiện tại:**
- Nguồn sự thật cho dị ứng là `patient_allergies` table trong **`patient-service`**.
- Trường `allergies text` trong `medical_records` trở nên **dư thừa** và không nên dùng để hiển thị trên giao diện.

**Khuyến nghị:**
- Giữ lại trường `allergies text` trong DB để tương thích ngược, nhưng **không expose ra API**.
- Khi Doctor Web cần hiển thị dị ứng bệnh nhân, luôn gọi sang `patient-service` (`GET /api/patients/{id}`) để lấy danh sách `allergies` đã cấu trúc hóa với `severity` rõ ràng (`CRITICAL`, `WARNING`, `INFO`).
