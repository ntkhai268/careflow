# CareFlow Service Contracts

> Phiên bản chuẩn: `1.1`
>
> Ngày chốt: `2026-08-04`
>
> Phạm vi: MVP khám ngoại trú tại bệnh viện công
>
> Tài liệu nghiệp vụ nguồn: [`../patient-journey-and-system-workflow.md`](../patient-journey-and-system-workflow.md)

## 1. Mục đích

Thư mục này là nguồn sự thật chung để con người và AI biết:

- một service chịu trách nhiệm gì và không được làm gì;
- service sở hữu dữ liệu nào;
- frontend hoặc service khác gọi API nào;
- event nào được publish/consume;
- cách mock dependency chưa hoàn thành;
- bằng chứng nào phải có trước khi tuyên bố service đã xong.

Không tự suy đoán contract từ code của service khác. Nếu code và tài liệu này khác nhau, tạo issue để
chọn một trong hai: sửa code theo contract hoặc cập nhật contract có version và migration rõ ràng.

## 2. Cách đọc bắt buộc

Trước khi bắt đầu một service, thành viên hoặc AI phải đọc theo thứ tự:

1. File này.
2. [`../patient-journey-and-system-workflow.md`](../patient-journey-and-system-workflow.md).
3. Contract của service được giao.
4. Contract của tất cả provider trong cột **Phụ thuộc trực tiếp**.
5. Code, migration và test hiện có của chính service.

Khi giao việc cho AI, prompt tối thiểu phải chứa:

```text
Hãy đọc docs/service-contracts/README.md,
docs/patient-journey-and-system-workflow.md,
và docs/service-contracts/<service>.md.
Không thay đổi API, event, state hoặc ownership nếu chưa cập nhật contract.
Chỉ tuyên bố hoàn thành khi cung cấp đủ evidence cho gate được yêu cầu.
```

## 3. Các mức hoàn thành

| Mức | Ý nghĩa | Evidence tối thiểu |
|---|---|---|
| `BELOW_CONTRACT` | Chưa đủ thông tin để service khác tích hợp ổn định | Có thể chỉ là skeleton hoặc API chưa chốt |
| `CONTRACT_READY` | Consumer có thể code và mock mà không cần đọc implementation | OpenAPI/API table, JSON mẫu, event schema, state và error cases đã chốt |
| `FUNCTIONAL_READY` | Nghiệp vụ nội bộ chạy đúng độc lập | Migration, validation, state transition, unit/service test đều pass |
| `INTEGRATION_READY` | Tương tác thật với Gateway/RabbitMQ/dependency chạy đúng | Contract test, auth/ownership, idempotency, retry/DLQ hoặc failure handling |
| `DEMO_READY` | Có thể trình diễn trong hành trình end-to-end | Docker/local run, smoke test, log/correlation, demo scenario và tài liệu vận hành |

Chỉ `DEMO_READY` được ghi là **Done**. Không dùng phần trăm cảm tính như “service hoàn thành 80%”.

## 4. Quy ước HTTP chung

### 4.1. Điểm vào và định danh

- Client chỉ gọi qua API Gateway: `http://localhost:8080`.
- JSON dùng `camelCase`; ID dùng UUID; ngày dùng `YYYY-MM-DD`; thời gian dùng ISO-8601 có timezone.
- Gateway xóa các identity header do client tự gửi, xác thực JWT rồi gắn:
  - `X-User-Id`
  - `X-User-Role`
  - `X-Correlation-Id`
- Downstream phải kiểm tra role và ownership; không được tin `patientId`, `doctorId` do client gửi.
- Role mục tiêu của MVP: `PATIENT`, `DOCTOR`, `STAFF`, `LAB_TECHNICIAN`, `ADMIN`.
- Trong thời gian Identity chưa hỗ trợ `STAFF` và `LAB_TECHNICIAN`, tài khoản demo dùng `ADMIN`;
  không mở endpoint công khai để né kiểm tra quyền.

Với synchronous service-to-service query trong MVP:

- service gọi phải chuyển tiếp Bearer token gốc và `X-Correlation-Id`;
- request đi qua Gateway hoặc cơ chế service discovery nhưng provider vẫn authorize end-user;
- không được tự tạo `X-User-Id`/`X-User-Role` để giả làm Gateway;
- background flow không có end-user token phải dùng event, không mở internal API vô danh.

Service credential/mTLS là nâng cấp production; không được giả vờ đã có trong MVP.

### 4.2. Response envelope

Mọi JSON response sử dụng cấu trúc hiện có trong `careflow-common`:

```json
{
  "status": 200,
  "message": "Success",
  "data": {},
  "timestamp": "2026-07-30T04:00:00Z"
}
```

Quy tắc:

- `201` cho create thành công; `200` cho query/update/action; `204` không dùng vì envelope cần body.
- `400`: sai định dạng hoặc state transition.
- `401`: thiếu/sai JWT.
- `403`: đúng danh tính nhưng không đủ role/ownership.
- `404`: resource không tồn tại hoặc không được phép lộ sự tồn tại.
- `409`: trùng dữ liệu, hết capacity hoặc idempotency conflict.
- `413`/`415`: file quá lớn hoặc sai loại.
- `500`: lỗi ngoài dự kiến; không trả stack trace.

### 4.3. Idempotency

- Command tạo tài nguyên nhận `Idempotency-Key` khi có nguy cơ người dùng bấm lại.
- Cùng key và cùng request phải trả cùng kết quả.
- Cùng key nhưng payload khác phải trả `409`.
- Event consumer deduplicate theo `eventId`.

## 5. Quy ước event chung

Mọi event mới phải dùng `EventEnvelope` trong `careflow-common`:

```json
{
  "eventId": "5ba2b6eb-6cd1-4b36-8f43-6cf2cb9ab56d",
  "eventType": "AppointmentConfirmed",
  "eventVersion": 1,
  "aggregateId": "cf367b19-b946-41dc-969b-0d7958075b22",
  "aggregateVersion": 1,
  "occurredAt": "2026-07-30T04:00:00Z",
  "producer": "appointment-service",
  "correlationId": "916b5544-3084-4ae8-8249-fcbdc0f0dc9f",
  "payload": {}
}
```

Quy tắc:

- Command dùng HTTP đồng bộ; sự thật đã xảy ra dùng event.
- Producer publish bằng transactional outbox hoặc cơ chế chứng minh được không mất event.
- Consumer xử lý at-least-once, idempotent, retry hữu hạn và đưa lỗi cuối cùng vào DLQ.
- Không đưa JWT, ảnh, nội dung hồ sơ chi tiết hoặc dữ liệu nhạy cảm không cần thiết vào event.
- Thay đổi field bắt buộc hoặc ý nghĩa field phải tăng `eventVersion`.

## 6. Registry và dependency matrix

| # | Service contract | Module | Cung cấp cho | Phụ thuộc trực tiếp |
|---|---|---|---|---|
| 1 | [API Gateway](01-api-gateway.md) | `careflow-api-gateway` | Tất cả client | Identity, Eureka |
| 2 | [Identity & eKYC](02-identity-ekyc.md) | `careflow-identity-service` | Gateway, mọi service | Không |
| 3 | [Patient](03-patient.md) | `careflow-patient-service` | Mobile, Doctor Web, EMR | Identity |
| 4 | [Electronic Medical Record](04-electronic-medical-record.md) | `careflow-emr-service` | Doctor Web, Patient Mobile | Patient và các event lâm sàng |
| 5 | [Appointment](05-appointment.md) | `careflow-appointment-service` | Patient Mobile, Doctor/Staff Web, Queue | Patient |
| 6 | [Queue Management](06-queue-management.md) | `careflow-queue-service` | Mobile, Doctor/Staff/Lab/Pharmacy Web | Appointment, Consultation, Laboratory Order, Prescription |
| 7 | [Analytics](07-analytics.md) | `careflow-analytics-service` chưa tạo | Admin Web | Event từ các service |
| 8 | [Doctor Consultation](08-doctor-consultation.md) | `careflow-consultation-service` | Doctor Web, EMR | Queue, Patient, Laboratory Order, Prescription |
| 9 | [Prescription](09-prescription.md) | `careflow-prescription-service` | Doctor/Pharmacy Web, Patient Mobile, EMR, Queue | Consultation, Patient, Queue |
| 10 | [Laboratory Order](10-laboratory-order.md) | `careflow-lab-service` | Doctor/Lab Web, Mobile, EMR | Consultation, Patient, Queue |
| 11 | [Notification](11-notification.md) | `careflow-notification-service` | Mobile và Web | Event nghiệp vụ |
| 12 | [AI Clinical Assistant](12-ai-clinical-assistant.md) | `careflow-ai-service` | Doctor Web | EMR, Consultation |

`careflow-common` và `careflow-eureka-server` là platform modules, không phải bounded-context service.
Chúng vẫn phải build, có health check và không được chứa nghiệp vụ của 12 service trên.

### Ranh giới payment của MVP

MVP không có Payment microservice độc lập. Phí khám trên Patient Mobile được
trình bày bằng fixture `GENERAL_CONSULTATION`, bắt buộc thanh toán trực tuyến
mô phỏng và lưu receipt qua local adapter. Thanh toán tiền mặt không thuộc flow
đặt lịch; nếu cuối lượt phát sinh `amountDue`, `VisitSettlement` local/demo có
thể ghi nhận khoản thu tại quầy. Cuối lượt, `VisitSettlement` cộng phí khám,
cận lâm sàng và thuốc, đối trừ `prepaidAmount`, rồi trả `amountDue`, `refundDue`
và trạng thái quyết toán. Laboratory Order không có payment contract
riêng và order hợp lệ tạo `LAB_EXECUTION` ngay. Chỉ `PAYMENT_DUE` chặn dispense;
`REFUND_PENDING` không chặn. Phạm vi nghiệp vụ chỉ xét người bệnh tự chi trả.

## 7. Thứ tự triển khai khuyến nghị

```text
Gateway + Identity + Patient
→ Appointment
→ Queue phòng khám
→ Consultation
→ Laboratory Order + Queue cận lâm sàng
→ Prescription + Queue phát thuốc
→ EMR projection
→ Notification
→ Analytics
→ AI Clinical Assistant
```

Không chờ toàn hệ thống xong mới tích hợp. Ngay khi provider đạt `CONTRACT_READY`, consumer tạo mock
theo đúng response/event mẫu và viết consumer contract test.

## 8. Quy tắc mock

Mỗi consumer phải có fixture trong phạm vi test của mình:

```text
src/test/resources/contracts/<provider>/<operation>-success.json
src/test/resources/contracts/<provider>/<operation>-error.json
src/test/resources/contracts/<provider>/<event>-v1.json
```

Mock được chấp nhận khi:

- request path, method, header và body khớp contract;
- response khớp envelope và schema;
- có ít nhất success, validation error, unauthorized/forbidden và not-found/conflict phù hợp;
- event fixture có đầy đủ envelope;
- consumer test chứng minh xử lý duplicate event không tạo dữ liệu trùng.

Không mock bằng một object tự nghĩ ra trong test nếu contract đã có JSON mẫu.

## 9. Baseline quan sát từ repository

Đây là điểm xuất phát sau khi audit cả `develop` và toàn bộ remote branch ngày `2026-07-30`.
Chi tiết evidence, commit và kết quả build nằm tại
[`REMOTE-BRANCH-AUDIT-2026-07-30.md`](REMOTE-BRANCH-AUDIT-2026-07-30.md).

Hai cột dưới đây cố ý tách biệt:

- **Code đã có**: ghi nhận khối lượng implementation trên bất kỳ remote branch nào.
- **Gate theo contract mục tiêu**: mức có thể tích hợp vào hành trình nghiệp vụ vừa chốt.

| Service | Code đã có trên develop/remote | Gate theo contract mục tiêu và khoảng cách chính |
|---|---|---|
| API Gateway | Có route, JWT filter và test trên `develop` | Chưa đánh giá lại đầy đủ; thiếu Analytics route và role nhân viên/kỹ thuật viên |
| Identity & eKYC | Có auth, refresh token và eKYC mock trên `develop` | Chưa đánh giá lại đầy đủ; thiếu role mục tiêu và contract tích hợp |
| Patient | Có profile và patient-uploaded health record trên `develop` | Chưa đánh giá lại đầy đủ; cần ownership/auth test và event chuẩn |
| Appointment | Có CRUD/query; create tự trả `CONFIRMED`, lưu owner/phòng và phát `AppointmentConfirmed` v1 qua outbox | Chưa có bảng quản trị `ClinicRoom`, capacity concurrency-safe/idempotency/follow-up |
| Queue Management | Initial consultation chạy thật; domain ba loại queue, Round Robin ba làn, result-review tự active và pharmacy dispensing FIFO đã có consumer idempotent/API | Chưa có producer thật từ Lab/Prescription, `LAB_EXECUTION`, quản trị service point và authorization theo assignment |
| Consultation | Prototype lớn tại `aaf0716`: domain, API, state, RabbitMQ và Doctor Web; module build thành công | `BELOW_CONTRACT`: thiếu queue assignment/ownership, transition guard, Lab flow, envelope/outbox và test |
| Prescription | Prototype lớn tại `e1381f5`: domain thuốc/toa, API, RabbitMQ và Doctor Web; module build thành công | `BELOW_CONTRACT`: thiếu ownership, cancel/dispense/amendment, event chuẩn, migration và test |
| Laboratory Order | Chỉ có module skeleton trên mọi remote ref | `BELOW_CONTRACT`: chưa có domain/API/state/event/test |
| EMR | Prototype aggregation tại `7af5811`: domain, CRUD/summary API, Feign clients và Doctor Web; module build thành công | `BELOW_CONTRACT`: đang fan-out đồng bộ và ghi trực tiếp, thiếu projection/event/audit/ownership/test |
| Notification | WebSocket bridge tại `b7485c8`: JWT STOMP, Queue consumer, retry/DLQ; module build thành công | `BELOW_CONTRACT`: chưa có inbox persistence/API/template/dedup/test; offline sẽ mất thông báo |
| AI Clinical Assistant | Backend vẫn skeleton; nhánh Consultation có `AiAssistantWidget` giả lập bằng `setTimeout` | `BELOW_CONTRACT`: chưa có AI API/context/guardrail/evidence/audit/test |
| Analytics | Không có module hoặc implementation trên remote | `BELOW_CONTRACT`: cần tạo module, route, projection và dashboard API |

## 10. Definition of Done toàn cục

Ngoài checklist riêng của từng service, mọi service chỉ đạt `DEMO_READY` khi:

- `mvn test` tại root thành công;
- service khởi động bằng cấu hình local/Docker mà không cần sửa source;
- `/actuator/health` trả `UP`;
- migration chạy được từ database rỗng;
- Swagger/OpenAPI phản ánh đúng endpoint thực tế;
- log có `X-Correlation-Id` và không chứa token/dữ liệu bệnh án nhạy cảm;
- không có secret hard-code;
- API có test auth, role, ownership, validation và state transition;
- event có producer/consumer contract test, idempotency và failure path;
- mọi synchronous dependency có success/error fixture và timeout behavior;
- README của service có lệnh chạy, biến môi trường và smoke-test command;
- có link PR/commit, test output và demo evidence để reviewer kiểm tra.

## 11. Thay đổi contract

Một thay đổi contract chỉ được merge khi:

1. Nêu consumer bị ảnh hưởng.
2. Cập nhật file service contract và fixture.
3. Giữ backward compatibility hoặc tăng version.
4. Consumer owner xác nhận.
5. Có migration/deprecation plan nếu xóa field hoặc endpoint.

PR chỉ sửa implementation nhưng âm thầm đổi API/event được xem là chưa hoàn thành.
