# Nguồn sự thật cho Chương 3 — Phân tích và thiết kế hệ thống

> Phiên bản: 1.3 — cập nhật quyết toán tự chi trả hai giai đoạn ngày 05/08/2026
> Phạm vi: CareFlow MVP hỗ trợ hành trình khám ngoại trú
> Trạng thái: baseline dùng chung cho nội dung và biểu đồ các giai đoạn sau

## 1. Mục đích

Tài liệu này thống nhất phạm vi, nguồn tham chiếu, tác nhân, thuật ngữ và trạng
thái nghiệp vụ trước khi viết chi tiết Chương 3. Mọi đặc tả Use Case và biểu đồ
phải dùng các tên đã chốt tại đây. Theo quyết định của tác giả báo cáo ngày
04/08/2026, các chức năng thuộc phạm vi MVP và `TARGET_DESIGN` được trình bày
như phiên bản mục tiêu đã hoàn thành; chỉ capability cố ý dùng adapter demo mới
tiếp tục mang nhãn `DEMO_MOCK`.

## 2. Thứ tự ưu tiên nguồn tham chiếu

1. `docs/decisions/2026-08-05-two-stage-self-pay-settlement.md`: quyết định mới nhất cho thanh toán tự chi trả hai giai đoạn.
2. `docs/decisions/2026-08-04-mobile-payment-flow.md`: quyết định cho Mobile payment.
3. `docs/patient-journey-and-system-workflow.md`: quyết định nghiệp vụ đã chốt.
4. `docs/service-contracts/`: ranh giới service, API, event và trạng thái mục tiêu.
5. Code trên nhánh `develop`: bằng chứng về phần đã hiện thực.
6. `docs/service-contracts/REMOTE-BRANCH-AUDIT-2026-07-30.md`: bằng chứng prototype trên các nhánh.
7. Patient Mobile và Doctor Web: bằng chứng giao diện và luồng đã nối.

Khi các nguồn mâu thuẫn, quyết định nghiệp vụ và contract mục tiêu được dùng cho
nội dung báo cáo. Baseline code bên dưới chỉ phục vụ theo dõi kỹ thuật nội bộ,
không được dùng để hạ các chức năng trong phạm vi thành “chưa hoàn thành” ở
Chương 5. Payment Mobile và adapter được chốt là mock vẫn phải ghi đúng biên,
không được gọi là production.

## 3. Phạm vi phân tích

### 3.1. Trong phạm vi

- Đăng ký, đăng nhập và quản lý phiên người dùng.
- Quản lý hồ sơ bệnh nhân và hồ sơ cũ do bệnh nhân tải lên.
- Tra cứu khoa, khung giờ, đặt và hủy lịch khám.
- Hiển thị dịch vụ khám và fixture thanh toán phí khám trên Patient Mobile.
- Cấp phiếu khám điện tử, số thứ tự và QR check-in theo phòng/phiên để bệnh viện hiển thị.
- Check-in tại phòng khám và quản lý ba làn active queue `PRIORITY`, `NORMAL`,
  `RESULT_REVIEW`.
- Đề xuất Round Robin `1:1:1`; bác sĩ chủ động gọi, gọi lại, đánh dấu lỡ lượt và
  bắt đầu phục vụ.
- Thực hiện phiên khám lâm sàng.
- Tạo chỉ định, thực hiện cận lâm sàng và phát hành kết quả.
- Trả trước phí khám khi đặt lịch và quyết toán toàn bộ chi phí ở cuối lượt;
  không có payment riêng cho Laboratory Order.
- Tạo lượt quay lại bác sĩ đọc kết quả trong cùng consultation.
- Kê toa, tạo lượt chờ phát thuốc FIFO, ghi nhận cấp phát và hoàn tất lượt khám.
- Hiển thị hành trình và thông báo cho bệnh nhân.
- Xác thực, phân quyền theo vai trò và quyền sở hữu dữ liệu.

### 3.2. Ngoài phạm vi triển khai đầy đủ

- Cấp cứu, nội trú và quản lý giường bệnh.
- Nghiệp vụ BHYT, cổng thanh toán và hoàn tiền production.
- Catalog dịch vụ/bảng giá production, Payment/Refund API và ledger viện phí.
- Kho dược, tồn kho, nhập/xuất kho và kiểm kê thuốc.
- PACS, DICOM và xử lý ảnh y khoa chuyên dụng.
- AI Clinical Assistant và Analytics trong phiên bản báo cáo hiện tại.
- SMS, email và push notification qua nhà cung cấp production.

Payment trong Patient Mobile là capability local/demo, không tạo Payment
microservice production trong MVP. Appointment Service chưa nhận payment/service
contract. Laboratory Order tạo queue ngay khi hợp lệ; quyết toán cuối lượt và
hoàn tiền dùng adapter demo. Phiên bản báo cáo chỉ xét người bệnh tự chi trả và
không phân tích nghiệp vụ BHYT.

## 4. Phân loại mức độ hiện thực

| Nhãn | Ý nghĩa trong báo cáo |
|---|---|
| `IMPLEMENTED` | Có code trên `develop`, chạy được trong phạm vi đã kiểm chứng |
| `DEMO_MOCK` | Luồng có thể trình diễn nhưng một phần trạng thái/dữ liệu được mô phỏng có kiểm soát |
| `TARGET_DESIGN` | Đã chốt về nghiệp vụ/contract; trong báo cáo được xem như phiên bản mục tiêu đã hoàn thành |
| `OUT_OF_SCOPE` | Không phân tích hoặc triển khai trong phiên bản báo cáo này |

Chương xây dựng trình bày `IMPLEMENTED` và `TARGET_DESIGN` trong phạm vi MVP như
phiên bản hoàn chỉnh theo quyết định của tác giả. Chỉ `DEMO_MOCK` và
`OUT_OF_SCOPE` phải được tách rõ.

### 4.1. Baseline quan sát trên nhánh `develop`

> Bảng này chỉ là dấu vết kỹ thuật tại thời điểm khảo sát code. Không dùng bảng
> để mô tả các capability trong phạm vi MVP là chưa hoàn thành trong báo cáo.

| Cụm chức năng | Nhãn baseline | Ghi chú sử dụng trong báo cáo |
|---|---|---|
| Identity/Auth | `IMPLEMENTED` | Có đăng ký, đăng nhập, refresh, logout, `/me` và eKYC mock |
| Patient/Profile/Uploaded Record | `IMPLEMENTED` | Có API hồ sơ và quản lý hồ sơ cũ do bệnh nhân upload |
| Appointment | `IMPLEMENTED` | Có tra cứu khoa/slot, đặt, xem và hủy; lịch online tạo ở `CONFIRMED` |
| ClinicRoom và phân phòng | `TARGET_DESIGN` | Code hiện tại chỉ có enum `Department`; chưa có bảng `ClinicRoom`, `Appointment.roomId` hoặc quy tắc phân phòng |
| Patient Mobile trước khám | `IMPLEMENTED` | Có đăng nhập, hồ sơ, upload và luồng đặt lịch qua API |
| Patient Mobile hành trình sau đặt lịch | `DEMO_MOCK` | Queue, consultation, lab, prescription, notification và các fixture payment được mô phỏng có kiểm soát trong Demo Mode |
| Patient Mobile payment | `DEMO_MOCK` | `AppointmentPaymentReceipt`, Visit Settlement và refund state; local adapter, chưa phải backend contract production |
| Queue Management backend | `TARGET_DESIGN` | Có prototype trên nhánh đã audit nhưng chưa khớp contract ba làn và Round Robin `1:1:1` |
| Consultation/Prescription/EMR | `TARGET_DESIGN` | Có prototype trên các nhánh đã audit; cần tiếp tục chuẩn hóa auth, state và event |
| Laboratory Order | `TARGET_DESIGN` | Contract đã chốt nhưng backend đầy đủ chưa có trên baseline đã audit |
| Notification | `TARGET_DESIGN` | Có prototype WebSocket; inbox bền vững và tích hợp event chưa hoàn thiện |
| Doctor/Hospital Web | `TARGET_DESIGN` | Code trên `develop` mới là nền tảng tối thiểu; giao diện nghiệp vụ đầy đủ nằm ở prototype/thiết kế |
| AI và Analytics | `OUT_OF_SCOPE` | Không đưa vào Use Case cốt lõi của báo cáo hiện tại |

## 5. Tác nhân của hệ thống

| Mã | Tác nhân | Vai trò chính |
|---|---|---|
| `ACT-PATIENT` | Bệnh nhân | Quản lý hồ sơ, đặt khám, quét QR tại bệnh viện, theo dõi hành trình và kết quả |
| `ACT-DOCTOR` | Bác sĩ | Theo dõi queue, khám, chỉ định, đọc kết quả, kê toa và hẹn tái khám |
| `ACT-RECEPTION` | Nhân viên tiếp nhận | Hiển thị QR check-in, hỗ trợ check-in tại quầy và xử lý trường hợp lỡ lượt |
| `ACT-LAB` | Kỹ thuật viên cận lâm sàng | Nhận order, gọi lượt, thực hiện và phát hành kết quả |
| `ACT-PHARMACY` | Nhân viên cấp phát thuốc | Theo dõi queue tại điểm cấp phát, gọi lượt, đối chiếu và xác nhận đã phát thuốc |
| `ACT-MANAGEMENT` | Bộ phận quản lý bệnh viện | Duy trì khoa, phòng, lịch, capacity và điểm phục vụ ở mức nghiệp vụ |
| `ACT-PAYMENT` | Thu ngân/hệ thống thanh toán ngoài | ghi nhận phí khám trả trước, quyết toán cuối lượt và hoàn phần dư nếu có |

`Patient Mobile App`, `Hospital Web App`, API Gateway và các microservice là
thành phần của hệ thống, không phải tác nhân con người trong Use Case Diagram.
Xác thực và phân quyền là tiền điều kiện/yêu cầu vận hành; không biểu diễn
`User`, actor kỹ thuật `Admin`, Đăng nhập hoặc Phân quyền như Use Case nghiệp vụ.
Màn hình hiển thị QR là một phần của Hospital Web/CareFlow, không phải tác nhân
nghiệp vụ độc lập. Trong MVP, check-in chính do bệnh nhân quét QR tại bệnh viện;
nhân viên tiếp nhận là luồng hỗ trợ cho người không dùng Mobile.
`ACT-PHARMACY` được ánh xạ vào role kỹ thuật `STAFF` trong MVP; việc tách role
`PHARMACY_STAFF` là khả năng mở rộng, không phải điều kiện để triển khai queue.

## 6. Thuật ngữ nghiệp vụ thống nhất

| Thuật ngữ | Định nghĩa |
|---|---|
| Appointment | Lịch khám trong tương lai; giữ slot nhưng không chứng minh bệnh nhân đã có mặt |
| Department | Khoa chuyên môn; một khoa có thể quản lý nhiều phòng khám |
| ClinicRoom | Phòng khám vật lý thuộc một Department; là đích của Appointment và queue phòng khám |
| Visit Ticket | Phiếu khám điện tử gồm số thứ tự, phòng và khung giờ; không phải QR check-in dùng chung tại bệnh viện |
| Hospital Check-in QR | QR do bệnh viện hiển thị theo phòng/phiên, chứa token ký số có thời hạn để bệnh nhân quét khi đến nơi |
| Hospital Geofence | Cấu hình tâm vị trí và bán kính cho phép dùng để xác nhận thiết bị của bệnh nhân đang ở bệnh viện |
| Queue Entry | Một lượt chờ tại một điểm phục vụ cụ thể |
| Active Queue | Danh sách lượt đủ điều kiện được gọi tại thời điểm hiện tại |
| Queue Type | Công đoạn phục vụ: `CONSULTATION`, `LAB_EXECUTION` hoặc `PHARMACY_DISPENSING` |
| Consultation Phase | Giai đoạn của lượt khám: `INITIAL` hoặc `RESULT_REVIEW` |
| Queue Class | Diện của lượt khám ban đầu: `PRIORITY` hoặc `NORMAL` |
| Scheduling Lane | Làn điều phối suy ra: `PRIORITY`, `NORMAL` hoặc `RESULT_REVIEW` |
| Consultation | Một phiên khám lâm sàng, có thể tạm dừng để chờ kết quả |
| Laboratory Order | Chỉ định cận lâm sàng và các hạng mục cần thực hiện |
| Result Review | Lượt quay lại bác sĩ đọc kết quả trong cùng consultation |
| Prescription | Toa thuốc do bác sĩ tạo và xác nhận |
| AppointmentServiceOption | Dịch vụ Mobile hiển thị trong bước đặt khám; MVP dùng `GENERAL_CONSULTATION` |
| AppointmentPaymentReceipt | Receipt local ghi nhận `ONLINE_MOCK` đã trả trước; receipt tiền mặt cũ chỉ được đọc tương thích |
| VisitSettlement | Bản quyết toán cuối lượt gồm tổng chi phí, khoản trả trước, số phải trả thêm, số phải hoàn và trạng thái |
| Follow-up | Lịch tái khám được tạo từ kết luận của consultation |
| Service Point | Phòng khám, khu lấy mẫu, quầy phát thuốc hoặc vị trí cung cấp một dịch vụ |
| Prepayment | Khoản phí khám đã trả trước khi đặt lịch hoặc tại bệnh viện |
| Visit Settlement | Quyết toán cuối lượt, tổng hợp toàn bộ chi phí và khấu trừ Prepayment |
| Refund | Khoản dư phải hoàn khi Prepayment lớn hơn chi phí thực tế |

Không dùng “Appointment”, “Visit Ticket” và “Queue Entry” thay thế cho nhau.
Không gọi bản ghi bệnh nhân tự tải lên là bệnh án chính thức; EMR là góc nhìn
tổng hợp dữ liệu lâm sàng do các service nguồn phát sinh.

## 7. Trạng thái nghiệp vụ chuẩn

### 7.1. Appointment

```text
CONFIRMED → FULFILLED
CONFIRMED → CANCELLED
CONFIRMED → NO_SHOW
```

`PENDING` là trạng thái legacy, không thuộc luồng mục tiêu. Đặt lịch trực tuyến
được tự động xác nhận khi slot hợp lệ và còn capacity.

Patient Mobile có thể trình bày dịch vụ `GENERAL_CONSULTATION` và receipt phí
khám trước/sau khi gọi Appointment API. Đây là dữ liệu local/demo, không làm
Appointment chuyển sang `PENDING` và không làm thay đổi state machine backend.

### 7.2. Queue Entry

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

Tại phòng khám, `CONSULTATION + INITIAL + PRIORITY`,
`CONSULTATION + INITIAL + NORMAL` và `CONSULTATION + RESULT_REVIEW` lần lượt ánh
xạ thành ba làn điều phối. Các làn dùng chung mô hình Queue Entry, giữ FIFO theo
`queuedAt` trong từng làn và được Queue Service đề xuất theo Round Robin `1:1:1`.
Queue cận lâm sàng và phát thuốc giữ FIFO riêng theo từng `servicePointId`.

Lượt khám ban đầu chỉ vào active queue sau khi `CHECKED_IN`. Bệnh nhân không
check-in lại bằng Visit Ticket ở khu cận lâm sàng hoặc khi quay lại đọc kết quả;
entry `CONSULTATION + RESULT_REVIEW` tự active ngay khi đủ kết quả bắt buộc.
Toa `CONFIRMED` tạo entry `PHARMACY_DISPENSING`; bệnh nhân không check-in lại tại
quầy thuốc.

### 7.3. Consultation

```text
Không có cận lâm sàng:
NOT_STARTED → IN_PROGRESS → COMPLETED

Có cận lâm sàng:
NOT_STARTED → IN_PROGRESS → WAITING_FOR_RESULTS
→ WAITING_FOR_REVIEW → IN_PROGRESS → COMPLETED
```

### 7.4. Laboratory Order

```text
ORDERED → QUEUED → CALLED → IN_PROGRESS → RESULT_AVAILABLE → REVIEWED
CALLED → MISSED → QUEUED
ORDERED → CANCELLED
```

### 7.5. Prescription

```text
DRAFT → CONFIRMED → DISPENSED
DRAFT → CANCELLED
CONFIRMED → CANCELLED_BY_AMENDMENT
```

Bệnh nhân chỉ xem toa `CONFIRMED` hoặc `DISPENSED`.

Visit Settlement là state machine trình diễn riêng; không được gộp vào trạng
thái Prescription hoặc Queue. Chỉ trạng thái còn `PAYMENT_DUE` mới chặn dispense.

## 8. Quy tắc nghiệp vụ nền tảng

1. Lịch online tự xác nhận nếu ngày/slot hợp lệ và còn capacity.
1a. Booking Mobile hiển thị `GENERAL_CONSULTATION` với giá demo `150.000 ₫` và
    chỉ cho chọn `ONLINE_MOCK` hoặc `CASH_AT_HOSPITAL`; tiền mặt là
    `DUE_AT_HOSPITAL`, không tuyên bố đã thu. Appointment API chưa nhận
    payment/service contract.
1b. Lỗi local khi lưu `AppointmentPaymentReceipt` không làm rollback lịch đã
    tạo thành công.
1c. Phiên bản báo cáo chỉ xét người bệnh tự chi trả. Hành trình có hai giai đoạn:
    trả trước phí khám và quyết toán cuối lượt; không có payment riêng trước cận
    lâm sàng.
1d. `amountDue = max(0, totalVisitCost - prepaidAmount)` và
    `refundDue = max(0, prepaidAmount - totalVisitCost)`; không lưu số phải trả âm.
2. Một bệnh nhân không có hai lịch chưa hủy trong cùng một khung giờ.
3. Mô hình mục tiêu là `Department 1:N ClinicRoom`. Trong dữ liệu MVP, mỗi khoa
   chỉ có đúng một phòng active; Appointment Service suy
   ra và lưu phòng, không nhận `roomId` từ bệnh nhân và không chọn random.
4. Nếu truy vấn phòng active trả 0 hoặc nhiều hơn 1 kết quả, MVP báo lỗi cấu hình
   thay vì âm thầm dùng `findFirst()`.
5. Bệnh viện hiển thị QR check-in theo phòng/phiên. Lượt khám ban đầu chỉ vào
   active queue sau khi bệnh nhân quét QR hợp lệ và vị trí thiết bị nằm trong
   Hospital Geofence; người không dùng Mobile được nhân viên hỗ trợ tại quầy.
6. Mỗi phòng và phiên có ba làn `PRIORITY`, `NORMAL`, `RESULT_REVIEW`; đề xuất
   theo Round Robin `1:1:1`, bỏ qua làn rỗng và giữ FIFO trong từng làn.
7. Lượt khám ban đầu mặc định thuộc `NORMAL`; chỉ nhân viên có quyền xác nhận
   `PRIORITY` theo diện đã kiểm tra và phải lưu lý do/audit.
8. QR check-in chỉ chứa token phiên/phòng ký số có thời hạn, không chứa dữ liệu y
   tế trực tiếp. Server không tin tọa độ do client tự khai báo nếu thiếu dữ liệu
   vị trí hợp lệ; khoảng cách được tính từ tâm và bán kính cấu hình của bệnh viện.
   Ứng dụng chỉ lấy vị trí tại thời điểm check-in, không theo dõi liên tục.
9. Bệnh nhân lỡ lượt không được giữ ở đầu queue.
10. Order đủ điều kiện tự tạo lượt cận lâm sàng; bệnh nhân không check-in lại.
10a. Laboratory Order hợp lệ tạo lượt `LAB_EXECUTION` ngay; chi phí được cộng vào
     Visit Settlement cuối lượt.
11. Khi đủ kết quả, Queue tự động tạo và kích hoạt entry
   `CONSULTATION + RESULT_REVIEW` mới nhưng vẫn liên kết consultation cũ, không
   tạo Appointment mới; `queuedAt` lấy theo thời điểm đủ kết quả.
12. Bệnh nhân không cần dùng Mobile hoặc xác nhận quay lại. Nếu được gọi khi chưa
   có mặt, entry chuyển `MISSED` và được xếp lại cuối làn theo chính sách.
13. Hệ thống chỉ đề xuất; bác sĩ có thể gọi lượt được đề xuất hoặc bất kỳ entry
   đủ điều kiện nào trong active queue. Queue Service ghi người gọi và phát
   `PatientCalled`.
14. Doctor Web có thể suy ra `roomId` từ khoa của bác sĩ, nhưng Queue Service vẫn
    phải xác minh bác sĩ có quyền truy cập phòng trên URL.
15. Chỉ bác sĩ được phân công mới cập nhật consultation và xác nhận toa.
16. Patient chỉ được truy cập hồ sơ, kết quả và toa thuộc quyền sở hữu của mình.
17. Toa `CONFIRMED` tự tạo một lượt `PHARMACY_DISPENSING`; điểm cấp phát gọi FIFO
    và Prescription Service chỉ chuyển toa sang `DISPENSED` sau khi phát thuốc.
17a. Visit Settlement Mobile chỉ chạy trong `DEMO_MODE`, tổng hợp phí khám, cận
     lâm sàng, thuốc và dịch vụ phát sinh; không tự ghi giao dịch ngân hàng thật.
17b. Lượt `PHARMACY_DISPENSING` vẫn được tạo ngay từ toa `CONFIRMED`. Dispense bị
     chặn khi settlement là `PAYMENT_DUE`; `SETTLED`, `REFUND_PENDING` và
     `REFUNDED` đều đủ điều kiện vì người bệnh không còn nợ.
18. Queue phát thuốc không đồng nghĩa với quản lý kho; tồn kho vẫn ngoài phạm vi.
19. Thông báo thất bại không rollback giao dịch nghiệp vụ đã thành công.
20. AI và Analytics không xuất hiện trong luồng MVP của chương này.

## 9. Cấu trúc Chương 3 đã chốt

```text
3.1. Phân tích yêu cầu hệ thống
3.2. Mô hình Use Case tổng quát
3.3. Phân tích các Use Case cốt lõi
3.4. Thiết kế kiến trúc hệ thống
3.5. Thiết kế trạng thái nghiệp vụ
3.6. Thiết kế dữ liệu
3.7. Thiết kế giao tiếp hệ thống
3.8. Thiết kế bảo mật và phân quyền
3.9. Kết luận chương
```

Vì Chương 2 hiện là “Cơ sở lý thuyết”, phần “Phân tích và thiết kế hệ thống”
được đánh số Chương 3. Nếu bố cục cuối cùng thay đổi, việc đổi số chương sẽ được
thực hiện đồng loạt sau khi nội dung ổn định.

Thiết kế và xây dựng giao diện không tách thành một mục riêng trong Chương 3.
Sitemap, cấu trúc màn hình, ảnh giao diện và cách hiện thực Patient Mobile/
Hospital Web sẽ được trình bày thống nhất trong Chương 4 để tránh lặp nội dung.
