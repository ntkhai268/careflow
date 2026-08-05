# CHƯƠNG 5. HIỆN THỰC PHẦN MỀM

Chương này trình bày kết quả hiện thực thiết kế ở Chương 4 thành các service,
API, giao diện và luồng chạy của CareFlow. Việc mô tả được sắp xếp theo hành
trình nghiệp vụ để người đọc có thể đối chiếu trực tiếp với các Use Case ở
Chương 3. Các trạng thái payment Mobile được hiện thực đúng biên `DEMO_MODE`
đã thống nhất, không được xem là hợp đồng thanh toán bệnh viện production.

## 5.1. Phiên bản phần mềm đã xây dựng

Phiên bản trình bày trong chương này là bản MVP mục tiêu của CareFlow, gồm
Patient Mobile, Hospital Web, Gateway, các service nghiệp vụ, PostgreSQL,
RabbitMQ và Notification/WebSocket. Bản này bao phủ hành trình từ đặt lịch,
check-in, ba lane phòng khám, cận lâm sàng, review kết quả, kê toa, quyết toán
đến phát thuốc. Các adapter trả trước/quyết toán Mobile được xây dựng ở
`DEMO_MODE` theo đúng biên đã chốt; cổng thanh toán và hoàn tiền production,
PACS, kho thuốc và AI/Analytics là giới hạn của đề tài. Luồng chỉ xét người bệnh
tự chi trả.

## 5.2. Môi trường và công cụ phát triển

| Thành phần | Công nghệ sử dụng | Vai trò trong hệ thống |
|---|---|---|
| Backend | Java 21, Spring Boot 3.3.5, Spring Data JPA, Spring Security | service nghiệp vụ, REST API, validation và phân quyền |
| Gateway/discovery | Spring Cloud Gateway, Eureka | định tuyến, JWT boundary và tìm instance |
| Cơ sở dữ liệu | PostgreSQL, Flyway | lưu dữ liệu theo service, migration và ràng buộc |
| Message broker | RabbitMQ | event, retry, dead-letter và tách producer/consumer |
| Realtime | WebSocket/STOMP | cập nhật queue và Notification |
| Hospital Web | Next.js, React, TypeScript | giao diện bác sĩ, kỹ thuật viên, nhân viên và bộ phận quản lý |
| Patient Mobile | Flutter, Dart, Riverpod | đặt lịch, QR, hành trình và thông báo cho bệnh nhân |
| Đóng gói | Maven, Docker Compose | build, chạy local và mô phỏng triển khai nhiều service |

Backend được tổ chức theo Maven multi-module. Mỗi service có cấu hình riêng,
Flyway migration riêng và chỉ truy cập database của mình. Frontend dùng các
client/repository để tách widget khỏi API contract; Patient Mobile dùng adapter
để thay thế dữ liệu demo bằng backend contract khi contract tương ứng được mở.

## 5.3. Cấu trúc mã nguồn

Một service backend có các nhóm package sau:

```text
<service>
├── controller/       REST endpoint và DTO vào/ra
├── application/      use-case service, transaction, authorization
├── domain/           aggregate, value object, state transition
├── repository/       JPA repository và truy vấn đọc
├── messaging/        event envelope, publisher, consumer, outbox
└── migration/        Flyway schema và seed dữ liệu MVP
```

Các trạng thái được biểu diễn bằng enum có giá trị contract ổn định. Timestamp
được lưu UTC và chuyển sang múi giờ hiển thị tại giao diện. Mọi request đi qua
Gateway mang `correlationId`; log nghiệp vụ ghi actor, aggregate, thao tác và
kết quả để hỗ trợ truy vết.

## 5.4. Hiện thực theo từng Use Case

Hai giai đoạn thanh toán được hiện thực đúng ranh giới thiết kế: phí khám trả
trước và quyết toán cuối lượt dùng adapter local/demo. Laboratory Order chỉ ghi
nhận chi phí và tạo queue ngay khi hợp lệ. CareFlow không giả lập sổ kế toán,
giao dịch ngân hàng hoặc hoàn tiền production.

### 5.4.1. `UC-APT-02` — Đặt lịch khám

Patient Mobile triển khai luồng bốn bước: chọn hồ sơ, chọn khoa/ngày/ca, chọn
dịch vụ và chọn phương thức phí khám. Catalog fixture mặc định có mã
`GENERAL_CONSULTATION`, tên “Khám thường”, thời lượng 15 phút và giá hiển thị
150.000 ₫. Khi catalog backend chưa cung cấp, adapter local trả cùng cấu trúc
để giao diện không thay đổi.

Sau khi người bệnh xác nhận, Mobile gọi Appointment API với payload lịch hẹn
chuẩn. Appointment Service kiểm tra hồ sơ, slot, capacity, suy ra phòng đang
hoạt động của khoa và trả về appointment `CONFIRMED` cùng `roomId`. Receipt phí
khám được lưu qua `AppointmentPaymentReceipt` local:

- `ONLINE_MOCK` chuyển receipt sang `PAID` trong môi trường demo.
- `CASH_AT_HOSPITAL` chuyển receipt sang `DUE_AT_HOSPITAL` và hướng dẫn thanh
  toán tại bệnh viện.

Receipt local không làm thay đổi trạng thái Appointment và lỗi lưu receipt
không hủy một lịch hẹn đã tạo thành công. Giá trị thực tế đã thu được dùng làm
`prepaidAmount` khi quyết toán cuối lượt.

API/service thực tế gồm API tra khoa/slot và `POST /api/appointments` qua
Appointment Service. Dữ liệu chính là Appointment, ClinicRoom, Visit Ticket và
`AppointmentConfirmed`; các ngoại lệ hết capacity, đặt trùng, sai ownership và
không có phòng active được trả về ngay trên form.

### 5.4.2. `UC-QUE-02` — Check-in bằng QR

Màn hình vé hiển thị mã QR, số thứ tự, khoa, phòng và trạng thái hiện tại.
Nhân viên có thể quét QR hoặc tìm theo mã; bệnh nhân không có điện thoại vẫn
được tạo ticket trực tiếp tại quầy với cùng API. Queue Service kiểm tra ticket
chưa check-in, ghi `checkedInAt`, xác định session và đưa entry vào lane phù hợp.

Queue entry của lượt khám ban đầu thuộc `PRIORITY` hoặc `NORMAL`; lượt quay lại
đọc kết quả thuộc phase `RESULT_REVIEW`; lượt quầy thuốc dùng queue type
`PHARMACY_DISPENSING`. Giao diện Mobile đọc snapshot qua REST và nhận thay đổi
qua WebSocket, nhưng mọi trạng thái quan trọng vẫn được lưu trong Queue Service.

Hospital Web gọi `POST /api/queues/check-in`. Queue Service lưu Queue Entry,
`checkedInAt`, audit và event `PatientCheckedIn`; QR sai ngày/chữ ký/phòng,
ticket đã dùng hoặc thiếu lý do ưu tiên đều bị từ chối mà không tạo entry trùng.

### 5.4.3. `UC-QUE-04` — Theo dõi, đề xuất và gọi lượt

Hospital Web hiển thị ba cột queue trong cùng một phòng/ca. Mỗi cột giữ FIFO
theo `queuedAt`. Thành phần `recommendedNext` của Queue Service áp dụng Round
Robin 1:1:1 theo thứ tự các lane; lane rỗng được bỏ qua. Vì bác sĩ là người
quyết định cuối cùng, giao diện có cả nút gọi tại entry cụ thể và nút gọi theo
gợi ý.

Khi bấm gọi, request được kiểm tra quyền bác sĩ, `roomId`, session và trạng thái
entry. Transaction khóa entry, chuyển sang `CALLED`, cập nhật
`lastServedLane`, tạo `PatientCalled` và ghi audit. Nếu bệnh nhân không có mặt,
bác sĩ chọn `MISSED`; thao tác requeue tạo thời điểm mới và giữ lịch sử lần gọi
trước. Consumer gửi thông báo đến Mobile và cập nhật bảng phòng qua WebSocket.

Các API chính là active queue, call/call-next, recall, miss và requeue. Queue
Service lưu QueueSession/QueueAudit và phát `PatientCalled`; lỗi đồng thời trả
xung đột cho request đến sau, không phát notification thứ hai.

### 5.4.4. `UC-CON-01` — Bắt đầu và thực hiện phiên khám

Sau khi gọi, bác sĩ mở hồ sơ phiên khám, nhập triệu chứng, sinh hiệu, chẩn đoán
sơ bộ và chỉ định. Consultation Service kiểm tra bác sĩ thuộc khoa/phòng hợp
lệ, lưu ghi chú và chuyển trạng thái theo state machine.

Hospital Web gọi API bắt đầu Queue Entry, tạo Consultation và cập nhật clinical
data. Dữ liệu gồm Consultation, ClinicalNote và Diagnosis; lượt chưa được gọi,
bác sĩ sai assignment hoặc transition không hợp lệ bị từ chối và ghi audit.

### 5.4.5. `UC-LAB-01` — Tạo chỉ định cận lâm sàng

Khi có chỉ định, Laboratory Order Service tạo order và các order item độc lập.
Bác sĩ chọn hạng mục, nhập lý do/ghi chú và gửi `POST /api/labs/orders`. Service
kiểm consultation, assignment và catalog trước khi lưu LaboratoryOrder,
LaboratoryOrderItem và event đủ điều kiện tạo queue.

Mỗi item lưu đơn giá và thành tiền để cộng vào `totalVisitCost`. Order hợp lệ
được phát `LabOrderCreated` và tạo `LAB_EXECUTION` ngay; người bệnh không thực
hiện thanh toán riêng ở khu cận lâm sàng. Order trùng hoặc item không hợp lệ bị
từ chối trước khi tạo queue.

### 5.4.6. `UC-LAB-03` — Thực hiện, phát hành và đọc kết quả

Khi tất cả kết quả bắt buộc đã `FINAL`, Laboratory Order Service phát
`AllRequiredResultsAvailable`. Queue Service tạo entry `RESULT_REVIEW` trong
cùng consultation/appointment; không tạo lịch hẹn giả và không yêu cầu bệnh
nhân check-in lại trên Mobile. Bác sĩ gọi entry, đọc kết quả, cập nhật chẩn
đoán cuối và kết luận.

Kỹ thuật viên dùng API start, cập nhật result item và finalize; bác sĩ dùng API
mark-reviewed. Laboratory Service lưu draft/final/correction và phát
`AllRequiredResultsAvailable`. Thiếu item bắt buộc không tạo `RESULT_REVIEW`;
kết quả FINAL chỉ được sửa bằng correction có audit.

### 5.4.7. `UC-PRE-01` — Kê toa, hẹn tái khám và hoàn tất

Nếu cần thuốc, Prescription Service lưu toa, liều dùng, số lượng và hướng dẫn;
bác sĩ xác nhận toa để phát `PrescriptionIssued`. Nếu cần tái khám, FollowUp
Appointment được tạo với liên kết đến consultation hiện tại.

Hospital Web gọi API tạo/cập nhật/xác nhận Prescription, tạo follow-up và hoàn
tất Consultation. Toa đã xác nhận không bị ghi đè; thuốc/liều thiếu hợp lệ hoặc
consultation chưa đủ kết luận làm thao tác xác nhận thất bại.

### 5.4.8. `UC-PHA-01` — Gọi lượt và phát thuốc

Queue Service tạo FIFO entry `PHARMACY_DISPENSING` khi toa được xác nhận. Màn
hình quầy thuốc hiển thị số đang gọi, toa tương ứng và cảnh báo đối chiếu. Nhân
viên cấp phát chỉ được xác nhận khi entry thuộc đúng điểm phục vụ, prescription
đang `CONFIRMED`, chưa có bản ghi dispense và quyết toán không còn
`PAYMENT_DUE`.

Sau khi xác nhận, hệ thống ghi người cấp phát, thời gian, các item đã giao và
phát `PrescriptionDispensed`. Patient Mobile chuyển hành trình sang
`medicationReady` rồi `completed`. Trước đó, adapter quyết toán tổng hợp phí
khám, cận lâm sàng và thuốc, sau đó áp dụng:

```text
amountDue = max(0, totalVisitCost - prepaidAmount)
refundDue = max(0, prepaidAmount - totalVisitCost)
```

Nếu còn thiếu, adapter chuyển `PAYMENT_DUE → SETTLED` sau khi xác nhận thu đủ.
Nếu trả trước dư, trạng thái là `REFUND_PENDING → REFUNDED`; trạng thái chờ hoàn
không chặn cấp phát vì người bệnh không còn khoản phải nộp.

Quầy thuốc dùng API queue tại service point và
`POST /api/prescriptions/{prescriptionId}/dispense`. Prescription Service lưu
DispenseRecord và phát `PrescriptionDispensed`; toa đã phát/hủy, sai điểm phục
vụ hoặc queue chưa `IN_PROGRESS` bị từ chối.

### 5.4.9. Chức năng hỗ trợ — Thông báo và đồng bộ Mobile

Notification Service nhận các event cần thông báo, tạo inbox bền vững và gửi
STOMP qua `/ws/notifications`. Recipient resolution dựa trên `patientId`,
`doctorId` hoặc vai trò điểm phục vụ; không broadcast dữ liệu lâm sàng cho
người không liên quan. Mobile lưu cache hành trình, đánh dấu đã đọc qua REST
và đồng bộ lại snapshot khi WebSocket bị gián đoạn.

## 5.5. Triển khai và vận hành hệ thống

Docker Compose khởi động Gateway, Eureka, các service nghiệp vụ, PostgreSQL,
RabbitMQ và frontend. Startup chạy Flyway theo thứ tự dependency; seed tạo khoa,
phòng hoạt động, ca làm việc và dữ liệu demo. Health check của từng service được
Gateway và Compose sử dụng để chỉ nhận traffic khi database và broker sẵn sàng.

Các cấu hình nhạy cảm như JWT secret, chuỗi kết nối và credential broker được
đọc từ biến môi trường. Môi trường phát triển có thể dùng dữ liệu demo; dữ liệu
thật phải dùng secret manager và bật HTTPS khi triển khai.

## 5.6. Kết luận chương

Chương 5 đã mô tả bản CareFlow được xây dựng từ thiết kế: backend phân service,
Hospital Web điều phối các điểm phục vụ, Patient Mobile theo dõi toàn bộ hành
trình và RabbitMQ/WebSocket liên kết các thay đổi. Các Use Case từ đặt lịch đến
phát thuốc được hiện thực nhất quán với mô hình ba lane, review kết quả trong
cùng consultation và biên trả trước/quyết toán Mobile. Chương 6 kiểm thử từng
lớp, từng Use Case, event, trạng thái và yêu cầu chất lượng để đánh giá bản xây
dựng.
