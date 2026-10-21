# Doctor Consultation Service Contract

> **Chuẩn hóa R0 — 19/10/2026:** Phạm vi bàn giao đầy đủ, ownership và phân biệt CURRENT/TARGET xem [bộ đặc tả chuẩn](../../.planning/architecture/README.md). Tài liệu này không chứng minh chức năng đã được nghiệm thu. Các đoạn MVP/mock cũ phải đọc theo giới hạn và thay đổi tại R0; không loại Analytics/AI khỏi yêu cầu cuối.


> Contract ID: `CF-SVC-08` | Version: `1.2` | Module: `careflow-consultation-service`

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


## E2E start authority — 2026-10-16

A login token is validated once per resource request through Common/Identity (RSA/session/DPoP), without a second HMAC parser. Creating a consultation requires an assignment-checked real Queue ticket matching appointment/patient/optional queueEntryId and status CALLED or IN_PROGRESS. A confirmed appointment or CHECKED_IN ticket alone cannot start clinical work. Queue/Appointment authority failure returns 503, assignment mismatch 403, wrong state 409; no consultation is saved. Reception walk-ins may have a real Queue ticket without an Appointment row; only an explicit Appointment 404 may use this verified Queue authority. Do not issue an invalid Appointment IN_PROGRESS update: Appointment remains CONFIRMED until the completion contract/event changes it to COMPLETED.

## Clinical responsibility and handover v1.2 — 2026-10-16

Same active department/room handover, including IN_PROGRESS/AWAITING_CLS/AWAITING_REVIEW/READY_TO_COMPLETE. A request does not close, restart or rebook the visit. Cross-room/specialty transfer is separate.

Response `doctorId`/`doctorName` identify the **current responsible doctor**. New `authoredByDoctorId`/`authoredByDoctorName` identify the original consultation author, whose stored doctor_id/name are unchanged. Existing consultations resolve both to their original doctor. Consumers authorize ongoing work against current doctorId; signatures/order/toa author snapshots remain original. Per-doctor active/today lists follow current responsibility.

| Method/path | Authorization and result |
|---|---|
| GET `/api/consultations/handovers` | Active DOCTOR: own requests/history, eligible same-room doctors and own open cases; explicitly granted coordinator additionally sees same-room case metadata for exceptional initiation and same-room pending handovers for cancellation |
| POST `/api/consultations/handovers` | Current doctor or granted same-room medical coordinator. Body: consultationId, toDoctorId, reason, situation, background, assessment, recommendation. Returns pending request, 200; identical pending submission is idempotent |
| POST `/api/consultations/handovers/{id}/accept` | Named receiver only, body `{note}` confirming understanding/acceptance; atomic responsibility change and audit, 200 |
| POST `/api/consultations/handovers/{id}/reject` | Named receiver only, required note; responsibility unchanged |
| POST `/api/consultations/handovers/{id}/cancel` | Sender, initiator, or explicitly granted active coordinator in the stored department/room; required note and actual actor retained; responsibility unchanged |
| GET/POST `/api/consultations/handovers/coordinators` | ADMIN only: list/grant explicit medical coordination capability for an active doctor; grant body `{doctorId,reason}`. Does not grant administrator clinical access |
| POST `/api/consultations/handovers/coordinators/{doctorId}/revoke` | ADMIN, required `{note}`; grant/revoke audit retained |
| GET `/api/consultations/handovers/coordinators/history` | ADMIN only, immutable permission decision history |
| GET `/api/consultations/handovers/patient-access/{patientId}` | DOCTOR only, minimal responsibility authority for Patient→Appointment delegation. Returns allowed and supersededAppointmentIds; no clinical fields. Prevents appointment's original-doctor snapshot from granting outdated access |

SBAR fields are required, max 4000 characters each; reason/decision note max 2000. Workspace provides the receiver the explicitly addressed summary, not general consultation write access before acceptance. ADMIN cannot send or accept a clinical handover. No coordinator is granted by default; administrator records hospital authorization with a reason. Exceptional initiation is marked and retains the real initiator, without impersonating the unavailable source doctor.

One pending request per consultation (partial unique index). Consultation row lock and optimistic version protect concurrent decisions/clinical updates; transactional per-doctor locks serialize new starts, resumes and acceptances. Receiver with another IN_PROGRESS case cannot accept an IN_PROGRESS case. Repeated same completed decision is idempotent; conflicting decisions, closed cases, changed owner/room, revoked coordinator or busy receiver return 409. Non-owner/incorrect receiver returns 403. Directory uncertainty returns 503; no authority bypass. Rejected/cancelled requests do not alter responsibility.

Queue is still room-scoped operational dispatch; calledBy remains historical caller. Handover does not change Queue order, number, room or status. Patient/EMR reads follow authoritative responsibility via Appointment; original booking remains historical. Completion by the receiver is allowed only after verifying current Consultation authority. Lab reads/review use current responsibility and preserve orderedBy. Another author's prescription draft cannot be edited/signed/cancelled by the receiver; create an own draft. Original author loses mutation rights after handover.

Persistence is created by Flyway V1 with baseline version 0 for the pre-existing Hibernate schema; existing data is not reset. Only idempotent workload support rows are initialized at startup; clinical handovers require actual API actions. Permissions/requests/decisions are durable DB records; no acceptance is fabricated by seeding.

Accepted handovers persist a server-only clinical snapshot at the time of acceptance so subsequent edits cannot erase the prior state. It is excluded from workspace/decision JSON. Patient name is a verified Appointment snapshot alongside the stable patient ID; legacy/walk-in records may lack this display name and must be identified by their actual record ID. Flyway V2/V3 add these fields without modifying applied V1. Appointment completion runs after the Consultation commit so its authority query sees the actual COMPLETED state; repeated completion is idempotent. This callback is not a durable cross-service outbox: if Appointment is unavailable after commit, the UI command can retry safely; general reconciliation/outbox delivery remains a separate hardening task.

Appointment's delegated caller needs `GET:/api/consultations/handovers/patient-access/**` and `GET:/api/consultations/appointment/**` in Identity's independent caller registry (both instances). These grant scoped provider queries only; end-user ownership is still checked.

The authoritative Queue ticket is checked when requesting and accepting: appointment/patient must match and its actual room must match the stored/requested assignment. Matching two doctor profiles alone is insufficient. Queue/Directory uncertainty fails closed; a rejected check leaves responsibility and the pending request unchanged.

### Live Lab guards and result review — 2026-10-17

Resume accepts AWAITING_CLS/AWAITING_REVIEW only after querying the real Lab API and verifying nonempty matching consultation/patient orders are RESULT_AVAILABLE/REVIEWED/CANCELLED. Complete requires REVIEWED/CANCELLED for every order; no-order consultations may finish. Pending results return 409, unavailable/mismatched authority returns 503. No Lab state is inferred from UI or forged event. Result review uses its own Queue entryId and the original consultationId.

Follow-up creation verifies current consultation responsibility and patient ownership through service calls. Appointment Service must have delegated GET:/api/consultations/** (plus patient/directory read scopes); this grants no bypass of user role/ownership. A receiving doctor may keep the follow-up when their live Directory profile is active and matches the same department/room, even when another doctor is the default for new bookings. Former owners are rejected 403.
