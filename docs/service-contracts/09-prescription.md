# Prescription Service Contract

> Contract ID: `CF-SVC-09` | Version: `1.0` | Module: `careflow-prescription-service`

## 1. Trách nhiệm và ranh giới

Sở hữu:

- toa thuốc và các dòng thuốc;
- trạng thái draft, xác nhận, hủy và phát thuốc trong phạm vi demo;
- cảnh báo validation cơ bản và chữ ký/xác nhận của bác sĩ;
- bản snapshot tên thuốc, hàm lượng, cách dùng tại thời điểm phát hành.

Không sở hữu kho dược, tồn kho, thanh toán hoặc quyết định chẩn đoán. MVP không tự động phát thuốc.

## 2. Trạng thái

```text
DRAFT → CONFIRMED → DISPENSED
DRAFT → CANCELLED
CONFIRMED → CANCELLED_BY_AMENDMENT
```

Patient chỉ xem toa `CONFIRMED` hoặc `DISPENSED`. Chỉ assigned doctor sửa `DRAFT`.

## 3. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `POST /api/prescriptions` | Assigned `DOCTOR` | Tạo draft |
| `GET /api/prescriptions/{prescriptionId}` | Assigned doctor/chính patient | Xem toa |
| `GET /api/prescriptions/patient/{patientId}` | Chính chủ/assigned doctor | Lịch sử toa |
| `PUT /api/prescriptions/{prescriptionId}` | Doctor sở hữu draft | Cập nhật thuốc/lời dặn |
| `POST /api/prescriptions/{prescriptionId}/confirm` | Doctor sở hữu draft | Xác nhận và phát hành |
| `POST /api/prescriptions/{prescriptionId}/cancel` | Doctor/Admin theo policy | Hủy có lý do |
| `POST /api/prescriptions/{prescriptionId}/dispense` | `STAFF`/`ADMIN` | Đánh dấu phát thuốc demo |

Create request:

```json
{
  "consultationId": "35df361e-f4b3-4113-ad0d-0ed853fe61fc",
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "items": [
    {
      "medicationCode": "PARA500",
      "medicationName": "Paracetamol",
      "strength": "500 mg",
      "dosage": "1 viên",
      "route": "Uống",
      "frequency": "2 lần/ngày",
      "durationDays": 3,
      "quantity": 6,
      "instruction": "Uống sau ăn khi đau"
    }
  ],
  "advice": "Không dùng quá liều ghi trên toa"
}
```

Server lấy `doctorId` từ trusted header và xác minh doctor được phân công consultation.

Response phải có `id`, `consultationId`, `patientId`, `doctorId`, `status`, `items`, `advice`,
`confirmedAt`, `dispensedAt`, `createdAt`, `updatedAt`.

## 4. Validation và an toàn

- Draft phải có ít nhất một item trước khi confirm.
- Quantity, duration và frequency không âm/rỗng.
- Patient/consultation phải khớp.
- Confirm là idempotent; sau confirm không sửa trực tiếp.
- Sửa toa đã confirm phải hủy bằng amendment và tạo toa mới liên kết `replacesPrescriptionId`.
- Cảnh báo dị ứng/interaction trong MVP là warning cho doctor; không được âm thầm thay đổi toa.

## 5. Event

Exchange: `prescription.exchange`.

| Publish | Routing key | Consumer |
|---|---|---|
| `PrescriptionIssued` v1 | `prescription.issued` | Consultation, EMR, Notification, Analytics |
| `PrescriptionCancelled` v1 | `prescription.cancelled` | EMR, Notification |
| `PrescriptionDispensed` v1 | `prescription.dispensed` | EMR, Analytics |

`PrescriptionIssued.payload`:

```json
{
  "prescriptionId": "a92c64f6-2bf6-4db0-97af-df03c54fe6af",
  "consultationId": "35df361e-f4b3-4113-ad0d-0ed853fe61fc",
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "doctorId": "8c9153aa-7d79-489a-a98f-2b75dd33fb94",
  "itemCount": 1,
  "issuedAt": "2026-08-18T05:05:00Z"
}
```

Chi tiết thuốc không cần đưa toàn bộ lên broker; EMR/Notification dùng ID để truy cập theo quyền hoặc
projection consumer được cấp đúng payload version.

## 6. Mock cho consumer

- Doctor Web mock draft rỗng, draft hợp lệ, validation error và confirmed.
- Mobile chỉ mock confirmed/dispensed, không hiển thị draft.
- Consultation mock `PrescriptionIssued`; Notification mock một thông báo có link đến toa.
- Fixture dị ứng phải trả warning rõ, không tự confirm.

## 7. Definition of Done

### `CONTRACT_READY`

- Item schema, state, visibility và event được chốt.
- Doctor Web/Mobile/Consultation có fixture thống nhất.

### `FUNCTIONAL_READY`

- Draft/update/confirm/cancel/dispense đúng transition.
- Test quantity, empty item, consultation mismatch, ownership và immutable confirmed prescription.
- Migration và concurrent confirm không tạo hai event.

### `INTEGRATION_READY`

- Xác minh consultation/doctor; EMR và Notification nhận event envelope idempotent.
- Patient chỉ đọc toa của mình qua Gateway.
- Audit đủ người xác nhận, thời điểm và amendment.

### `DEMO_READY`

- Doctor kê và xác nhận toa; Mobile nhận thông báo và xem đúng toa.
- Toa xuất hiện trong EMR.
- Demo cancel/replacement không làm mất lịch sử toa cũ.
