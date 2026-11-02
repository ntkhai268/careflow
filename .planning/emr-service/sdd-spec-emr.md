# Spec-Driven Development (SDD): EMR Service (`careflow-emr-service`)

> **Bounded Context**: Quản lý Hồ sơ Bệnh án Điện tử Chủ (Master Electronic Medical Record — nhóm máu, tiền sử bệnh nền) và Tổng hợp Hồ sơ Lâm sàng 360° liên thông (`PatientSummaryResponse`: thông tin hành chính, dị ứng, bệnh án chủ, lịch sử khám, đơn thuốc, xét nghiệm).
> **Schema Ownership**: `public.medical_records` (`careflow_emr` DB)
> **Port**: `8086`

---

## 1. Bounded Context & Data Ownership

### 1.1. Bảng CSDL thuộc quyền sở hữu
| Bảng | Vai trò | Ràng buộc (Invariants) |
|---|---|---|
| `medical_records` | Lưu trữ hồ sơ bệnh án chủ của từng bệnh nhân (`patient_id`, `record_number`, `blood_type`, `medical_history`) | `uk_emr_patient` (`patient_id` UNIQUE), `uk_emr_record_number` (`record_number` UNIQUE theo định dạng `EMR-YYYY-XXXXXX`) |

### 1.2. Liên kết ngoại vi (Downstream Clinical Providers)
- `PatientClient` (`careflow-patient-service`):
  - Dùng cho cả **Authorization** (`EmrAccessService`: kiểm tra quyền sở hữu `userId` của `PATIENT` hoặc phân công lâm sàng của `DOCTOR`) và **Aggregation** (thông tin hành chính + danh sách dị ứng `allergies`).
- `ConsultationClient` (`careflow-consultation-service`): Lấy danh sách lịch sử ca khám (`GET /api/v1/consultations/patients/{patientId}`).
- `PrescriptionClient` (`careflow-prescription-service`): Lấy danh sách lịch sử đơn thuốc (`GET /api/v1/prescriptions/patients/{patientId}`).
- `LabClient` (`careflow-lab-service`): Lấy danh sách lịch sử phiếu xét nghiệm (`GET /api/labs/orders/patient/{patientId}`).

---

## 2. Quy tắc Nghiệp vụ & Bảo mật (Invariants)

1. **Read-Only Aggregation Invariant**:
   - `GET /api/emr/patients/{patientId}/summary` **tuyệt đối không tự động tạo mới** bản ghi `MedicalRecord` nếu bệnh nhân chưa có hồ sơ chủ (`findByPatientId` trả về rỗng sẽ ánh xạ `MedicalRecordInfo` rỗng).
2. **Fail-Closed Downstream Provider Policy**:
   - Khi tổng hợp EMR 360°, nếu bất kỳ dịch vụ nguồn nào (`patient-service`, `consultation-service`, `prescription-service`, `lab-service`) trả về `null`, thiếu `data`, hoặc ném ngoại lệ kết nối, hệ thống **bắt buộc trả về HTTP `503 Service Unavailable`** thay vì trả về danh sách rỗng giả tạo (tránh rủi ro lâm sàng khi bác sĩ nhìn thấy "không có tiền sử/đơn thuốc/xét nghiệm" do lỗi mạng).
3. **Strict Access Control (`EmrAccessService`)**:
   - **Đọc (`requireRead`)**: Chỉ cho phép `PATIENT` (đúng chủ hồ sơ `patient.userId == actorUserId`) hoặc `DOCTOR` (được `patient-service` xác thực quyền truy cập hồ sơ bệnh nhân). Các vai trò vận hành (`STAFF`, `LAB_TECHNICIAN`, `ADMIN`) bị chặn với HTTP `403 Forbidden`.
   - **Ghi (`requireWrite`)**: Chỉ cho phép `DOCTOR` được phân công hợp lệ tạo mới hoặc cập nhật `MedicalRecord`. `PATIENT` không được tự chỉnh sửa hồ sơ bệnh án chính thức (`403 Forbidden`).

---

## 3. Kiến trúc Module sau Refactor (`careflow-emr-service`)

| Lớp / Thành phần | Trách nhiệm |
|---|---|
| `MedicalRecord` | Rich Domain Entity quản lý thông tin `patientId`, `recordNumber`, `bloodType`, `medicalHistory` cùng factory `create(...)` và `applyUpdates(...)` |
| `MedicalRecordService` | Điều phối CRUD hồ sơ bệnh án chủ (`createMedicalRecord`, `getMedicalRecordByPatientId`, `updateMedicalRecord`) và ủy quyền tổng hợp EMR 360° |
| `EmrClinicalSummaryAggregator` | Lớp cộng tác chuyên trách tổng hợp dữ liệu EMR 360° từ 4 Feign Clients (`PatientClient`, `ConsultationClient`, `PrescriptionClient`, `LabClient`) theo nguyên tắc fail-closed `503` |
| `EmrAccessService` | Kiểm tra phân quyền đọc/ghi hồ sơ EMR dựa trên vai trò và xác thực quyền truy cập qua `PatientClient` |
| `MedicalRecordController` | REST Controller cung cấp các endpoint `/api/emr/records` và `/api/emr/patients/{patientId}/*` |

---

## 4. Error Matrix

| HTTP Status | Tình huống | Thông điệp chuẩn |
|---|---|---|
| `400 Bad Request` | Bệnh nhân đã có hồ sơ EMR khi gọi `POST /api/emr/records` | `"Bệnh nhân đã có hồ sơ bệnh án EMR"` |
| `401 Unauthorized` | Thiếu header định danh (`X-User-Id`, `X-User-Role`) hoặc phiên đăng nhập không hợp lệ | `"Authenticated user is required"` |
| `403 Forbidden` | Vai trò không thuộc `PATIENT`/`DOCTOR` khi đọc, không phải `DOCTOR` khi ghi, `PATIENT` đọc hồ sơ người khác, hoặc `DOCTOR` không được phân công | `"Patient access is not allowed"` / `"Assigned clinician access is required"` |
| `404 Not Found` | Không tìm thấy `MedicalRecord` theo `patientId` | `ResourceNotFoundException` |
| `503 Service Unavailable` | Không kết nối được hoặc phản hồi rỗng từ `PatientClient`, `ConsultationClient`, `PrescriptionClient`, `LabClient` | `"Patient/Consultation/Prescription/Laboratory history is unavailable"` |

---

## 5. Sequence Diagram (Eraser.io DSL)

```eraser
title Luồng Tra cứu Hồ sơ Bệnh án Điện tử 360° (UC6 - Fail-Closed Aggregation)

actor "Doctor / Patient" as Actor
participant "MedicalRecordController" as MRC
participant "EmrAccessService" as EAS
participant "MedicalRecordService" as MRS
participant "EmrClinicalSummaryAggregator" as ECSA
participant "PatientClient" as PC
participant "ConsultationClient" as CC
participant "PrescriptionClient" as PRC
participant "LabClient" as LC
database "PostgreSQL (EMR)" as DB

Actor -> MRC: GET /api/emr/patients/{patientId}/summary
MRC -> EAS: requireRead(patientId, actorUserId, role)
EAS -> PC: getPatientById(patientId)
PC --> EAS: 200 OK (verify id & owner userId for PATIENT)
MRC -> MRS: getPatientSummary(patientId)
MRS -> DB: findByPatientId(patientId) [Read-only, never auto-creates]
MRS -> ECSA: aggregate(patientId, medicalRecord)
ECSA -> PC: getPatientById(patientId) [Demographics + Allergies]
ECSA -> CC: getConsultationsByPatient(patientId)
ECSA -> PRC: getPrescriptionsByPatient(patientId)
ECSA -> LC: getLabOrdersByPatient(patientId)
ECSA --> MRS: PatientSummaryResponse
MRS --> MRC: PatientSummaryResponse
MRC --> Actor: 200 OK ApiResponse<PatientSummaryResponse>
```
