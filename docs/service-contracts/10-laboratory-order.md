# Laboratory Order Service Contract

> Contract ID: `CF-SVC-10` | Version: `1.0` | Module: `careflow-lab-service`

## 1. Trách nhiệm và ranh giới

Sở hữu:

- chỉ định cận lâm sàng và từng hạng mục;
- điểm thực hiện, yêu cầu chuẩn bị, trạng thái thực hiện;
- kết quả, khoảng tham chiếu, người nhập/xác nhận và lịch sử chỉnh sửa;
- trạng thái đủ điều kiện thực hiện sau thanh toán/BHYT mock.

Không sở hữu active queue; Queue Management tự tạo lượt từ order đủ điều kiện. Không yêu cầu bệnh
nhân check-in lần hai. Không lưu thông tin thẻ/ngân hàng.

## 2. Trạng thái

```text
ORDERED → QUEUED → CALLED → IN_PROGRESS → RESULT_AVAILABLE → REVIEWED

ORDERED → PAYMENT_PENDING → QUEUED
CALLED → MISSED → QUEUED
ORDERED | PAYMENT_PENDING → CANCELLED
```

`QUEUED/CALLED/MISSED` là trạng thái projection từ Queue; Lab Order vẫn là nguồn sự thật của
`ORDERED`, payment eligibility, thực hiện và kết quả.

Payment status MVP:

```text
NOT_REQUIRED | PENDING | PAID_ONLINE_MOCK | PAID_CASH | COVERED_BY_INSURANCE
```

## 3. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `POST /api/labs/orders` | Assigned `DOCTOR` | Tạo chỉ định |
| `GET /api/labs/orders/{orderId}` | Doctor/Lab tech/chính patient theo view | Chi tiết |
| `GET /api/labs/orders/consultation/{consultationId}` | Assigned doctor | Chỉ định của lần khám |
| `GET /api/labs/orders/patient/{patientId}` | Chính chủ/assigned clinical staff | Lịch sử |
| `POST /api/labs/orders/{orderId}/payment-confirmations` | Chính patient hoặc `STAFF` | Xác nhận phương thức MVP |
| `POST /api/labs/orders/{orderId}/start` | Assigned `LAB_TECHNICIAN` | Bắt đầu sau khi queue CALLED |
| `PUT /api/labs/orders/{orderId}/items/{itemId}/result` | Assigned lab tech | Nhập kết quả item |
| `POST /api/labs/orders/{orderId}/finalize` | Lab tech có quyền | Phát hành đủ kết quả |
| `POST /api/labs/orders/{orderId}/mark-reviewed` | Assigned `DOCTOR` | Xác nhận đã đọc |
| `POST /api/labs/orders/{orderId}/cancel` | Doctor/Staff theo policy | Hủy có lý do |

Trước `POST /api/labs/orders/{orderId}/start`, Lab Web gọi
`POST /api/queues/entries/{entryId}/start`; Lab Service chỉ chấp nhận khi entry tương ứng đã
`IN_PROGRESS` và được claim cho đúng kỹ thuật viên. Cả hai command đều idempotent để retry không tạo
hai lượt thực hiện.

Create request:

```json
{
  "consultationId": "35df361e-f4b3-4113-ad0d-0ed853fe61fc",
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "items": [
    {
      "serviceCode": "CBC",
      "serviceName": "Công thức máu",
      "servicePointId": "LAB-HEMATOLOGY-01",
      "required": true,
      "preparationInstruction": "Không cần nhịn ăn"
    }
  ],
  "clinicalNote": "Loại trừ thiếu máu",
  "paymentRequired": true
}
```

Server lấy `orderedByDoctorId` từ trusted header và xác minh consultation. Payment confirmation:

```json
{
  "method": "PAID_ONLINE_MOCK",
  "reference": "MOCK-PAY-20260818-00047"
}
```

Không nhận số thẻ/CVV. Với tiền mặt, `STAFF` xác nhận `PAID_CASH`; với BHYT demo,
`COVERED_BY_INSURANCE`.

Result request:

```json
{
  "value": "13.4",
  "unit": "g/dL",
  "referenceRange": "12.0-15.5",
  "flag": "NORMAL",
  "comment": null
}
```

Kết quả định lượng giữ cả giá trị gốc và field display; không dùng floating point cho tính toán y tế
nếu cần độ chính xác thập phân.

## 4. Tạo queue tự động

Khi order không cần thanh toán hoặc đã được xác nhận thanh toán/BHYT:

1. Lab publish `LabOrderReadyForExecution`.
2. Queue tạo một `LAB_EXECUTION` cho mỗi `servicePointId` cần đến.
3. Mobile hiển thị số/địa điểm từ Queue API.
4. Bệnh nhân đến khu đó và ngồi chờ; không check-in lại.
5. Lab technician gọi qua Queue, xác minh danh tính rồi start order.

Nếu một order có item ở nhiều service point, mỗi nhóm có queue entry riêng nhưng cùng `orderId`.

## 5. Event

Exchange: `lab.exchange`.

| Publish | Routing key | Consumer |
|---|---|---|
| `LabOrderCreated` v1 | `lab.order.created` | Consultation, EMR, Notification, Analytics |
| `LabOrderReadyForExecution` v1 | `lab.order.ready` | Queue, Notification |
| `LabOrderStarted` v1 | `lab.order.started` | Consultation, Analytics |
| `LabResultAvailable` v1 | `lab.result.available` | Consultation, EMR, Notification, Analytics |
| `AllRequiredResultsAvailable` v1 | `lab.results.all-required-available` | Consultation, Queue, Notification |
| `LabResultCorrected` v1 | `lab.result.corrected` | EMR, Consultation, Notification |
| `LabOrderCancelled` v1 | `lab.order.cancelled` | Queue, Consultation, EMR |

`LabOrderReadyForExecution.payload`:

```json
{
  "orderId": "62210a5c-3081-48a8-82d8-e783864aa2ec",
  "consultationId": "35df361e-f4b3-4113-ad0d-0ed853fe61fc",
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "servicePoints": [
    {
      "servicePointId": "LAB-HEMATOLOGY-01",
      "itemIds": ["bfeef8b1-f885-49cd-8de0-523369513c03"]
    }
  ]
}
```

`AllRequiredResultsAvailable` chỉ publish một lần khi tất cả item `required=true` có kết quả đã
finalize.

## 6. Authorization, audit và chỉnh kết quả

- Patient chỉ xem kết quả đã phát hành của mình.
- Lab technician chỉ xem thông tin cần để thực hiện order được phân công.
- Mọi create/start/result/finalize/correction lưu actor, timestamp và correlation ID.
- Không overwrite kết quả đã phát hành. Correction tạo version mới, lý do bắt buộc và phát event.
- Doctor không trực tiếp ghi kết quả; lab technician không sửa diagnosis.

## 7. Mock cho consumer

- Doctor Web mock order `PAYMENT_PENDING`, `QUEUED`, `IN_PROGRESS`, `RESULT_AVAILABLE`.
- Mobile mock online, cash và BHYT; cả ba chỉ là option MVP, không tích hợp cổng thật.
- Queue mock `LabOrderReadyForExecution`, kể cả hai service point.
- Consultation mock partial result và `AllRequiredResultsAvailable`.
- Test khẳng định không có endpoint check-in cận lâm sàng.

## 8. Definition of Done

### `CONTRACT_READY`

- Chốt order/item/payment state, result schema, event và quy tắc không check-in.
- Doctor/Lab/Mobile/Queue/Consultation có fixture chung.

### `FUNCTIONAL_READY`

- Create/payment/start/result/finalize/review/cancel/correction đúng transition.
- Migration và test required items, multi-service-point, authorization và immutable finalized result.
- `AllRequiredResultsAvailable` chỉ phát đúng một lần.

### `INTEGRATION_READY`

- Consultation hợp lệ tạo order; Queue tự tạo lượt; EMR/Notification nhận kết quả.
- Queue state được đối chiếu trước start.
- Event envelope/outbox/idempotent consumer và DLQ path được kiểm tra.

### `DEMO_READY`

- Doctor tạo chỉ định; patient chọn online mock/tiền mặt/BHYT.
- Lab queue xuất hiện tự động, không check-in lại.
- Technician nhập kết quả; patient quay lại review đúng rule và thấy kết quả sau phát hành.
