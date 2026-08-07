# Queue Management Service Contract

> Contract ID: `CF-SVC-06` | Version: `1.3` | Module: `careflow-queue-service`

## 1. Trách nhiệm và nguyên tắc

Sở hữu:

- Visit Ticket, QR token và số thứ tự;
- check-in và active queue theo phòng/phiên;
- phân ba làn logic `PRIORITY`, `NORMAL`, `RESULT_REVIEW`, đề xuất theo Round
  Robin `1:1:1` và giữ FIFO trong từng làn;
- đề xuất lượt tiếp theo; bác sĩ có thể gọi lượt được đề xuất hoặc bất kỳ lượt
  `CHECKED_IN` nào trong phòng, sau đó recall, missed, bắt đầu và hoàn tất lượt;
- queue cận lâm sàng tự tạo từ order;
- tự động tạo và kích hoạt lượt khám có `consultationPhase=RESULT_REVIEW` ngay
  khi đủ kết quả bắt buộc;
- queue phát thuốc tự tạo từ toa đã xác nhận và vận hành FIFO tại điểm cấp phát.

Không quản lý slot/capacity lịch hẹn, chẩn đoán hoặc kết quả xét nghiệm. Không
trộn cấp cứu và không áp dụng thuật toán điều phối liên phòng/toàn bệnh viện.
Queue Service không sở hữu `Department` hoặc `ClinicRoom`; nó lưu `roomId`,
`department` và snapshot tên phòng từ `AppointmentConfirmed` để quản lý ticket,
Queue Entry và scheduler theo phòng.

## 2. Loại queue và trạng thái

```text
QueueType: CONSULTATION | LAB_EXECUTION | PHARMACY_DISPENSING
ConsultationPhase: INITIAL | RESULT_REVIEW
QueueClass: PRIORITY | NORMAL
SchedulingLane: PRIORITY | NORMAL | RESULT_REVIEW

CONSULTATION + INITIAL:
TICKET_ISSUED → CHECKED_IN → CALLED → IN_PROGRESS → COMPLETED
CALLED → MISSED
MISSED → CHECKED_IN
TICKET_ISSUED → CANCELLED | NO_SHOW

LAB_EXECUTION:
QUEUED → CALLED → IN_PROGRESS → COMPLETED
CALLED → MISSED → QUEUED

CONSULTATION + RESULT_REVIEW:
QUEUED → CALLED → IN_PROGRESS → COMPLETED
CALLED → MISSED → QUEUED

PHARMACY_DISPENSING:
QUEUED → CALLED → IN_PROGRESS → COMPLETED
CALLED → MISSED → QUEUED
```

`ConsultationPhase` chỉ áp dụng cho `CONSULTATION`; `QueueClass` chỉ áp dụng khi
phase là `INITIAL`. Làn điều phối tại phòng khám được suy ra như sau:

```text
CONSULTATION + INITIAL + PRIORITY → PRIORITY
CONSULTATION + INITIAL + NORMAL   → NORMAL
CONSULTATION + RESULT_REVIEW      → RESULT_REVIEW
```

`ARRIVED` và `READY` không tồn tại trong MVP; `CHECKED_IN` mang cả hai ý nghĩa
đối với lượt khám ban đầu. `SchedulingLane` là giá trị suy ra, không phải trạng
thái vòng đời và không yêu cầu ba bảng dữ liệu riêng. `LAB_EXECUTION` và
`PHARMACY_DISPENSING` vận hành FIFO tại từng `servicePointId`, không tham gia
Round Robin `1:1:1` của phòng khám.

## 3. Quy tắc xếp hàng

### Queue phòng khám

- Tạo ticket khi nhận `AppointmentConfirmed`, nhưng chưa vào active queue.
- `roomId` của ticket phải lấy từ event đã xác nhận; Queue Service không random,
  không tự phân phòng và không nhận phòng mới từ bệnh nhân.
- Số được cấp trước và giữ theo phòng/phiên.
- Active queue phòng khám chỉ chứa lượt `CONSULTATION + INITIAL` đã
  `CHECKED_IN` và lượt `CONSULTATION + RESULT_REVIEW` đang `QUEUED` sau khi đủ
  kết quả bắt buộc.
- Khi check-in, ghi `queuedAt`; FIFO trong từng `QueueClass` theo `queuedAt`, nếu
  trùng thì dùng `queueNumber` làm tiêu chí phụ.
- `QueueClass` mặc định là `NORMAL`. Chỉ nhân viên có quyền mới được xác nhận
  `PRIORITY` theo diện ưu tiên đã kiểm tra; phải lưu `priorityReasonCode` và audit,
  không nhận diện ưu tiên do bệnh nhân tự khai trực tiếp trong command.
- Số chưa check-in không chặn số sau đã check-in.
- Bệnh nhân đến trễ được đưa cuối làn tương ứng hoặc staff xử lý thủ công có audit.
- Doctor Web nhận ba làn và `recommendedNext`; mỗi entry đủ điều kiện trong
  active queue đều có thao tác **Gọi**. Việc đọc active queue không làm thay đổi
  scheduler.
- Khi cả ba làn có dữ liệu, đề xuất tuần tự
  `PRIORITY → NORMAL → RESULT_REVIEW → PRIORITY`. Làn rỗng được bỏ qua.
- Nếu chưa có `lastServedLane`, bắt đầu quét từ `PRIORITY`; các lần sau quét từ
  làn đứng ngay sau `lastServedLane` và chọn làn không rỗng đầu tiên.
- Queue Service lưu `lastServedLane` theo `roomId + sessionDate + sessionCode` và
  chỉ cập nhật nó sau một lần gọi thành công.
- Hệ thống không tự gọi bệnh nhân. Bác sĩ có thể gọi trực tiếp một entry bằng
  `call`, hoặc dùng `call-next` như lệnh gọi nhanh lượt đang được đề xuất.
- Một phòng MVP có một bác sĩ và một máy Doctor Web. Queue Service không chặn
  gọi chỉ vì phòng đã có lượt `CALLED` hoặc `IN_PROGRESS`; bác sĩ có quyền gọi
  thêm bệnh nhân vào chờ.

### Queue cận lâm sàng

- Tạo tự động khi `LabOrderReadyForExecution`.
- Không yêu cầu check-in thứ hai.
- Kỹ thuật viên gọi và đối chiếu danh tính trước khi bắt đầu.

### Queue đọc kết quả

- Tạo khi nhận `AllRequiredResultsAvailable`.
- Consultation chuyển sang `WAITING_FOR_REVIEW` và bệnh nhân được thông báo quay
  lại phòng khám.
- Queue tự động tạo và kích hoạt một entry mới có `type=CONSULTATION`,
  `consultationPhase=RESULT_REVIEW`, vẫn liên kết với `consultationId` cũ;
  `queuedAt` lấy theo thời điểm nhận sự kiện đủ kết quả.
- Bệnh nhân không phải dùng Mobile, xác nhận quay lại hoặc check-in lần nữa.
  Khu cận lâm sàng hướng dẫn bệnh nhân quay lại phòng khám; thông báo Mobile chỉ
  là kênh hỗ trợ.
- Lượt tham gia làn `RESULT_REVIEW`, giữ FIFO riêng và được gọi theo Round Robin
  `1:1:1` cùng hai làn khám ban đầu.
- Nếu bị missed, chỉ xếp lại cuối làn `RESULT_REVIEW` theo chính sách.

### Queue phát thuốc

- Tạo tự động khi nhận `PrescriptionIssued` cho toa đã `CONFIRMED`.
- Mỗi toa chỉ có tối đa một lượt `PHARMACY_DISPENSING` đang hoạt động.
- Lượt gắn với `prescriptionId`, `patientId` và `servicePointId` của điểm cấp phát;
  MVP cấu hình một điểm cấp phát mặc định nhưng mô hình hỗ trợ nhiều quầy.
- Bệnh nhân không check-in lại. Nhân viên cấp phát thuốc gọi FIFO theo `queuedAt`,
  đối chiếu danh tính, bắt đầu phục vụ rồi thực hiện lệnh phát thuốc.
- Khi Prescription Service phát `PrescriptionDispensed`, Queue Service hoàn tất
  entry tương ứng. Queue Service không sở hữu tồn kho hoặc tự chuyển trạng thái toa.
- Toa bị hủy trước khi phát thuốc làm lượt chưa hoàn tất chuyển `CANCELLED`.

## 4. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `GET /api/queues/tickets/appointment/{appointmentId}` | Chính chủ/Staff | Xem phiếu, QR, số |
| `POST /api/queues/check-in` | `STAFF`, `ADMIN` | Quét QR và tiếp nhận |
| `GET /api/queues/patients/{patientId}/current` | Chính chủ/clinical staff | Lượt hiện tại của bệnh nhân |
| `GET /api/queues/rooms/{roomId}/active?date=&session=` | `DOCTOR`, `STAFF`, `ADMIN` | Active queue phòng; backend kiểm tra phạm vi phòng của actor |
| `GET /api/queues/service-points/{servicePointId}/active?date=` | Staff được phân công, `ADMIN` | Active queue cận lâm sàng hoặc phát thuốc tại điểm phục vụ |
| `POST /api/queues/entries/{entryId}/call` | Actor được phân công | Gọi đúng lượt đủ điều kiện do người phục vụ chọn |
| `POST /api/queues/rooms/{roomId}/call-next` | `DOCTOR` | Lệnh gọi nhanh lượt được server đề xuất tại phòng khám |
| `POST /api/queues/service-points/{servicePointId}/call-next` | Staff được phân công | Gọi nhanh đầu queue FIFO tại điểm phục vụ |
| `POST /api/queues/entries/{entryId}/recall` | `DOCTOR`, `STAFF` | Gọi lại lượt đang CALLED |
| `POST /api/queues/entries/{entryId}/miss` | `DOCTOR`, `STAFF`, `LAB_TECHNICIAN` | Đánh dấu vắng |
| `POST /api/queues/entries/{entryId}/requeue` | Staff phù hợp | Đưa lại hàng |
| `POST /api/queues/entries/{entryId}/start` | Actor được phân công | Bắt đầu phục vụ |
| `POST /api/queues/entries/{entryId}/complete` | Actor được phân công | Hoàn tất lượt; queue phát thuốc hoàn tất theo event `PrescriptionDispensed` |

Check-in request:

```json
{
  "qrToken": "eyJ0aWNrZXRJZCI6IlBLLTIwMjYtMDAxMjUifQ.signed",
  "roomId": "ROOM-21",
  "queueClass": "PRIORITY",
  "priorityReasonCode": "ELDERLY"
}
```

`queueClass` và `priorityReasonCode` chỉ được chấp nhận từ actor có quyền; nếu
không gửi, Queue Service dùng `NORMAL`.

`roomId` trong check-in phải trùng phòng đã gắn với Visit Ticket. Giá trị trên
request chỉ dùng để đối chiếu điểm tiếp nhận, không cho phép chuyển ticket sang
phòng khác.

Queue là nguồn assignment vận hành cho Staff: request đọc hoặc mutate queue phải
được giới hạn ở đúng room/service point mà actor đang xử lý. Với queue khám,
Queue Service xác minh room của actor qua Hospital Directory ở các endpoint
room dashboard, call-next và các thao tác trên entry (`call`, `recall`, `miss`,
`requeue`, `start`, `complete`): Doctor phải là doctor active được phân công vào
room; Staff phải có bản ghi `staff_assignments` active cho room; Admin được
phép trong scope quản trị. Không được suy ra quyền chỉ từ `X-User-Role` hoặc
`roomId` do client gửi. Queue response chỉ nên cung cấp patient identifier và
dữ liệu vận hành cần thiết; Staff không dùng được endpoint Patient profile đầy
đủ để vượt qua scope này.

Hospital Directory cung cấp endpoint kiểm tra staff room scope:
`GET /api/directory/staff/{userId}/room-access?roomId={roomId}`. Assignment
doctor được kiểm tra bằng `GET /api/directory/doctors/{userId}`. Nếu Directory
không sẵn sàng hoặc không xác nhận assignment, request bị từ chối (fail-closed).

Staff room assignment được ADMIN quản trị qua Hospital Directory:

- `GET /api/directory/staff/assignments?userId=&roomId=` — xem assignment;
- `POST /api/directory/staff/assignments` — tạo assignment;
- `PUT /api/directory/staff/assignments/{id}` — đổi room/department hoặc
  revoke bằng `isActive=false`.

Request quản trị phải có `X-User-Role=ADMIN`. Directory kiểm tra department và
room active, đồng thời room phải thuộc đúng department trước khi lưu.

Ticket response `data`:

```json
{
  "ticketId": "b0881e06-f2e3-402d-bd19-63410367ac3e",
  "ticketCode": "PK-2026-00125",
  "appointmentId": "cf367b19-b946-41dc-969b-0d7958075b22",
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "queueNumber": 47,
  "department": "NEUROLOGY",
  "roomId": "ROOM-21",
  "roomDisplayName": "Phòng 21 - Lầu 1 khu A",
  "timeSlot": "10:30-11:30",
  "qrToken": "<opaque-or-signed-token>",
  "status": "TICKET_ISSUED"
}
```

Active queue item:

```json
{
  "entryId": "6fb18911-a1e8-45d9-a53e-f5b207b8c588",
  "type": "CONSULTATION",
  "consultationPhase": "INITIAL",
  "queueClass": "PRIORITY",
  "schedulingLane": "PRIORITY",
  "queueNumber": 47,
  "status": "CHECKED_IN",
  "checkedInAt": "2026-08-18T03:20:00Z",
  "position": 1
}
```

Active queue response `data`:

```json
{
  "roomId": "ROOM-21",
  "sessionDate": "2026-08-18",
  "sessionCode": "MORNING",
  "priorityQueue": [],
  "normalQueue": [],
  "resultReviewQueue": [],
  "recommendedNext": {
    "entryId": "6fb18911-a1e8-45d9-a53e-f5b207b8c588",
    "schedulingLane": "PRIORITY"
  },
  "lastServedLane": "RESULT_REVIEW",
  "schedulerVersion": 12
}
```

`recommendedNext` chỉ là snapshot để hiển thị và không giới hạn quyền chọn của
bác sĩ. `call-next` không nhận `entryId`; server tính lại gợi ý và chuyển lượt đó
sang `CALLED`. Khi bác sĩ bấm nút **Gọi** tại một hàng, Doctor Web dùng
`POST /entries/{entryId}/call` và chính entry được chọn được chuyển sang `CALLED`.

Entry `CONSULTATION` có phase `RESULT_REVIEW` luôn được điều phối trong làn cùng
tên; `QueueClass` trước đó của bệnh nhân không tạo thêm làn thứ tư.

Màn hình công cộng chỉ được nhận `queueNumber`, `roomDisplayName`, `status`; không nhận tên,
patientId hoặc dữ liệu bệnh án.

## 5. Event

Exchange: `queue.exchange`.

Consume:

| Event | Hành động |
|---|---|
| `AppointmentConfirmed` | Tạo ticket `TICKET_ISSUED` idempotent |
| `AppointmentCancelled/NoShow` | Vô hiệu ticket chưa phục vụ |
| `LabOrderReadyForExecution` | Tạo `LAB_EXECUTION` ở trạng thái `QUEUED` |
| `AllRequiredResultsAvailable` | Dùng `sourceQueueEntryId`/projection consultation để giữ đúng phòng, tạo ngay entry `CONSULTATION + RESULT_REVIEW` ở trạng thái `QUEUED`, dùng `occurredAt` làm `queuedAt` và thông báo bệnh nhân quay lại |
| `PrescriptionIssued` | Tạo `PHARMACY_DISPENSING` ở trạng thái `QUEUED` tại `dispensingServicePointId` trong event |
| `PrescriptionCancelled` | Hủy lượt phát thuốc chưa hoàn tất |
| `PrescriptionDispensed` | Hoàn tất lượt phát thuốc tương ứng idempotent |

Publish:

| Event | Routing key |
|---|---|
| `VisitTicketIssued` v1 | `queue.visit-ticket.issued` |
| `PatientCheckedIn` v1 | `queue.checked-in` |
| `PatientCalled` v1 | `queue.called` |
| `QueueEntryMissed` v1 | `queue.missed` |
| `QueueEntryStarted` v1 | `queue.started` |
| `QueueEntryCompleted` v1 | `queue.completed` |
| `QueueNearTurn` v1 | `queue.near-turn` |

`PatientCheckedIn.payload`:

```json
{
  "entryId": "6fb18911-a1e8-45d9-a53e-f5b207b8c588",
  "appointmentId": "cf367b19-b946-41dc-969b-0d7958075b22",
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "roomId": "ROOM-21",
  "queueNumber": 47,
  "checkedInAt": "2026-08-18T03:20:00Z"
}
```

## 6. Concurrency, idempotency và lỗi

- `call` khóa đúng Queue Entry được chọn; request lặp cùng `Idempotency-Key` trả
  kết quả cũ để chống double-click hoặc retry từ Doctor Web.
- `call-next` vẫn phải tính lại gợi ý trong transaction. Đây là tính nhất quán
  của command, không phải giả định nhiều bác sĩ cùng vận hành một phòng MVP.
- Với `DOCTOR`, Queue Service phải đối chiếu trusted user ID với clinical context
  do Appointment Service cung cấp hoặc projection phân công tương đương, rồi xác
  minh phòng trên URL thuộc phạm vi bác sĩ. Không chỉ tin `roomId` do Doctor Web
  truyền lên.
- Mọi lệnh gọi ghi `calledByUserId`, `calledAt`, tăng `callAttempts` và phát
  `PatientCalled`. Phòng có thể đồng thời có nhiều lượt `CALLED`/`IN_PROGRESS`.
- Một appointment chỉ có một ticket khám ban đầu; một lab order chỉ có một lab
  entry tại mỗi điểm thực hiện.
- Một consultation chỉ có tối đa một entry `CONSULTATION + RESULT_REVIEW` đang
  hoạt động; event `AllRequiredResultsAvailable` gửi lại không tạo entry trùng.
- Một prescription chỉ có tối đa một entry `PHARMACY_DISPENSING` đang hoạt động;
  event gửi lại không tạo lượt phát thuốc trùng.
- QR hết hạn/sai phòng/sai ngày trả `400` hoặc `409`; ticket bị hủy trả `409`.
- Action lặp lại cùng `Idempotency-Key` trả kết quả cũ.
- State transition sai trả `409`, không âm thầm bỏ qua.

Trong MVP, Doctor Web gọi `GET /api/appointments/clinical-context/me`, nhận khoa
và đúng một `roomId`, rồi gọi API `/rooms/{roomId}`. Mô hình và endpoint vẫn hỗ
trợ nhiều phòng; chỉ dữ liệu MVP đang cấu hình một phòng active mỗi khoa.

## 7. Mock cho consumer

- Appointment test publish fixture và assert một `VisitTicketIssued`.
- Doctor Web mock active queue có số 47 và 49 nhưng không có 48 chưa check-in.
- Lab Web mock `LAB_EXECUTION` đã tự vào queue, không có nút check-in.
- Pharmacy Web mock `PHARMACY_DISPENSING` FIFO tại một điểm cấp phát.
- Consultation mock `QueueEntryStarted`; Notification mock called/near-turn/missed.
- Test Round Robin: ba làn có P1, N1, R1 và `lastServedLane=RESULT_REVIEW` → ba
  lần gọi thành công tiếp theo là P1, N1, R1.
- Test bỏ qua làn rỗng: `NORMAL` rỗng → luân phiên `PRIORITY`, `RESULT_REVIEW`.
- Test `AllRequiredResultsAvailable` tự tạo đúng một result-review entry active;
  event gửi lại không tạo trùng.
- Test bác sĩ gọi một entry không phải `recommendedNext`.
- Test gọi thêm khi phòng đã có lượt `CALLED` hoặc `IN_PROGRESS`.
- Test double-click cùng `Idempotency-Key` không phát `PatientCalled` hai lần.

## 8. Definition of Done

### Trạng thái triển khai 2026-08-03

- `CONSULTATION + INITIAL` đã nối thật từ `AppointmentConfirmed` đến Visit Ticket,
  QR, staff check-in, active queue, gọi theo entry/gợi ý, start/complete và Mobile production.
- Appointment producer và Queue producer đều dùng outbox; consumer Appointment
  của Queue có idempotency bằng `processed_events`.
- Domain và API đã tách `QueueType`, `ConsultationPhase`, `QueueClass` và
  `SchedulingLane`; scheduler phòng khám dùng Round Robin `1:1:1`, giữ FIFO
  trong từng làn và lưu `lastServedLane`.
- Queue đã consume idempotent các event Prescription, tự tạo
  `PHARMACY_DISPENSING`, cung cấp dashboard/call-next FIFO theo `servicePointId`,
  xử lý cancel và chỉ hoàn tất khi nhận `PrescriptionDispensed`.
- Queue đã consume idempotent `AllRequiredResultsAvailable`, tự tạo
  `CONSULTATION + RESULT_REVIEW` active theo `occurredAt`, giữ đúng phòng khám và
  chống trùng theo `consultationId`; lượt missed luôn xếp lại cuối làn.
- Producer thật của Prescription/Lab Service, `LAB_EXECUTION`, quản trị
  `ClinicRoom` và xác minh assignment theo Hospital Directory vẫn là các slice
  tiếp theo.

### `CONTRACT_READY`

- Chốt ba loại queue, hai ConsultationPhase, hai QueueClass, ba SchedulingLane,
  state machine, QR, API,
  event và Round Robin `1:1:1`.
- Các UI có fixture cho ticket, active queue phòng khám, lab queue, result review
  và pharmacy queue.

### `FUNCTIONAL_READY`

- Migration/domain/API chạy; QR không chứa PII.
- FIFO trong từng làn, late arrival, missed/requeue và Round Robin có
  deterministic tests.
- Concurrency test chứng minh không double-call/double-ticket.
- Authorization test chứng minh bác sĩ không đọc hoặc gọi queue của phòng ngoài
  clinical context được phân công.

### `INTEGRATION_READY`

- Consume event thật từ Appointment/Lab/Prescription; publish envelope cho
  Notification/Consultation/Analytics.
- Redelivery không tạo entry trùng.
- Auth/room assignment/ownership chạy qua Gateway.

### `DEMO_READY`

- Appointment confirmed → có phiếu số 47.
- Trước check-in số 47 không có trong active queue; sau check-in xuất hiện đúng vị trí.
- Lab order tự vào hàng không check-in lại.
- Result review tự active khi đủ kết quả và tham gia Round Robin `1:1:1` tại
  phòng khám; nếu bệnh nhân chưa có mặt thì dùng `MISSED/requeue`.
- Toa đã xác nhận tự tạo lượt phát thuốc; nhân viên gọi FIFO và phát thuốc làm
  Queue Entry hoàn tất mà không yêu cầu hệ thống kho.
