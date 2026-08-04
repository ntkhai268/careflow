# Doctor Consultation Service Contract

> Contract ID: `CF-SVC-08` | Version: `1.0` | Module: `careflow-consultation-service`

## 1. Trách nhiệm và dữ liệu sở hữu

Sở hữu một phiên khám:

- doctor, patient, appointment và queue entry liên quan;
- sinh hiệu do bác sĩ nhập;
- triệu chứng, khám thực thể, chẩn đoán và kết luận;
- trạng thái chờ cận lâm sàng/chờ đọc kết quả;
- liên kết đến lab orders, prescription và follow-up.

Theo quyết định MVP, bác sĩ nhập sinh hiệu trực tiếp trên Doctor Web để rút ngắn luồng; không có bước
điều dưỡng bắt buộc.

## 2. Trạng thái

```text
NOT_STARTED → IN_PROGRESS → COMPLETED

NOT_STARTED → IN_PROGRESS
→ WAITING_FOR_RESULTS
→ WAITING_FOR_REVIEW
→ IN_PROGRESS
→ COMPLETED
```

Chỉ doctor được phân công mới sửa consultation. `COMPLETED` là terminal; sửa sau hoàn tất cần một
amendment có lý do và audit, không overwrite im lặng.

## 3. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `POST /api/consultations` | Assigned `DOCTOR` | Tạo/bắt đầu từ queue entry đã được claim |
| `GET /api/consultations/{consultationId}` | Assigned doctor hoặc patient với view phù hợp | Chi tiết |
| `GET /api/consultations/patient/{patientId}` | Chính chủ/assigned doctor | Lịch sử |
| `PUT /api/consultations/{consultationId}/clinical-data` | Assigned `DOCTOR` | Lưu sinh hiệu/khám/chẩn đoán |
| `POST /api/consultations/{consultationId}/wait-for-results` | Assigned `DOCTOR` | Chuyển chờ kết quả |
| `POST /api/consultations/{consultationId}/resume` | Assigned `DOCTOR` | Tiếp tục khi result review bắt đầu |
| `POST /api/consultations/{consultationId}/complete` | Assigned `DOCTOR` | Kết luận lượt khám |
| `POST /api/consultations/{consultationId}/amendments` | Doctor + audit policy | Bổ sung sau hoàn tất |

Create/start request:

```json
{
  "queueEntryId": "6fb18911-a1e8-45d9-a53e-f5b207b8c588",
  "appointmentId": "cf367b19-b946-41dc-969b-0d7958075b22",
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1"
}
```

`doctorId` lấy từ trusted `X-User-Id`, không lấy từ request. Doctor Web gọi Queue
`POST /api/queues/entries/{entryId}/start` trước; sau đó `queueEntryId` phải ở `IN_PROGRESS` và được
claim cho đúng doctor. Create idempotent theo `queueEntryId`; nếu bước tạo consultation phải retry thì
không tạo phiên thứ hai.

Clinical data request:

```json
{
  "vitals": {
    "bloodPressure": "118/76",
    "pulse": 78,
    "temperature": 36.8,
    "respiratoryRate": 18,
    "heightCm": 160.0,
    "weightKg": 55.0
  },
  "chiefComplaint": "Đau đầu kéo dài",
  "symptoms": ["đau nửa đầu", "buồn nôn"],
  "physicalExamination": "Tỉnh, tiếp xúc tốt, không yếu liệt",
  "diagnoses": [
    {
      "code": "G43.0",
      "name": "Migraine without aura",
      "type": "PRIMARY"
    }
  ],
  "clinicalConclusion": "Theo dõi migraine"
}
```

Complete request:

```json
{
  "advice": "Ngủ đủ, tránh yếu tố khởi phát",
  "followUpRequired": true
}
```

Patient response không được lộ internal note hoặc AI prompt; chỉ trả phần đã ký/xác nhận.

## 4. Quan hệ với Lab và Prescription

- Doctor Web gọi `POST /api/labs/orders` để tạo chỉ định thuộc consultation.
- `LabOrderCreated` làm consultation sang `WAITING_FOR_RESULTS`.
- Khi đủ kết quả, Lab publish `AllRequiredResultsAvailable`; Consultation sang `WAITING_FOR_REVIEW`.
- Khi Queue bắt đầu entry `CONSULTATION` phase `RESULT_REVIEW`, Doctor gọi `/resume`.
- Doctor Web gọi Prescription API để tạo/xác nhận toa trước khi complete nếu có kê thuốc.
- Appointment API nhận yêu cầu follow-up sau kết luận.

Consultation không tự ghi vào database của Lab, Prescription hoặc Appointment.

## 5. Event

Exchange: `consultation.exchange`.

| Publish | Routing key |
|---|---|
| `ConsultationStarted` v1 | `consultation.started` |
| `ConsultationUpdated` v1 | `consultation.updated` |
| `ConsultationWaitingForResults` v1 | `consultation.waiting-results` |
| `ConsultationWaitingForReview` v1 | `consultation.waiting-review` |
| `ConsultationCompleted` v1 | `consultation.completed` |
| `ConsultationAmended` v1 | `consultation.amended` |

Consume:

- `QueueEntryStarted` để đối chiếu start;
- `LabOrderCreated`;
- `AllRequiredResultsAvailable`;
- `PrescriptionIssued`;
- `FollowUpScheduled`.

`ConsultationCompleted.payload` chỉ chứa summary cần thiết:

```json
{
  "consultationId": "35df361e-f4b3-4113-ad0d-0ed853fe61fc",
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "doctorId": "8c9153aa-7d79-489a-a98f-2b75dd33fb94",
  "appointmentId": "cf367b19-b946-41dc-969b-0d7958075b22",
  "primaryDiagnosisCode": "G43.0",
  "completedAt": "2026-08-18T05:10:00Z"
}
```

## 6. Mock cho frontend và dependency

- Doctor Web mock consultation không lab, đang chờ lab, chờ review và completed.
- Lab mock `consultationId` hợp lệ; Prescription mock assigned doctor/patient.
- Queue mock start success và `409` khi entry chưa CALLED.
- AI mock chỉ trả suggestion; clinical data chỉ thay đổi khi doctor chủ động lưu.

## 7. Definition of Done

### `CONTRACT_READY`

- State, clinical data, assignment, API và event đã chốt.
- Doctor Web/Lab/Prescription/AI có fixture dùng chung ID.

### `FUNCTIONAL_READY`

- Start, save, wait, resume, complete và amendment đúng transition.
- Migration và test validation/assignment/idempotency/concurrent update pass.
- Không complete khi còn required result chưa có.

### `INTEGRATION_READY`

- Queue start, Lab results, Prescription và follow-up kết nối bằng contract/event.
- EMR nhận được projection; event redelivery không lặp transition.
- Auth/audit chạy đúng qua Gateway.

### `DEMO_READY`

- Doctor nhập sinh hiệu/chẩn đoán trực tiếp.
- Chạy được cả nhánh không lab và có lab/result review.
- Patient chỉ xem dữ liệu đã hoàn tất; mọi sửa đổi sau hoàn tất có audit.
