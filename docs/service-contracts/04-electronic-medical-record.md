# Electronic Medical Record Service Contract

> Contract ID: `CF-SVC-04` | Version: `1.0` | Module: `careflow-emr-service`

## 1. Trách nhiệm và ranh giới

EMR cung cấp góc nhìn đọc tổng hợp theo bệnh nhân:

- thông tin hành chính và hồ sơ bệnh nhân upload;
- các lần khám, sinh hiệu, triệu chứng, chẩn đoán;
- chỉ định và kết quả cận lâm sàng;
- toa thuốc và lịch tái khám;
- timeline có nguồn gốc và audit truy cập.

EMR là read model/projection, không phải nơi sửa dữ liệu nguồn. Sửa chẩn đoán ở Consultation, sửa
kết quả ở Laboratory Order, sửa toa ở Prescription; EMR nhận event mới để cập nhật projection.

## 2. Dữ liệu sở hữu

EMR chỉ sở hữu:

- projection đã chuẩn hóa theo `patientId`;
- checkpoint/idempotency của event consumer;
- audit log truy cập hồ sơ;
- metadata về nguồn: `sourceService`, `sourceAggregateId`, `sourceVersion`.

Không copy binary hồ sơ upload hoặc ảnh xét nghiệm; chỉ giữ URL/ID có kiểm soát.

## 3. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `GET /api/emr/patients/{patientId}/summary` | Chính chủ hoặc assigned doctor | Tóm tắt hiện tại |
| `GET /api/emr/patients/{patientId}/timeline?from=&to=&type=` | Như trên | Timeline theo thời gian |
| `GET /api/emr/patients/{patientId}/encounters/{consultationId}` | Như trên | Một lần khám đầy đủ |
| `GET /api/emr/patients/{patientId}/lab-results` | Như trên | Kết quả đã phát hành |
| `GET /api/emr/patients/{patientId}/prescriptions` | Như trên | Toa đã xác nhận |

Summary response `data`:

```json
{
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "profile": {
    "fullName": "Nguyễn Thị Quỳnh Thương",
    "dateOfBirth": "1992-02-11",
    "gender": "FEMALE",
    "insuranceNumber": "DN4790000047"
  },
  "allergies": ["PENICILLIN"],
  "activeProblems": ["Migraine"],
  "latestVitals": {
    "bloodPressure": "118/76",
    "pulse": 78,
    "temperature": 36.8,
    "recordedAt": "2026-07-30T03:30:00Z"
  },
  "latestConsultationId": "35df361e-f4b3-4113-ad0d-0ed853fe61fc",
  "lastProjectedAt": "2026-07-30T04:00:00Z"
}
```

Mỗi timeline item phải có:

```json
{
  "type": "LAB_RESULT",
  "occurredAt": "2026-07-30T04:00:00Z",
  "sourceService": "lab-service",
  "sourceAggregateId": "62210a5c-3081-48a8-82d8-e783864aa2ec",
  "title": "Công thức máu",
  "summary": "Kết quả đã có",
  "detailRef": "/api/labs/orders/62210a5c-3081-48a8-82d8-e783864aa2ec"
}
```

## 4. Event consume

EMR không cần gọi đồng bộ hàng loạt service cho mỗi màn hình. Nó consume:

| Event | Nguồn | Tác dụng |
|---|---|---|
| `PatientProfileCreated/Updated` | Patient | Cập nhật profile projection |
| `PatientUploadedRecordCreated/Deleted` | Patient | Cập nhật tài liệu ngoài |
| `ConsultationStarted/Updated/Completed` | Consultation | Cập nhật encounter |
| `LabOrderCreated`, `LabResultAvailable`, `LabResultCorrected` | Laboratory Order | Cập nhật chỉ định/kết quả |
| `PrescriptionIssued/Cancelled` | Prescription | Cập nhật toa |
| `FollowUpScheduled` | Appointment | Cập nhật lịch tái khám |

EMR cũng consume `ConsultationStarted/Completed` để duy trì access grant theo assigned doctor. Doctor
chưa bắt đầu consultation chỉ thấy thông tin tối thiểu từ Queue, chưa được mở toàn bộ EMR.

Event đến sai thứ tự được xử lý bằng `aggregateVersion`: bỏ version cũ, áp dụng version mới hơn.
Duplicate `eventId` không tạo timeline item trùng.

## 5. Authorization và audit

- Patient chỉ xem `patientId` map với `X-User-Id`.
- Doctor chỉ xem khi access-grant projection xác nhận consultation được phân công cho doctor đó.
- Staff và lab technician không được xem toàn bộ summary nếu không cần cho nhiệm vụ.
- Mỗi lần Doctor/Admin mở EMR ghi `actorUserId`, `role`, `patientId`, `purpose`,
  `correlationId`, `accessedAt`.
- Không log nội dung chẩn đoán, file hoặc token vào application log.

## 6. Mock cho frontend

- Mock `summary`, timeline rỗng, timeline nhiều nguồn và projection đang trễ.
- `lastProjectedAt` cho phép UI hiển thị “dữ liệu cập nhật lúc...”.
- Khi projection chưa nhận event vừa tạo, frontend có thể refetch hữu hạn; không tự ghép dữ liệu sai nguồn.
- Mock `403` cho doctor không được phân công và patient xem hồ sơ người khác.

## 7. Definition of Done

### `CONTRACT_READY`

- Chốt projection schema, source reference, event input và quyền truy cập.
- Doctor Web có fixture summary/timeline mà không cần service thật.

### `FUNCTIONAL_READY`

- Migration projection/audit chạy từ database rỗng.
- Consumer idempotent, xử lý event out-of-order theo aggregate version.
- Query summary/timeline/encounter đúng với projection fixture.

### `INTEGRATION_READY`

- Nhận event thật từ Patient, Consultation, Lab, Prescription, Appointment.
- Contract test chứng minh projection không trùng khi redelivery.
- Authorization assignment và audit log hoạt động qua Gateway.

### `DEMO_READY`

- Sau một lượt khám, Mobile và Doctor Web xem được timeline thống nhất.
- Có thể truy từ timeline về resource nguồn.
- Demo sửa/cancel nguồn làm projection cập nhật đúng, không sửa trực tiếp EMR.
