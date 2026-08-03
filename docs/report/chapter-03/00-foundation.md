# Nguồn sự thật cho Chương 3 — Phân tích và thiết kế hệ thống

> Phiên bản: 1.1 — cập nhật mô hình Queue ngày 03/08/2026
> Phạm vi: CareFlow MVP hỗ trợ hành trình khám ngoại trú
> Trạng thái: baseline dùng chung cho nội dung và biểu đồ các giai đoạn sau

## 1. Mục đích

Tài liệu này thống nhất phạm vi, nguồn tham chiếu, tác nhân, thuật ngữ và trạng
thái nghiệp vụ trước khi viết chi tiết Chương 3. Mọi đặc tả Use Case và biểu đồ
phải dùng các tên đã chốt tại đây. Nếu code hiện tại khác thiết kế mục tiêu,
báo cáo phải nêu rõ khác biệt thay vì âm thầm trộn hai trạng thái.

## 2. Thứ tự ưu tiên nguồn tham chiếu

1. `docs/patient-journey-and-system-workflow.md`: quyết định nghiệp vụ đã chốt.
2. `docs/service-contracts/`: ranh giới service, API, event và trạng thái mục tiêu.
3. Code trên nhánh `develop`: bằng chứng về phần đã hiện thực.
4. `docs/service-contracts/REMOTE-BRANCH-AUDIT-2026-07-30.md`: bằng chứng prototype trên các nhánh.
5. Patient Mobile và Doctor Web: bằng chứng giao diện và luồng đã nối.

Khi các nguồn mâu thuẫn, quyết định nghiệp vụ và contract mục tiêu được dùng cho
phần **thiết kế**; code được dùng để đánh giá **hiện trạng triển khai** ở chương
xây dựng. Không mô tả prototype hoặc mock là chức năng production đã hoàn thiện.

## 3. Phạm vi phân tích

### 3.1. Trong phạm vi

- Đăng ký, đăng nhập và quản lý phiên người dùng.
- Quản lý hồ sơ bệnh nhân và hồ sơ cũ do bệnh nhân tải lên.
- Tra cứu khoa, khung giờ, đặt và hủy lịch khám.
- Cấp phiếu khám điện tử, số thứ tự và QR.
- Check-in tại phòng khám và quản lý ba làn active queue `PRIORITY`, `NORMAL`,
  `RESULT_REVIEW`.
- Đề xuất Round Robin `1:1:1`; bác sĩ chủ động gọi, gọi lại, đánh dấu lỡ lượt và
  bắt đầu phục vụ.
- Thực hiện phiên khám lâm sàng.
- Tạo chỉ định, thực hiện cận lâm sàng và phát hành kết quả.
- Tạo lượt quay lại bác sĩ đọc kết quả trong cùng consultation.
- Kê toa, tạo lượt chờ phát thuốc FIFO, ghi nhận cấp phát và hoàn tất lượt khám.
- Hiển thị hành trình và thông báo cho bệnh nhân.
- Xác thực, phân quyền theo vai trò và quyền sở hữu dữ liệu.

### 3.2. Ngoài phạm vi triển khai đầy đủ

- Cấp cứu, nội trú và quản lý giường bệnh.
- Quyết toán BHYT và cổng thanh toán production.
- Kho dược, tồn kho, nhập/xuất kho và kiểm kê thuốc.
- PACS, DICOM và xử lý ảnh y khoa chuyên dụng.
- AI Clinical Assistant và Analytics trong phiên bản báo cáo hiện tại.
- SMS, email và push notification qua nhà cung cấp production.

Thanh toán/BHYT được xem là hệ thống ngoài hoặc mô phỏng trạng thái trong MVP.

## 4. Phân loại mức độ hiện thực

| Nhãn | Ý nghĩa trong báo cáo |
|---|---|
| `IMPLEMENTED` | Có code trên `develop`, chạy được trong phạm vi đã kiểm chứng |
| `DEMO_MOCK` | Luồng có thể trình diễn nhưng một phần trạng thái/dữ liệu được mô phỏng có kiểm soát |
| `TARGET_DESIGN` | Đã chốt về nghiệp vụ/contract nhưng chưa hoàn thiện tích hợp thực tế |
| `OUT_OF_SCOPE` | Không phân tích hoặc triển khai trong phiên bản báo cáo này |

Phần phân tích và thiết kế được phép mô tả `TARGET_DESIGN`, nhưng chương xây dựng
phải tách rõ phần nào đã hiện thực, phần nào là mock và phần nào chưa hoàn thành.

### 4.1. Baseline quan sát trên nhánh `develop`

| Cụm chức năng | Nhãn baseline | Ghi chú sử dụng trong báo cáo |
|---|---|---|
| Identity/Auth | `IMPLEMENTED` | Có đăng ký, đăng nhập, refresh, logout, `/me` và eKYC mock |
| Patient/Profile/Uploaded Record | `IMPLEMENTED` | Có API hồ sơ và quản lý hồ sơ cũ do bệnh nhân upload |
| Appointment | `IMPLEMENTED` | Có tra cứu khoa/slot, đặt, xem và hủy; lịch online tạo ở `CONFIRMED` |
| ClinicRoom và phân phòng | `TARGET_DESIGN` | Code hiện tại chỉ có enum `Department`; chưa có bảng `ClinicRoom`, `Appointment.roomId` hoặc quy tắc phân phòng |
| Patient Mobile trước khám | `IMPLEMENTED` | Có đăng nhập, hồ sơ, upload và luồng đặt lịch qua API |
| Patient Mobile hành trình sau đặt lịch | `DEMO_MOCK` | Queue, consultation, lab, prescription và notification được mô phỏng có kiểm soát trong Demo Mode |
| Queue Management backend | `TARGET_DESIGN` | Có prototype trên nhánh đã audit nhưng chưa khớp contract ba làn và Round Robin `1:1:1` |
| Consultation/Prescription/EMR | `TARGET_DESIGN` | Có prototype trên các nhánh đã audit; cần tiếp tục chuẩn hóa auth, state và event |
| Laboratory Order | `TARGET_DESIGN` | Contract đã chốt nhưng backend đầy đủ chưa có trên baseline đã audit |
| Notification | `TARGET_DESIGN` | Có prototype WebSocket; inbox bền vững và tích hợp event chưa hoàn thiện |
| Doctor/Hospital Web | `TARGET_DESIGN` | Code trên `develop` mới là nền tảng tối thiểu; giao diện nghiệp vụ đầy đủ nằm ở prototype/thiết kế |
| AI và Analytics | `OUT_OF_SCOPE` | Không đưa vào Use Case cốt lõi của báo cáo hiện tại |

## 5. Tác nhân của hệ thống

| Mã | Tác nhân | Vai trò chính |
|---|---|---|
| `ACT-PATIENT` | Bệnh nhân | Quản lý hồ sơ, đặt khám, xem phiếu, theo dõi hành trình và kết quả |
| `ACT-DOCTOR` | Bác sĩ | Theo dõi queue, khám, chỉ định, đọc kết quả, kê toa và hẹn tái khám |
| `ACT-RECEPTION` | Nhân viên tiếp nhận | Quét QR, check-in và hỗ trợ trường hợp lỡ lượt |
| `ACT-LAB` | Kỹ thuật viên cận lâm sàng | Nhận order, gọi lượt, thực hiện và phát hành kết quả |
| `ACT-PHARMACY` | Nhân viên cấp phát thuốc | Theo dõi queue tại điểm cấp phát, gọi lượt, đối chiếu và xác nhận đã phát thuốc |
| `ACT-ADMIN` | Quản trị viên | Quản lý tài khoản nội bộ, khoa, phòng, lịch và điểm phục vụ |
| `ACT-PAYMENT` | Hệ thống thanh toán/BHYT ngoài | Xác nhận trạng thái đủ điều kiện thực hiện dịch vụ trong phạm vi mock/tích hợp |

`Patient Mobile App`, `Hospital Web App`, API Gateway và các microservice là
thành phần của hệ thống, không phải tác nhân con người trong Use Case Diagram.
Kiosk chỉ được biểu diễn là tác nhân phụ nếu xác định nó là hệ thống bên ngoài
CareFlow; trong MVP, check-in chính do nhân viên tiếp nhận thực hiện.
`ACT-PHARMACY` được ánh xạ vào role kỹ thuật `STAFF` trong MVP; việc tách role
`PHARMACY_STAFF` là khả năng mở rộng, không phải điều kiện để triển khai queue.

## 6. Thuật ngữ nghiệp vụ thống nhất

| Thuật ngữ | Định nghĩa |
|---|---|
| Appointment | Lịch khám trong tương lai; giữ slot nhưng không chứng minh bệnh nhân đã có mặt |
| Department | Khoa chuyên môn; một khoa có thể quản lý nhiều phòng khám |
| ClinicRoom | Phòng khám vật lý thuộc một Department; là đích của Appointment và queue phòng khám |
| Visit Ticket | Phiếu khám điện tử gồm số thứ tự, phòng, khung giờ và QR |
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
| Follow-up | Lịch tái khám được tạo từ kết luận của consultation |
| Service Point | Phòng khám, khu lấy mẫu, quầy phát thuốc hoặc vị trí cung cấp một dịch vụ |

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
ORDERED → PAYMENT_PENDING → QUEUED
CALLED → MISSED → QUEUED
ORDERED | PAYMENT_PENDING → CANCELLED
```

### 7.5. Prescription

```text
DRAFT → CONFIRMED → DISPENSED
DRAFT → CANCELLED
CONFIRMED → CANCELLED_BY_AMENDMENT
```

Bệnh nhân chỉ xem toa `CONFIRMED` hoặc `DISPENSED`.

## 8. Quy tắc nghiệp vụ nền tảng

1. Lịch online tự xác nhận nếu ngày/slot hợp lệ và còn capacity.
2. Một bệnh nhân không có hai lịch chưa hủy trong cùng một khung giờ.
3. Mô hình mục tiêu là `Department 1:N ClinicRoom`. Trong dữ liệu MVP, mỗi khoa
   chỉ có đúng một phòng active; Appointment Service suy
   ra và lưu phòng, không nhận `roomId` từ bệnh nhân và không chọn random.
4. Nếu truy vấn phòng active trả 0 hoặc nhiều hơn 1 kết quả, MVP báo lỗi cấu hình
   thay vì âm thầm dùng `findFirst()`.
5. Phiếu có thể được cấp trước nhưng lượt khám ban đầu chỉ vào active queue sau
   khi bệnh nhân check-in.
6. Mỗi phòng và phiên có ba làn `PRIORITY`, `NORMAL`, `RESULT_REVIEW`; đề xuất
   theo Round Robin `1:1:1`, bỏ qua làn rỗng và giữ FIFO trong từng làn.
7. Lượt khám ban đầu mặc định thuộc `NORMAL`; chỉ nhân viên có quyền xác nhận
   `PRIORITY` theo diện đã kiểm tra và phải lưu lý do/audit.
8. QR chỉ chứa token tham chiếu hoặc token đã ký, không chứa dữ liệu y tế trực tiếp.
9. Bệnh nhân lỡ lượt không được giữ ở đầu queue.
10. Order đủ điều kiện tự tạo lượt cận lâm sàng; bệnh nhân không check-in lại.
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
