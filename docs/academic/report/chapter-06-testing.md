# CHƯƠNG 6. KIỂM THỬ VÀ ĐÁNH GIÁ

Chương 6 kiểm tra bản CareFlow được mô tả ở Chương 5 theo các yêu cầu đã phân
tích ở Chương 3 và thiết kế ở Chương 4. Mục tiêu không chỉ là xác nhận từng
chức năng hoạt động, mà còn kiểm tra tính nhất quán của trạng thái, sự phối hợp
giữa service, công bằng của ba lane queue, quyền truy cập và khả năng phục hồi
khi event hoặc kết nối Mobile bị gián đoạn.

## 6.1. Mục tiêu và phạm vi kiểm thử

### 6.1.1. Mục tiêu

- Xác nhận hành trình từ đặt lịch đến phát thuốc không bị đứt liên kết.
- Kiểm tra các trạng thái và chuyển trạng thái đúng điều kiện nghiệp vụ.
- Kiểm tra FIFO trong từng lane và Round Robin 1:1:1 giữa `PRIORITY`,
  `NORMAL`, `RESULT_REVIEW`.
- Kiểm tra event at-least-once không tạo dữ liệu trùng.
- Xác nhận người dùng chỉ đọc hoặc thay đổi dữ liệu thuộc phạm vi quyền của
  mình.
- Đánh giá các yêu cầu phi chức năng quan trọng: tin cậy, hiệu năng, bảo mật,
  realtime và khả năng bảo trì.

### 6.1.2. Phạm vi

Phạm vi kiểm thử gồm backend service, Gateway, RabbitMQ, PostgreSQL, Hospital
Web, Patient Mobile, Notification/WebSocket và các adapter payment demo. Phạm
vi nghiệp vụ chỉ xét người bệnh tự chi trả và không bao gồm cổng thanh toán/hoàn
tiền production, thiết bị xét nghiệm thật, PACS, kho thuốc thật và AI/Analytics;
các biên này được kiểm tra ở mức contract/boundary.

## 6.2. Môi trường và dữ liệu kiểm thử

Môi trường kiểm thử dùng Docker Compose với Java 21, Spring Boot, PostgreSQL,
RabbitMQ, Next.js và Flutter. Mỗi service chạy migration riêng; test profile
dùng database/schema cô lập và broker virtual host riêng. WebSocket được kiểm
tra bằng client Hospital Web và Patient Mobile giả lập.

Bộ dữ liệu có tối thiểu:

- hai bệnh nhân có hồ sơ hợp lệ, một bệnh nhân không có Mobile;
- một Hospital Geofence với tọa độ tâm, bán kính hợp lệ, tọa độ trong/ngoài vùng
  và trường hợp không cấp quyền vị trí;
- một khoa và một `ClinicRoom` active trong dữ liệu MVP, đồng thời có fixture
  thứ hai để kiểm tra quan hệ `Department 1:N ClinicRoom`;
- ba lane có nhiều entry, có cả lane rỗng và entry bị bỏ lỡ;
- một consultation có chỉ định nhiều item, kết quả một phần và kết quả đầy đủ;
- một toa có nhiều thuốc, một toa đã phát và một toa chưa phát;
- tài khoản bác sĩ, kỹ thuật viên, nhân viên tiếp nhận, nhân viên cấp phát và
  bộ phận quản lý với quyền khác nhau.

## 6.3. Kiểm thử đơn vị

Kiểm thử đơn vị chạy trong module tương ứng, không cần khởi động toàn bộ hệ
thống. Các quy tắc miền được kiểm tra bằng dữ liệu biên và trường hợp lỗi.

| Mã | Thành phần/quy tắc | Trường hợp kiểm thử tiêu biểu | Kết quả |
|---|---|---|---|
| UT-QUE-01 | FIFO từng lane | thêm entry với `queuedAt` khác nhau, lấy entry sớm nhất | Đạt |
| UT-QUE-02 | Round Robin | ba lane có dữ liệu, gọi liên tiếp 1:1:1 | Đạt |
| UT-QUE-03 | Bỏ qua lane rỗng | `RESULT_REVIEW` rỗng, thứ tự vẫn công bằng giữa hai lane còn lại | Đạt |
| UT-QUE-04 | Missed/requeue | bỏ lỡ rồi requeue, entry có thời điểm mới và audit cũ | Đạt |
| UT-QUE-05 | Idempotency | xử lý lại cùng `eventId`, không tạo entry thứ hai | Đạt |
| UT-APT-01 | Capacity | slot đầy, request tiếp theo bị từ chối rõ nguyên nhân | Đạt |
| UT-APT-02 | Phòng theo khoa | không cho đặt phòng thuộc khoa khác; MVP trả đúng phòng active | Đạt |
| UT-CON-01 | State consultation | chỉ phase hợp lệ mới được hoàn tất hoặc mở review | Đạt |
| UT-LAB-01 | Result finalization | thiếu item bắt buộc thì không phát hành toàn bộ kết quả | Đạt |
| UT-PRE-01 | Prescription | toa `CONFIRMED` không sửa âm thầm; correction có audit | Đạt |
| UT-MOB-01 | Prepayment adapter | online chuyển `PAID`, tiền mặt chuyển `DUE_AT_HOSPITAL`; chỉ khoản đã thu vào `prepaidAmount` | Đạt |
| UT-MOB-02 | Visit Settlement | tính `amountDue`/`refundDue` bằng `max(0, ...)`, không sinh số âm | Đạt |

## 6.4. Kiểm thử chức năng theo Use Case

### 6.4.1. Đặt lịch, chọn dịch vụ và check-in

| Mã | Nguồn | Tiền điều kiện | Dữ liệu vào/thao tác | Kết quả mong đợi | Kết quả thực tế | Trạng thái |
|---|---|---|---|---|---|---|
| FT-APT-01 | UC-APT-02 | hồ sơ và slot hợp lệ | `GENERAL_CONSULTATION`, `ONLINE_MOCK` | Appointment `CONFIRMED`, có `roomId`, receipt `PAID` local | Đúng như mong đợi | Đạt |
| FT-APT-02 | UC-APT-02 | hồ sơ và slot hợp lệ | không hiển thị lựa chọn tiền mặt khi đặt lịch | Booking chỉ có `ONLINE_MOCK`; receipt `PAID` local | Đúng như mong đợi | Đạt |
| FT-APT-03 | UC-APT-02/luồng lỗi | slot đã đủ capacity | gửi yêu cầu đặt lịch | không tạo lịch, hiển thị slot không khả dụng | Không có aggregate mới | Đạt |
| FT-QUE-01 | UC-QUE-02 | QR phòng/phiên hợp lệ, vị trí trong bán kính | bệnh nhân quét QR bằng Mobile và gửi tọa độ | ticket check-in đúng session và lane | Entry `CHECKED_IN`, có `PATIENT_QR_GEOFENCE` | Đạt |
| FT-QUE-02 | UC-QUE-02/luồng lỗi | QR hợp lệ nhưng vị trí ngoài bán kính | quét QR và gửi tọa độ ngoài geofence | từ chối check-in, ticket vẫn `TICKET_ISSUED` | Không tạo active entry | Đạt |
| FT-QUE-03 | UC-QUE-02/luồng lỗi | không cấp được vị trí hoặc độ chính xác không đạt | gửi request thiếu/không hợp lệ location | từ chối và hướng dẫn thử lại hoặc đến quầy | Không tạo active entry | Đạt |
| FT-QUE-04 | UC-QUE-02/luồng thay thế | bệnh nhân không có Mobile | nhân viên đối chiếu và xác nhận tại quầy | ticket vẫn được active bằng `STAFF_ASSISTED` | Active queue có đúng entry | Đạt |
| FT-QUE-05 | UC-QUE-02/luồng lỗi | ticket đã check-in | quét lại cùng QR | không tạo entry trùng, trả trạng thái hiện tại | Một entry duy nhất | Đạt |

### 6.4.2. Gọi lượt và hành trình khám

| Mã | Nguồn | Tiền điều kiện | Dữ liệu vào/thao tác | Kết quả mong đợi | Kết quả thực tế | Trạng thái |
|---|---|---|---|---|---|---|
| FT-QUE-04 | UC-QUE-04 | ba lane có dữ liệu | mở bảng phòng | ba lane và `recommendedNext` đúng Round Robin | Gợi ý đúng chu kỳ | Đạt |
| FT-QUE-05 | UC-QUE-04 | entry được gợi ý còn hợp lệ | bấm gọi entry | `CALLED`, con trỏ cập nhật, có audit/notification | Đủ trạng thái và side effect | Đạt |
| FT-QUE-06 | UC-QUE-04/luồng thay thế | entry khác đủ điều kiện | bác sĩ chọn entry khác gợi ý | entry được gọi, lane còn lại giữ FIFO | Không đổi thứ tự lane khác | Đạt |
| FT-CON-01 | UC-CON-01 | lượt đã gọi, bác sĩ đúng assignment | bắt đầu và hoàn tất khám | lưu note/chẩn đoán và state đúng phase | Consultation hợp lệ | Đạt |
| FT-LAB-01 | UC-LAB-01 | consultation đang hoạt động | tạo order và items | liên kết đúng consultation | Order/items được lưu đúng | Đạt |
| FT-LAB-02 | UC-LAB-03/luồng lỗi | thiếu item bắt buộc | bấm finalize | không phát `AllRequiredResultsAvailable` | Order chưa hoàn tất | Đạt |
| FT-LAB-03 | UC-LAB-03 | đủ kết quả bắt buộc | finalize order | tạo `RESULT_REVIEW` trong consultation cũ | Một entry review được tạo | Đạt |
| FT-LAB-04 | UC-LAB-01/chi phí | order và catalog hợp lệ | tạo order nhiều hạng mục | lưu đúng line amount, tạo ngay đúng một `LAB_EXECUTION`, không yêu cầu payment | Đúng chi phí và không tạo trùng | Đạt |
| FT-PRE-01 | UC-PRE-01 | consultation đủ kết luận | xác nhận toa | toa `CONFIRMED`, `PrescriptionIssued` một lần | Đúng event và state | Đạt |
| FT-PHA-01 | UC-PHA-01 | entry, toa hợp lệ và settlement `PAYMENT_DUE` | thử dispense, xác nhận thu đủ rồi thử lại | lần đầu bị chặn; sau `SETTLED` toa/entry hoàn tất | Chỉ lần sau tạo DispenseRecord | Đạt |
| FT-PHA-02 | UC-PHA-01/luồng lỗi | toa đã phát | gửi lại dispense | từ chối, giữ bản ghi cũ | Không có bản ghi thứ hai | Đạt |
| FT-PHA-03 | UC-PHA-01/hoàn tiền | `prepaidAmount > totalVisitCost` | tạo settlement rồi dispense | `amountDue = 0`, `refundDue > 0`, trạng thái `REFUND_PENDING` không chặn phát thuốc; sau xử lý chuyển `REFUNDED` | Đúng công thức và trạng thái | Đạt |

### 6.4.3. Notification và Mobile

| Mã | Nguồn | Tiền điều kiện | Dữ liệu vào/thao tác | Kết quả mong đợi | Kết quả thực tế | Trạng thái |
|---|---|---|---|---|---|---|
| FT-NOT-01 | FR-NOT/UC-QUE-04 | bệnh nhân có recipient hợp lệ | gọi lượt trên Hospital Web | đúng người nhận inbox/WebSocket | Đúng recipient | Đạt |
| FT-NOT-02 | FR-NOT | notification chưa đọc | đánh dấu đã đọc | unread giảm và lưu bền vững | Trạng thái được lưu | Đạt |
| FT-NOT-03 | NFR-Realtime | Mobile mất kết nối | reconnect và tải snapshot | không mất state nghiệp vụ | REST khôi phục snapshot | Đạt |
| FT-MOB-01 | FR-MOB/UC-LAB-03 | đã đủ kết quả | mở journey | hiển thị chờ `RESULT_REVIEW` | Đúng bước và hướng dẫn | Đạt |
| FT-MOB-02 | FR-MOB/UC-PHA-01 | lượt khám đã chốt chi phí, `DEMO_MODE` | mở quyết toán và hoàn tất dispense | hiển thị tổng, trả trước, còn phải trả/cần hoàn và trạng thái đúng | Không sửa ledger production | Đạt |

## 6.5. Kiểm thử API và tích hợp

### 6.5.1. API và phân quyền

Contract test kiểm tra schema request/response, mã lỗi và tên trạng thái theo
các tài liệu trong `docs/service-contracts`. Các trường hợp chính:

| Mã | Kiểm tra | Kết quả |
|---|---|---|
| API-01 | JWT hợp lệ gọi endpoint đúng role | Đạt |
| API-02 | JWT thiếu role hoặc hết hạn | `401/403`, không có side effect | Đạt |
| API-03 | Bác sĩ truy cập appointment khác phạm vi | Từ chối ownership/scope | Đạt |
| API-04 | `roomId` không thuộc khoa | `400/409`, không tạo queue | Đạt |
| API-05 | Request lặp có `Idempotency-Key` | Trả cùng kết quả, không tạo aggregate trùng | Đạt |

### 6.5.2. Tích hợp database và message broker

Integration test khởi động service thật với PostgreSQL và RabbitMQ test
container. Test xác nhận transaction aggregate và outbox cùng commit; khi
consumer bị dừng, message được giữ lại và xử lý sau khi consumer lên lại. Khi
message lỗi nhiều lần, message đi vào dead-letter queue và có thông tin
correlation để tra cứu.

Các chuỗi event được kiểm thử đầy đủ:

```text
AppointmentConfirmed
  → QueueEntryCreated
  → PatientCheckedIn
  → PatientCalled
  → ConsultationCompleted
  → ResultPublished
  → AllRequiredResultsAvailable
  → PrescriptionIssued
  → PrescriptionDispensed
```

Trong từng chuỗi, kiểm tra consumer restart, redelivery, event đến trễ và event
đến hai lần. Snapshot cuối cùng phải giống nhau với một lần xử lý thành công.

| Mã | Kịch bản tích hợp | Kết quả mong đợi | Kết quả |
|---|---|---|---|
| INT-01 | aggregate và outbox cùng transaction | cùng commit hoặc cùng rollback | Đạt |
| INT-02 | consumer tạm dừng rồi khởi động lại | message được xử lý sau khi phục hồi | Đạt |
| INT-03 | cùng `eventId` được giao hai lần | side effect chỉ xuất hiện một lần | Đạt |
| INT-04 | event đến trễ/khác thứ tự | state cuối vẫn hợp lệ hoặc event bị từ chối có audit | Đạt |
| INT-05 | lỗi vượt số lần retry | message vào dead-letter với correlation ID | Đạt |

## 6.6. Kiểm thử trạng thái và dữ liệu

### 6.6.1. State transition

Các state machine được kiểm tra theo bảng chuyển trạng thái trong Chương 3 và
Chương 4. Một transition hợp lệ phải có actor, điều kiện và thời điểm; transition
không hợp lệ bị từ chối mà không sửa dữ liệu. Trọng tâm gồm:

- Appointment: `CONFIRMED → FULFILLED`, `CONFIRMED → CANCELLED` hoặc
  `CONFIRMED → NO_SHOW`; check-in thuộc vòng đời Queue Entry;
- Queue Entry lượt khám ban đầu: `TICKET_ISSUED → CHECKED_IN → CALLED →
  IN_PROGRESS → COMPLETED`; `CALLED → MISSED → CHECKED_IN` khi requeue;
- Queue Entry tự động: `QUEUED → CALLED → IN_PROGRESS → COMPLETED`; `CALLED →
  MISSED → QUEUED` khi requeue;
- Consultation: `IN_PROGRESS → WAITING_FOR_RESULTS → WAITING_FOR_REVIEW →
  IN_PROGRESS → COMPLETED`;
- Laboratory Result: `DRAFT → FINAL`, correction có audit;
- Prescription: `DRAFT → CONFIRMED → DISPENSED`;
- Visit Settlement: `PAYMENT_DUE → SETTLED`, `SETTLED` hoặc
  `REFUND_PENDING → REFUNDED`; `amountDue` và `refundDue` không âm.

### 6.6.2. Đồng thời trong queue

Hai client bác sĩ gửi `call` cho cùng một entry gần như đồng thời. Chỉ một
request nhận `200` và chuyển entry sang `CALLED`; request còn lại nhận trạng
thái xung đột, không gửi notification thứ hai. Hai request gọi ở hai entry khác
nhau vẫn có thể xử lý song song nếu không tranh chấp cùng entry/session. Bộ test
cũng xác nhận cập nhật `lastServedLane` không bị mất khi có nhiều request tuần
tự.

Ca `CON-01` xác nhận chỉ một request gọi cùng entry thành công; request còn lại
nhận conflict và không tạo notification trùng.

### 6.6.3. Nhất quán dữ liệu

Sau mỗi kịch bản, kiểm tra các bất biến:

- Queue Entry luôn tham chiếu appointment và phòng hợp lệ.
- Mỗi lane có thứ tự FIFO theo `queuedAt`.
- `RESULT_REVIEW` tham chiếu consultation cũ, không tạo appointment giả.
- `PHARMACY_DISPENSING` chỉ xuất hiện sau `PrescriptionIssued`.
- Receipt local không làm thay đổi payment contract backend.
- Notification chỉ xuất hiện cho recipient đúng và không nhân đôi khi redelivery.

## 6.7. Kiểm thử yêu cầu phi chức năng

| Mã | Nhóm | Cách kiểm tra | Tiêu chí đạt | Kết quả |
|---|---|---|---|---|
| NFR-01 | Hiệu năng | tải đồng thời active queue/gọi lượt | phản hồi trong mục tiêu MVP, không mất entry | Đạt |
| NFR-02 | Độ tin cậy | dừng consumer, redelivery, restart | retry/idempotent, không mất state | Đạt |
| NFR-03 | Realtime | đóng/mở WebSocket | đẩy khi online, REST khôi phục khi offline | Đạt |
| NFR-04 | Bảo mật | role sai, thay patientId/roomId | từ chối và không lộ dữ liệu | Đạt |
| NFR-05 | Audit | tra lịch sử gọi/sửa/dispense | đủ actor, thời điểm, hành động, correlation | Đạt |
| NFR-06 | Phát triển | migration và build riêng service | migration an toàn, service build độc lập | Đạt |

Các ngưỡng đo chi tiết có thể điều chỉnh theo hạ tầng triển khai thật; trong
phạm vi đồ án, tiêu chí quan trọng là không sai trạng thái, không trùng dữ liệu
và không mất event.

## 6.8. Ma trận truy vết kiểm thử

| Yêu cầu | Thiết kế liên quan | Ca kiểm thử |
|---|---|---|
| FR-APT-01..04 | Appointment Service, capacity, Department–ClinicRoom | UT-APT-01/02, FT-APT-01..03 |
| FR-QUE-01..05 | Queue Session, ba lane, lock/version | UT-QUE-01..05, FT-QUE-04..06, CON-01 |
| FR-CON-01..03 | Consultation state machine, ownership | UT-CON-01, FT-CON-01, API-02/03 |
| FR-LAB-01..04 | Order/Result, line amount, tạo queue và finalization event | UT-LAB-01, FT-LAB-01..04 |
| FR-PRE-01..03 | Prescription aggregate, issued/dispensed event và quyết toán cuối lượt | UT-PRE-01, FT-PRE-01, FT-PHA-01..03 |
| FR-MOB-01..04 | Mobile journey và local adapters | UT-MOB-01/02, FT-MOB-01/02, FT-APT-01/02 |
| FR-NOT-01..03 | Inbox, WebSocket, recipient resolution | FT-NOT-01..03, INT-01 |
| QR-BUS-01..05 | JWT, ownership, QR phòng/phiên, geofence, outbox, idempotency, audit | API-01..05, NFR, CON-01 |

Ma trận cho phép truy ngược từ một yêu cầu đến thiết kế, ca kiểm thử và kết quả.
Nếu thay đổi một trạng thái hoặc contract, các ca liên quan phải được cập nhật
trước khi phát hành bản mới.

## 6.9. Tổng hợp kết quả

| Nhóm | Số ca | Đạt | Không đạt |
|---|---:|---:|---:|
| Đơn vị | 12 | 12 | 0 |
| Chức năng theo Use Case | 23 | 23 | 0 |
| API và phân quyền | 5 | 5 | 0 |
| Tích hợp/event | 5 | 5 | 0 |
| Đồng thời | 1 | 1 | 0 |
| Yêu cầu chất lượng | 6 | 6 | 0 |
| **Tổng** | **52** | **52** | **0** |

Trong phạm vi MVP, 52 ca được mô tả đều đạt. Không còn lỗi mở làm mất Queue
Entry, tạo dữ liệu trùng hoặc bỏ qua `RESULT_REVIEW`. Các lỗi phát hiện trong
quá trình xây dựng như gọi trùng do race condition, consumer tạo side effect
hai lần và Mobile tính sai khoản trả trước/quyết toán đã được khắc phục bằng
lock/version, idempotency và tách adapter. Phần chưa kiểm thử là các tích hợp đã
được xác định ngoài phạm vi: payment/refund production, PACS/LIS và kho thuốc.

Kết quả cho thấy thiết kế Microservices phù hợp khi các điểm phục vụ có nhịp độ
và dữ liệu riêng; Queue Service cung cấp quy tắc công bằng tập trung; event giúp
Appointment, Consultation, Laboratory, Prescription và Notification không phụ
thuộc trực tiếp vào database của nhau. Đổi lại, hệ thống cần outbox,
idempotency, observability và contract test để kiểm soát độ phức tạp phân tán.

## 6.10. Đánh giá phiên bản

| Nhóm yêu cầu | Mức đáp ứng | Đánh giá |
|---|---|---|
| Đặt lịch, capacity và phân phòng | Đáp ứng | lịch xác nhận, suy ra room theo khoa, không chọn ngẫu nhiên |
| Check-in và queue | Đáp ứng | QR phòng/phiên, kiểm tra geofence, hỗ trợ người không dùng Mobile, FIFO và Round Robin 1:1:1 |
| Khám, cận lâm sàng và review | Đáp ứng | cùng một hành trình, tự tạo `RESULT_REVIEW` khi đủ kết quả |
| Kê toa và phát thuốc | Đáp ứng | toa xác nhận tạo queue, đối chiếu và chống phát hai lần |
| Notification và truy vết | Đáp ứng MVP | inbox bền vững, WebSocket, audit và correlation |
| Thanh toán tự chi trả | Đáp ứng ở mức mô phỏng | phí khám trả trước và quyết toán cuối lượt đúng công thức/trạng thái; chưa phải production |

Ưu điểm của phiên bản là quy trình xuyên suốt, ranh giới dữ liệu rõ, queue công
bằng nhưng vẫn giữ quyết định gọi ở bác sĩ, và có cơ chế tin cậy cho event.
Hạn chế là chi phí vận hành Microservices cao hơn Monolithic; dữ liệu MVP mới
minh họa một phòng active/khoa; các hệ thống bệnh viện bên ngoài chưa được kết
nối thật.

Payment Mobile ở mức adapter demo đáp ứng mục tiêu minh họa trải nghiệm, nhưng
chưa phải thanh toán hoặc hoàn tiền production. Những hướng phát triển tiếp theo
gồm tích hợp cổng thanh toán/refund theo contract riêng, kết nối PACS/LIS, quản
lý kho thuốc, triển khai nhiều phòng thực tế, bổ sung báo cáo vận hành và cân
nhắc AI sau khi có dữ liệu đủ chất lượng.

## 6.11. Kết luận chương

Chương 6 đã kiểm thử CareFlow theo toàn bộ chuỗi từ đặt lịch, QR check-in và
geofence, điều
phối, khám, cận lâm sàng, review kết quả, kê toa đến phát thuốc. Kết quả được
truy vết về yêu cầu và thiết kế, đồng thời kiểm tra các rủi ro đặc thù của hệ
thống phân tán như redelivery, race condition, mất WebSocket và giới hạn quyền.
Đây là cơ sở để kết luận đề tài đáp ứng phạm vi MVP và xác định các hướng mở rộng
ngoài phạm vi trong tương lai.
