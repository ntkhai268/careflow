# Remote Branch Audit — 2026-07-30

> Phạm vi: Queue, Notification, Consultation, Prescription, EMR, Laboratory Order,
> AI Clinical Assistant và Analytics

## 1. Vì sao có báo cáo này?

Baseline ban đầu chỉ đọc code trên `develop`, nên đã gọi nhầm Queue, Notification, Consultation,
Prescription và EMR là “skeleton”. Audit này sửa nhận định đó bằng evidence từ toàn bộ remote refs.

Một service có nhiều code chưa đồng nghĩa đã hoàn thành theo contract mới. Báo cáo dùng hai trục:

1. **Độ sâu implementation:** skeleton, partial, substantial prototype hoặc implementation lớn.
2. **Gate contract:** `BELOW_CONTRACT` → `CONTRACT_READY` → `FUNCTIONAL_READY` →
   `INTEGRATION_READY` → `DEMO_READY`.

## 2. Phương pháp kiểm tra

- Fetch/prune toàn bộ remote và quét tất cả `refs/remotes/origin`.
- So sánh từng branch với `origin/develop` bằng commit graph và tree diff.
- Materialize năm branch thành detached worktree tạm, không chuyển/sửa `develop`.
- Chạy Maven module cùng dependency bằng:

```text
mvn -pl <service-module> -am test
```

- Đọc controller, domain, migration, messaging, security, test, frontend và planning docs.
- Đối chiếu từng service với contract 1.0 trong thư mục này.

## 3. Kết quả tổng hợp

| Service | Remote ref | Độ sâu implementation | Build/test evidence | Gate contract 1.0 |
|---|---|---|---|---|
| Queue | `origin/feature/dangkhoii/queue-algorithm@838f08f` | Implementation lớn | `BUILD SUCCESS`; 24 test, 0 failure | `BELOW_CONTRACT` |
| Notification | `origin/feature/dangkhoii/notification-websocket@b7485c8` | Partial WebSocket bridge | `BUILD SUCCESS`; không có test service-local | `BELOW_CONTRACT` |
| Consultation | `origin/feature/vi/consultation-service@aaf0716` | Substantial prototype | `BUILD SUCCESS`; không có test service-local | `BELOW_CONTRACT` |
| Prescription | `origin/feature/vi/prescription-service@e1381f5` | Substantial prototype | `BUILD SUCCESS`; không có test service-local | `BELOW_CONTRACT` |
| EMR | `origin/feature/vi/emr-service@7af5811` | Substantial aggregation prototype | `BUILD SUCCESS`; không có test service-local | `BELOW_CONTRACT` |
| Laboratory Order | Không có feature ref chứa backend thực | Skeleton | Chỉ module wiring; không có test | `BELOW_CONTRACT` |
| AI Clinical Assistant | Không có feature ref chứa backend thực | Backend skeleton; UI mock | Không có test backend/contract | `BELOW_CONTRACT` |
| Analytics | Không có module/ref | Chưa bắt đầu | Không có target để build | `BELOW_CONTRACT` |

Gate `BELOW_CONTRACT` không có nghĩa “không làm gì”. Nó có nghĩa consumer chưa thể dựa vào contract
1.0 mà tích hợp an toàn.

## 4. Queue Management

### Code có thể tái sử dụng

Branch `838f08f` có:

- `QueueEntry`, `QueueConfig`, sequence, idempotency record, processed event và outbox domain;
- ba Flyway migration;
- QR token, check-in, call-next, recall, miss, requeue, start và complete;
- pessimistic locking, unique constraint và idempotency key;
- appointment consumer, transactional outbox, publisher confirm/retry/dead handling;
- 24 test cho consumer, outbox, QR, service và scheduler.

### Lệch contract

- Scheduler chia `EMERGENCY`, `PRIORITY`, `APPOINTMENT`, `WALK_IN` và chạy tỷ lệ N:M.
- Contract mới yêu cầu FIFO theo phòng/phiên, không trộn cấp cứu/priority.
- Chưa có `INITIAL_CONSULTATION`, `LAB_EXECUTION`, `RESULT_REVIEW`.
- Chưa consume `LabOrderReadyForExecution` hoặc `AllRequiredResultsAvailable`.
- Chưa có rule chèn result review sau một initial consultation kế tiếp.
- API và event name đang theo contract cũ.

### Hướng xử lý

Giữ migration/outbox/idempotency/locking/QR/test harness; thay scheduling core, state/type, route/event
và bổ sung Lab/result-review flow. Không nên viết lại toàn service từ đầu.

## 5. Notification

### Code có thể tái sử dụng

Branch `b7485c8` có:

- STOMP WebSocket `/ws`;
- xác thực JWT lúc CONNECT;
- Queue event consumer;
- gửi theo user destination và department topic;
- Rabbit retry/DLQ topology và hướng dẫn STOMP.

### Lệch contract

- Chỉ có `GET /api/notifications/info`; không có inbox/read/unread/preferences API.
- Không có database, migration, repository hoặc delivery state.
- Offline user mất thông báo.
- Chỉ relay Queue event; chưa map Appointment/Lab/Prescription.
- Không deduplicate, không publish delivered/read/failed event và không có test.

### Hướng xử lý

Giữ JWT/STOMP/Rabbit topology; bổ sung inbox persistence, template mapper, dedup, REST API, delivery
event và test trước khi tích hợp Mobile.

## 6. Consultation

### Code có thể tái sử dụng

Branch `aaf0716` có:

- JPA domain cho consultation và state cận lâm sàng;
- API create/get/update/status/complete và query theo patient/doctor/appointment;
- Feign client đến Appointment;
- publish completion event;
- Doctor Web cho queue/clinical/prescription/allergy/history;
- structured allergy/safety UI.

### Lệch contract

- Tin `doctorId` từ request, không authorize trusted user hoặc ownership.
- Không có `queueEntryId`, Queue claim hoặc `QueueEntryStarted`.
- State update gán thẳng, thiếu transition guard.
- Chưa tích hợp Lab/result readiness.
- Event không dùng `EventEnvelope`/outbox và exchange producer-consumer không khớp.
- Không migration, service-local test hoặc contract fixture.

### Hướng xử lý

Giữ domain/UI/API code phù hợp; thêm queue assignment, auth/ownership, state machine, Lab events,
outbox/envelope, migration và test.

## 7. Prescription

### Code có thể tái sử dụng

Branch `e1381f5` có:

- `Prescription`, `PrescriptionItem`, `Drug` và drug dictionary seed;
- create/get/update/confirm/query/catalog API;
- khóa sửa draft sau confirm;
- Rabbit publish và Doctor Web tích hợp.

### Lệch contract

- Tin doctor/patient từ body; patient query có thể thấy draft của patient bất kỳ.
- Consultation validation fail-open khi dependency lỗi.
- Thiếu cancel, dispense, amendment/replacement và optimistic concurrency.
- Confirm chưa idempotent; event raw `prescription.created`, không outbox.
- Không migration, test hoặc patient-mobile view.

### Hướng xử lý

Giữ model/catalog/UI; khóa ownership, chuyển dependency sang fail-closed, hoàn thiện state/API,
event/outbox/migration/test.

## 8. EMR

### Code có thể tái sử dụng

Branch `7af5811` có:

- `MedicalRecord` domain và create/get/update/summary API;
- Feign clients tới Patient, Consultation và Prescription;
- Doctor Web gọi summary.

### Lệch contract

- Là synchronous fan-out aggregator, chưa phải event projection.
- `GET summary` có side effect tự tạo record.
- Không lấy Lab/Appointment, thiếu timeline/source reference.
- Không ownership/access grant/audit.
- Không migration, event consumer, checkpoint/dedup hoặc test.

### Hướng xử lý

Có thể giữ response mapper/UI làm bước chuyển tiếp, nhưng domain chính phải chuyển thành projection
theo event và bổ sung audit/access grant. Không để EMR sửa dữ liệu nguồn.

## 9. Laboratory Order, AI và Analytics

### Laboratory Order

Toàn bộ remote refs chỉ có `LabApplication`, POM/config/Dockerfile và `.gitkeep`. Gateway/Compose/DB
wiring không phải implementation nghiệp vụ.

### AI Clinical Assistant

Backend chỉ có `AiApplication` và wiring. Nhánh Consultation/EMR có
`frontend/doctor-web/src/components/AiAssistantWidget.tsx`, nhưng widget dùng `setTimeout` để trả
câu cố định, không gọi `/api/ai`; browser `CustomEvent` cũng không phải AI event backend.

### Analytics

Không có `careflow-analytics-service`, Maven module, Gateway route, database, backend API hoặc Admin
dashboard trên bất kỳ remote ref nào.

## 10. Quan hệ branch và rủi ro merge

Ba branch của nhóm lâm sàng không độc lập:

```text
feature/vi/prescription-service
→ là ancestor của feature/vi/consultation-service
→ là ancestor của feature/vi/emr-service
```

Không merge cả ba một cách mù quáng. Cần tạo integration branch từ `develop`, chọn strategy đưa code
vào theo service, giải quyết contract drift và chạy full reactor test sau mỗi bước.

Độ lệch với `origin/develop` tại thời điểm audit:

| Branch | Commit chỉ có ở develop | Commit chỉ có ở branch |
|---|---:|---:|
| Queue | 7 | 8 |
| Notification | 30 | 3 |
| Consultation | 1 | 14 |
| Prescription | 16 | 4 |
| EMR | 1 | 15 |

## 11. Thứ tự tiếp theo

1. Đưa contract 1.0 lên remote để branch owner cùng đọc.
2. Queue owner lập diff “giữ / bỏ / sửa” theo FIFO contract.
3. Tách hoặc rebase chuỗi Prescription → Consultation → EMR trên integration branch.
4. Hoàn thiện Notification inbox sau khi Queue event schema được chốt.
5. Xây Laboratory Order vì đây là dependency để hoàn tất consultation/result-review.
6. Hoàn thiện EMR projection sau khi event lâm sàng ổn định.
7. Làm Analytics và AI sau core end-to-end; AI UI hiện tại chỉ được ghi là mock.
