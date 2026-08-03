# CHƯƠNG 3. PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG

> Trạng thái: đã hoàn thành baseline và nội dung dùng chung của Giai đoạn 2.
> Đặc tả Use Case chi tiết, thiết kế dữ liệu và giao tiếp sẽ được bổ sung ở các
> checkpoint tiếp theo theo tài liệu nguồn trong thư mục `chapter-03`.

## 3.1. Phân tích yêu cầu hệ thống

CareFlow được xây dựng nhằm hỗ trợ và điều phối hành trình khám ngoại trú tại
bệnh viện công. Hệ thống kết nối bệnh nhân với bác sĩ và các bộ phận phục vụ từ
giai đoạn đặt lịch đến khi hoàn tất khám. Việc phân tích yêu cầu được thực hiện
dựa trên quy trình nghiệp vụ đã thống nhất, hợp đồng của các service và phạm vi
MVP. Những chức năng chưa hoàn thiện trong code vẫn có thể được trình bày như
thiết kế mục tiêu, nhưng phải được phân biệt với kết quả triển khai ở Chương 4.

### 3.1.1. Các tác nhân của hệ thống

Các tác nhân được xác định theo vai trò tương tác trực tiếp với CareFlow. Ứng
dụng Mobile, Hospital Web, API Gateway và các microservice là thành phần nội bộ,
không được xem là tác nhân trong Use Case Diagram.

| Mã | Tác nhân | Mô tả trách nhiệm |
|---|---|---|
| `ACT-PATIENT` | Bệnh nhân | Quản lý hồ sơ, đặt và hủy lịch, xem phiếu, theo dõi lượt, nhận kết quả, toa thuốc và lịch tái khám |
| `ACT-DOCTOR` | Bác sĩ | Xem queue và hồ sơ được phân công, gọi bệnh nhân, khám, tạo chỉ định, đọc kết quả, kê toa và hoàn tất phiên khám |
| `ACT-RECEPTION` | Nhân viên tiếp nhận | Quét QR, xác nhận bệnh nhân có mặt, hỗ trợ gọi lại, lỡ lượt và xếp lại hàng |
| `ACT-LAB` | Kỹ thuật viên cận lâm sàng | Theo dõi order và queue, gọi lượt, thực hiện kỹ thuật, nhập và phát hành kết quả |
| `ACT-ADMIN` | Quản trị viên | Quản lý tài khoản nội bộ, khoa, phòng, lịch làm việc, slot, capacity và điểm phục vụ |
| `ACT-PAYMENT` | Hệ thống thanh toán/BHYT ngoài | Xác nhận trạng thái đủ điều kiện thực hiện dịch vụ trong phạm vi tích hợp hoặc mô phỏng của MVP |

Trong MVP, Hospital Web là một ứng dụng dùng chung và hiển thị chức năng theo
vai trò. Thanh toán/BHYT không phải service cốt lõi của CareFlow mà được xem là
hệ thống ngoài. Kiosk chỉ trở thành tác nhân riêng nếu được phát triển như một
hệ thống độc lập; quy trình hiện tại sử dụng nhân viên tiếp nhận để check-in.

### 3.1.2. Yêu cầu chức năng

Yêu cầu chức năng được nhóm theo miền nghiệp vụ để thuận lợi ánh xạ sang Use Case
và service chịu trách nhiệm.

#### a. Xác thực và tài khoản

| Mã | Yêu cầu |
|---|---|
| `FR-AUTH-01` | Hệ thống cho phép bệnh nhân đăng ký tài khoản bằng thông tin hợp lệ. |
| `FR-AUTH-02` | Hệ thống cho phép người dùng đăng nhập và nhận phiên truy cập phù hợp với vai trò. |
| `FR-AUTH-03` | Hệ thống cho phép làm mới phiên và đăng xuất bằng refresh token. |
| `FR-AUTH-04` | Quản trị viên có thể khóa, mở hoặc vô hiệu hóa tài khoản nội bộ. |

#### b. Hồ sơ bệnh nhân

| Mã | Yêu cầu |
|---|---|
| `FR-PAT-01` | Bệnh nhân có thể tạo, xem và cập nhật hồ sơ thuộc tài khoản của mình. |
| `FR-PAT-02` | Bệnh nhân có thể tải lên, xem, sửa thông tin mô tả và xóa hồ sơ y tế cũ. |
| `FR-PAT-03` | Bác sĩ chỉ có thể xem hồ sơ của bệnh nhân thuộc lượt khám được phân công. |
| `FR-PAT-04` | Hệ thống phải kiểm tra quyền sở hữu trước khi trả hồ sơ hoặc tệp đính kèm. |

#### c. Lịch khám

| Mã | Yêu cầu |
|---|---|
| `FR-APT-01` | Bệnh nhân có thể tra cứu khoa, ngày khám và khung giờ khả dụng. |
| `FR-APT-02` | Bệnh nhân có thể chọn hồ sơ, khoa, ngày, khung giờ và lý do để đặt lịch. |
| `FR-APT-03` | Hệ thống tự xác nhận lịch nếu yêu cầu hợp lệ và slot còn capacity. |
| `FR-APT-04` | Bệnh nhân có thể xem danh sách, chi tiết và hủy lịch đủ điều kiện. |
| `FR-APT-05` | Bác sĩ có thể tạo lịch tái khám từ consultation đã có kết luận. |
| `FR-APT-06` | Khi đặt lịch, hệ thống tự xác định và lưu phòng khám active từ khoa; bệnh nhân không chọn hoặc truyền `roomId`. |

`FR-APT-06` là thiết kế mục tiêu. Code hiện tại mới dùng enum `Department` và
chưa có bảng `ClinicRoom` hoặc trường `Appointment.roomId`; phần hiện thực và
migration tương ứng sẽ được đánh giá riêng ở Chương 4.

#### d. Phiếu khám và hàng đợi

| Mã | Yêu cầu |
|---|---|
| `FR-QUE-01` | Hệ thống cấp Visit Ticket, số thứ tự và QR sau khi lịch được xác nhận. |
| `FR-QUE-02` | Nhân viên tiếp nhận có thể quét QR để xác nhận bệnh nhân đã đến. |
| `FR-QUE-03` | Active queue phòng khám hiển thị lượt khám ban đầu đã `CHECKED_IN` và lượt đọc kết quả được tự động kích hoạt khi đủ kết quả bắt buộc. |
| `FR-QUE-04` | Doctor Web xác định phòng từ khoa của bác sĩ, sau đó hiển thị riêng ba làn `PRIORITY`, `NORMAL`, `RESULT_REVIEW` và lượt được Queue Service đề xuất tiếp theo. |
| `FR-QUE-05` | Hệ thống không tự động gọi. Bác sĩ có thể gọi lượt được đề xuất hoặc bất kỳ entry đủ điều kiện nào trong active queue của phòng. |
| `FR-QUE-06` | Người có quyền có thể gọi lại, đánh dấu lỡ lượt và xếp lại lượt theo chính sách. |
| `FR-QUE-07` | Bệnh nhân có thể theo dõi trạng thái và vị trí tương đối của lượt hiện tại. |
| `FR-QUE-08` | Hệ thống tự tạo lượt cận lâm sàng và kích hoạt entry `CONSULTATION` phase `RESULT_REVIEW` khi đủ điều kiện. |
| `FR-QUE-09` | Gọi nhanh phải tính lại đề xuất; gọi theo hàng phải chuyển đúng Queue Entry được bác sĩ chọn sang `CALLED`. |
| `FR-QUE-10` | Khi toa được xác nhận, hệ thống tự tạo lượt `PHARMACY_DISPENSING`; nhân viên cấp phát gọi FIFO tại đúng điểm phục vụ. |

#### e. Phiên khám

| Mã | Yêu cầu |
|---|---|
| `FR-CON-01` | Bác sĩ được phân công có thể bắt đầu consultation từ Queue Entry đã được gọi. |
| `FR-CON-02` | Bác sĩ có thể ghi nhận sinh hiệu, triệu chứng, khám thực thể và chẩn đoán sơ bộ. |
| `FR-CON-03` | Consultation có thể chuyển sang chờ kết quả khi phát sinh chỉ định. |
| `FR-CON-04` | Bác sĩ có thể tiếp tục consultation khi đủ kết quả bắt buộc. |
| `FR-CON-05` | Bác sĩ có thể hoàn tất consultation sau khi đáp ứng các điều kiện nghiệp vụ. |

#### f. Cận lâm sàng

| Mã | Yêu cầu |
|---|---|
| `FR-LAB-01` | Bác sĩ được phân công có thể tạo order gồm một hoặc nhiều hạng mục. |
| `FR-LAB-02` | Hệ thống có thể ghi nhận trạng thái thanh toán/BHYT ở mức MVP. |
| `FR-LAB-03` | Order đủ điều kiện phải tự tạo lượt tại đúng service point, không check-in lần hai. |
| `FR-LAB-04` | Kỹ thuật viên có thể gọi, bắt đầu và cập nhật tiến trình thực hiện order được phân công. |
| `FR-LAB-05` | Kỹ thuật viên có thể nhập và phát hành kết quả; kết quả đã phát hành không bị ghi đè âm thầm. |
| `FR-LAB-06` | Khi đủ kết quả bắt buộc, consultation chuyển sang chờ review và hệ thống tự động tạo entry `CONSULTATION` phase `RESULT_REVIEW`. |

#### g. Toa thuốc, tái khám và thông báo

| Mã | Yêu cầu |
|---|---|
| `FR-PRE-01` | Bác sĩ được phân công có thể tạo và cập nhật toa ở trạng thái `DRAFT`. |
| `FR-PRE-02` | Bác sĩ có thể xác nhận toa hợp lệ; toa đã xác nhận không được sửa trực tiếp. |
| `FR-PRE-03` | Bệnh nhân chỉ có thể xem toa đã `CONFIRMED` hoặc `DISPENSED` của mình. |
| `FR-PRE-04` | Nhân viên cấp phát chỉ được xác nhận phát thuốc khi Queue Entry tương ứng đã được gọi, bắt đầu và thuộc đúng điểm phục vụ. |
| `FR-NOT-01` | Hệ thống tạo thông báo tại các mốc quan trọng của lịch khám, queue, xét nghiệm, toa và tái khám. |
| `FR-NOT-02` | Người dùng có thể xem lại thông báo đã lưu và đánh dấu đã đọc. |
| `FR-NOT-03` | Cập nhật realtime không được thay thế nguồn dữ liệu nghiệp vụ bền vững. |

#### h. Quản trị cấu hình

| Mã | Yêu cầu |
|---|---|
| `FR-ADM-01` | Quản trị viên có thể quản lý tài khoản bác sĩ và nhân viên. |
| `FR-ADM-02` | Quản trị viên có thể cấu hình quan hệ `Department 1:N ClinicRoom` và trạng thái hoạt động của phòng/điểm phục vụ. |
| `FR-ADM-03` | Quản trị viên có thể cấu hình lịch làm việc, khung giờ và capacity. |

### 3.1.3. Yêu cầu phi chức năng

Các yêu cầu dưới đây là mục tiêu thiết kế. Mức đạt được thực tế sẽ được kiểm thử
và đánh giá riêng, không mặc nhiên được coi là đã hoàn thành.

| Mã | Nhóm | Yêu cầu |
|---|---|---|
| `NFR-SEC-01` | Bảo mật | Mọi API nghiệp vụ, trừ đăng ký/đăng nhập/làm mới phiên, phải yêu cầu danh tính hợp lệ. |
| `NFR-SEC-02` | Bảo mật | Hệ thống phải kiểm tra cả vai trò và quyền sở hữu/assignment trước khi trả dữ liệu y tế. |
| `NFR-SEC-03` | Bảo mật | Password, token, secret và dữ liệu bệnh án nhạy cảm không được ghi vào log. |
| `NFR-SEC-04` | Bảo mật | QR không chứa trực tiếp thông tin định danh hoặc dữ liệu lâm sàng. |
| `NFR-SEC-05` | Phân quyền | Queue Service phải xác minh bác sĩ được phép truy cập `roomId` trên URL; không chỉ tin tham số do client gửi. |
| `NFR-PERF-01` | Hiệu năng | Trong môi trường demo và tải dự kiến, phần lớn yêu cầu đồng bộ thông thường nên phản hồi trong 3 giây. |
| `NFR-PERF-02` | Thời gian thực | Thay đổi gọi lượt nên được chuyển đến client đang kết nối trong vòng 5 giây. |
| `NFR-REL-01` | Tin cậy | Với sự kiện có thể được gửi lại, consumer phải nhận biết sự kiện đã xử lý để không tạo dữ liệu nghiệp vụ trùng. |
| `NFR-REL-02` | Tin cậy | Lỗi Notification không được rollback giao dịch Appointment, Queue, Lab hoặc Prescription đã thành công. |
| `NFR-REL-03` | Tin cậy | Double-click hoặc retry cùng `Idempotency-Key` không được phát lệnh gọi một Queue Entry hai lần. |
| `NFR-DATA-01` | Toàn vẹn dữ liệu | Mỗi service chỉ sửa dữ liệu thuộc quyền sở hữu của mình; không dùng khóa ngoại vật lý xuyên service. |
| `NFR-DATA-02` | Kiểm toán | Thao tác truy cập hồ sơ, nhập kết quả, xác nhận toa và sửa dữ liệu đã phát hành phải truy vết được actor và thời điểm. |
| `NFR-USE-01` | Khả dụng | Giao diện bệnh nhân phải hiển thị rõ bước hiện tại, địa điểm và hành động tiếp theo. |
| `NFR-USE-02` | Riêng tư | Màn hình công cộng chỉ hiển thị số thứ tự và phòng, không hiển thị họ tên hoặc thông tin y tế. |
| `NFR-MAIN-01` | Bảo trì | API và event phải có hợp đồng, mã lỗi và phiên bản rõ ràng. |
| `NFR-MAIN-02` | Bảo trì | Các service có thể build và triển khai tương đối độc lập khi hợp đồng không thay đổi. |
| `NFR-DEP-01` | Triển khai | Thành phần backend phải có khả năng đóng gói bằng container và cấu hình qua biến môi trường. |
| `NFR-OBS-01` | Quan sát | Yêu cầu xuyên service phải mang correlation ID để hỗ trợ theo dõi và chẩn đoán lỗi. |

### 3.1.4. Các quy tắc nghiệp vụ

| Mã | Quy tắc |
|---|---|
| `BR-APT-01` | Lịch online được tạo ở `CONFIRMED` khi ngày/slot hợp lệ và còn capacity; không cần duyệt thủ công. |
| `BR-APT-02` | Không cho phép một bệnh nhân có hai lịch chưa hủy trong cùng khung giờ. |
| `BR-APT-03` | Ca đã bắt đầu hoặc đã qua không được phép đặt. |
| `BR-APT-04` | Một `Department` có thể có nhiều `ClinicRoom`; dữ liệu MVP chỉ có đúng một phòng active cho mỗi khoa. Nếu kết quả phân phòng là 0 hoặc nhiều hơn 1, hệ thống báo lỗi cấu hình và không random/find-first. |
| `BR-QUE-01` | Visit Ticket có thể cấp trước nhưng lượt khám ban đầu chỉ vào active queue sau khi `CHECKED_IN`; lượt đọc kết quả tự active khi đủ kết quả bắt buộc. |
| `BR-QUE-02` | Mỗi phòng và phiên có ba làn logic `PRIORITY`, `NORMAL`, `RESULT_REVIEW`; FIFO theo `queuedAt` trong từng làn và đề xuất Round Robin `1:1:1`, bỏ qua làn rỗng. Khi chưa có lịch sử gọi, chu kỳ bắt đầu từ `PRIORITY`. |
| `BR-QUE-03` | Lượt `MISSED` không được giữ ở đầu queue và chỉ được xếp lại theo chính sách. |
| `BR-QUE-04` | QR chỉ chứa token tham chiếu hoặc token đã ký và phải được kiểm tra hiệu lực khi quét. |
| `BR-QUE-05` | Xem đề xuất không thay đổi trạng thái; bác sĩ được gọi lượt đề xuất hoặc một lượt `CHECKED_IN` khác bằng nút trên từng hàng. |
| `BR-QUE-06` | Lệnh gọi khóa Queue Entry được chọn, ghi người gọi và phát `PatientCalled`; không chặn gọi thêm khi đã có lượt `CALLED`/`IN_PROGRESS`. |
| `BR-QUE-07` | Lượt khám ban đầu mặc định thuộc `NORMAL`; chỉ nhân viên có quyền được xác nhận `PRIORITY` theo diện đã kiểm tra và phải lưu lý do/audit. |
| `BR-LAB-01` | Order đủ điều kiện tự tạo lượt cận lâm sàng; bệnh nhân không check-in lại tại mỗi khu. |
| `BR-LAB-02` | Kết quả đã phát hành chỉ được sửa bằng phiên bản correction có lý do và audit. |
| `BR-REV-01` | Lượt đọc kết quả thuộc consultation hiện tại và không tạo Appointment mới. |
| `BR-REV-02` | `RESULT_REVIEW` dùng một làn riêng, tự active khi đủ kết quả bắt buộc và tham gia Round Robin `1:1:1`; nếu bệnh nhân chưa có mặt thì áp dụng `MISSED/requeue`. |
| `BR-CON-01` | Chỉ bác sĩ được phân công mới cập nhật hoặc hoàn tất consultation. |
| `BR-PRE-01` | Toa `CONFIRMED` không được sửa trực tiếp; thay đổi phải tạo amendment/toa thay thế. |
| `BR-PRE-02` | Mỗi toa `CONFIRMED` có tối đa một lượt `PHARMACY_DISPENSING` đang hoạt động; lượt được gọi FIFO và hoàn tất khi toa chuyển `DISPENSED`. |
| `BR-PAT-01` | Bệnh nhân chỉ truy cập hồ sơ, kết quả và toa thuộc quyền sở hữu của mình. |
| `BR-NOT-01` | Lỗi gửi thông báo không làm thất bại giao dịch nghiệp vụ nguồn. |

Các quy tắc trên là cơ sở xác định tiền điều kiện, hậu điều kiện, luồng ngoại lệ
và transition trong các Use Case cốt lõi.

## 3.2. Mô hình Use Case tổng quát

Mô hình Use Case mô tả chức năng từ góc nhìn của tác nhân, không mô tả cấu trúc
microservice hoặc endpoint nội bộ. Danh mục đầy đủ gồm 29 Use Case; trong đó chín
Use Case cốt lõi được đặc tả chi tiết ở mục 3.3.

### 3.2.1. Biểu đồ Use Case tổng thể

Biểu đồ tổng thể thể hiện bảy tác nhân và các nhóm chức năng chính của CareFlow.
Các chức năng được gom ở mức nghiệp vụ để sơ đồ vẫn đọc được khi trình bày trên
khổ A4.

Nguồn PlantUML: [DGM-UC-01 — Use Case tổng quát](chapter-03/diagrams/use-case/dgm-uc-01-overall.puml).

### 3.2.2. Use Case phân hệ bệnh nhân

Phân hệ bệnh nhân tập trung vào quá trình trước khám, theo dõi hành trình và xem
thông tin sau khám. Bệnh nhân không trực tiếp thay đổi queue, consultation hoặc
kết quả; các trạng thái này được tải từ service sở hữu dữ liệu.

Nguồn PlantUML: [DGM-UC-02 — Phân hệ bệnh nhân](chapter-03/diagrams/use-case/dgm-uc-02-patient.puml).

### 3.2.3. Use Case phân hệ bác sĩ và nhân viên y tế

Hospital Web được dùng chung cho bác sĩ, nhân viên tiếp nhận, kỹ thuật viên cận
lâm sàng, nhân viên cấp phát thuốc và quản trị viên. Chức năng hiển thị và quyền
thao tác phụ thuộc vào vai trò đã xác thực. Biểu đồ phân hệ thể hiện sự khác biệt
trách nhiệm giữa các nhóm này.

Nguồn PlantUML: [DGM-UC-03 — Phân hệ bác sĩ và nhân viên](chapter-03/diagrams/use-case/dgm-uc-03-hospital.puml).

### 3.2.4. Danh mục Use Case

Danh mục Use Case được quản lý tại
[Danh mục Use Case CareFlow](chapter-03/01-use-case-catalog.md). Các Use Case
được chia thành ba mức:

- **Chi tiết:** có bảng đặc tả, Activity Diagram và Sequence Diagram.
- **Mở rộng:** có đặc tả và Activity Diagram, chỉ bổ sung Sequence khi cần.
- **Tóm tắt/gộp:** mô tả trong danh mục hoặc là một phần của Use Case cốt lõi.

Việc lựa chọn này giúp bao phủ đầy đủ phạm vi hệ thống nhưng không tạo số lượng
biểu đồ lặp lại quá lớn cho các thao tác CRUD đơn giản.

## 3.3. Phân tích các Use Case cốt lõi

### 3.3.1. Đăng nhập hệ thống

### 3.3.2. Đặt lịch khám

### 3.3.3. Check-in bằng mã QR

### 3.3.4. Theo dõi, đề xuất và gọi lượt khám

### 3.3.5. Bắt đầu và thực hiện phiên khám

### 3.3.6. Tạo chỉ định cận lâm sàng

### 3.3.7. Thực hiện, phát hành và đọc kết quả

### 3.3.8. Kê toa, hẹn tái khám và hoàn tất

### 3.3.9. Gọi lượt và phát thuốc

### 3.3.10. Các Use Case mở rộng

## 3.4. Thiết kế kiến trúc hệ thống

### 3.4.1. Kiến trúc tổng thể

CareFlow được thiết kế theo kiến trúc Microservices và tổ chức thành bốn nhóm
thành phần logic:

1. **Lớp trình bày:** Patient Mobile dành cho bệnh nhân và Hospital Web dành
   cho bác sĩ, nhân viên tiếp nhận, kỹ thuật viên, nhân viên cấp phát thuốc và
   quản trị viên.
2. **Lớp truy cập:** API Gateway cung cấp điểm vào chung; Eureka Server hỗ trợ
   đăng ký và phát hiện service.
3. **Lớp dịch vụ nghiệp vụ:** mỗi service sở hữu một miền nghiệp vụ và dữ liệu
   tương ứng.
4. **Lớp hạ tầng:** PostgreSQL lưu trữ dữ liệu, RabbitMQ vận chuyển event và
   WebSocket chuyển cập nhật realtime đến client.

Client không gọi trực tiếp database hoặc truy cập service bằng địa chỉ nội bộ.
Yêu cầu đồng bộ đi qua API Gateway. Các sự thật nghiệp vụ đã xảy ra được phát
thành event để giảm phụ thuộc trực tiếp giữa producer và consumer.

Nguồn PlantUML: [DGM-ARC-01 — Kiến trúc logic CareFlow](chapter-03/diagrams/architecture/dgm-arc-01-logical.puml).

### 3.4.2. Phân rã hệ thống thành các Microservice

Hệ thống được phân rã theo miền nghiệp vụ thay vì theo tầng kỹ thuật. API
Gateway, Eureka và module dùng chung là thành phần nền tảng, không phải bounded
context nghiệp vụ. Trong phạm vi báo cáo hiện tại, AI Clinical Assistant và
Analytics không được đưa vào luồng cốt lõi.

| Nhóm | Thành phần | Miền trách nhiệm |
|---|---|---|
| Nền tảng | API Gateway | Điểm vào, routing, xác thực biên và correlation ID |
| Nền tảng | Eureka Server | Đăng ký và phát hiện instance của service |
| Nghiệp vụ | Identity & eKYC Service | Tài khoản, vai trò, access/refresh token và eKYC mock |
| Nghiệp vụ | Patient Service | Hồ sơ hành chính, tiền sử khai báo và hồ sơ cũ do bệnh nhân upload |
| Nghiệp vụ | Appointment Service | Khoa, `ClinicRoom`, slot, capacity, phân phòng, lịch khám, hủy, no-show và tái khám |
| Nghiệp vụ | Queue Management Service | Visit Ticket, QR, số thứ tự, Queue Entry; ba làn phòng khám; queue FIFO tại điểm cận lâm sàng và phát thuốc |
| Nghiệp vụ | Doctor Consultation Service | Phiên khám, sinh hiệu, triệu chứng, chẩn đoán và trạng thái chờ kết quả |
| Nghiệp vụ | Laboratory Order Service | Chỉ định, hạng mục, quá trình thực hiện và kết quả cận lâm sàng |
| Nghiệp vụ | Prescription Service | Toa thuốc, dòng thuốc, xác nhận, amendment và trạng thái phát thuốc; không sở hữu hàng đợi hoặc tồn kho |
| Nghiệp vụ | EMR Service | Góc nhìn tổng hợp lịch sử bệnh nhân từ dữ liệu nguồn |
| Nghiệp vụ | Notification Service | Inbox, template, trạng thái gửi và cập nhật realtime |

Ranh giới trên giúp tránh một service nắm quá nhiều trách nhiệm. Ví dụ,
Appointment Service không cấp số thứ tự; Queue Service không sửa lịch; EMR
Service không trở thành nguồn dữ liệu thay thế Consultation, Lab hay Prescription.

Quan hệ cấu hình mục tiêu là `Department 1:N ClinicRoom`. Appointment Service
phân và lưu phòng trước khi phát `AppointmentConfirmed`; Queue Service chỉ giữ
tham chiếu/snapshot phòng để vận hành hàng đợi. Dữ liệu MVP có đúng một phòng
active mỗi khoa, nhưng schema không khóa quan hệ thành 1:1.

### 3.4.3. Trách nhiệm và dữ liệu sở hữu của từng service

Nguyên tắc Database per Service yêu cầu mỗi service chỉ sửa dữ liệu mà nó sở
hữu. Service khác truy cập thông qua REST API hoặc xây dựng projection từ event.

| Service | Dữ liệu sở hữu chính | Không sở hữu |
|---|---|---|
| Identity | User, role, trạng thái tài khoản, refresh token, eKYC mock | Patient profile, appointment và dữ liệu lâm sàng |
| Patient | Patient profile, dị ứng/tiền sử khai báo, uploaded record | Chẩn đoán, lab result và prescription chính thức |
| Appointment | Appointment, Department, ClinicRoom, room assignment, slot/capacity và follow-up | QR, queue number và active queue |
| Queue | Visit Ticket, QR token, room/service-point snapshot, Queue Entry, scheduler phòng/phiên, `lastServedLane` và chính sách xếp lượt | ClinicRoom configuration, Appointment slot, consultation, lab result, nội dung toa và tồn kho |
| Consultation | Consultation, sinh hiệu, triệu chứng, chẩn đoán và kết luận | Order result, toa thuốc và lịch hẹn |
| Laboratory Order | Order, item, payment eligibility, kết quả và correction | Active queue và chẩn đoán cuối cùng |
| Prescription | Prescription, item và trạng thái toa | Kho dược, thanh toán và quyết định chẩn đoán |
| EMR | Projection/timeline và checkpoint đồng bộ | Không sửa dữ liệu nguồn của service khác |
| Notification | Inbox, preference và delivery state | Trạng thái nghiệp vụ nguồn |

Các ID xuyên service được lưu như tham chiếu logic, không được dùng để tạo khóa
ngoại vật lý xuyên database. Khi một service cần dữ liệu chi tiết, nó gọi API
theo quyền hoặc sử dụng projection đã được xây dựng từ event.

### 3.4.4. Giao tiếp đồng bộ và bất đồng bộ

Giao tiếp đồng bộ được sử dụng cho command hoặc query mà người dùng cần biết kết
quả ngay. Ví dụ: đăng nhập, đặt lịch, check-in, gọi lượt, bắt đầu khám, tạo chỉ
định, nhập kết quả và xác nhận toa. REST API phải trả response envelope và HTTP
status phù hợp.

Giao tiếp bất đồng bộ được sử dụng để lan truyền một sự thật đã xảy ra. Producer
ghi nhận giao dịch cục bộ rồi phát event qua RabbitMQ; consumer xử lý theo cơ
chế at-least-once và phải idempotent.

| Sự kiện tiêu biểu | Producer | Consumer chính | Mục đích |
|---|---|---|---|
| `AppointmentConfirmed` | Appointment | Queue, Notification | Cấp Visit Ticket và thông báo lịch đã xác nhận |
| `AppointmentCancelled` | Appointment | Queue, Notification | Vô hiệu phiếu chưa phục vụ và báo hủy |
| `PatientCheckedIn` | Queue | Notification, Analytics tương lai | Ghi nhận bệnh nhân đã vào active queue |
| `PatientCalled` | Queue | Notification | Báo bệnh nhân đến lượt |
| `QueueEntryStarted` | Queue | Consultation/Lab | Đối chiếu quyền bắt đầu phục vụ |
| `LabOrderReadyForExecution` | Laboratory Order | Queue, Notification | Tạo lượt tại đúng service point |
| `AllRequiredResultsAvailable` | Laboratory Order | Consultation, Queue, Notification | Chuyển consultation và tạo lượt đọc kết quả |
| `PrescriptionIssued` | Prescription | Queue, Consultation, EMR, Notification | Ghi nhận toa đã phát hành và tạo lượt phát thuốc |
| `PrescriptionDispensed` | Prescription | Queue, EMR, Notification | Hoàn tất lượt phát thuốc và cập nhật hành trình |
| `ConsultationCompleted` | Consultation | EMR, Appointment, Notification | Tổng hợp hồ sơ và hoàn tất hành trình |

WebSocket chỉ dùng để chuyển cập nhật nhanh đến client. Trạng thái nghiệp vụ vẫn
phải được lưu trong service nguồn để người dùng có thể tải lại sau khi mất kết
nối hoặc đóng ứng dụng.

### 3.4.5. Mô hình triển khai ở mức logic

Ở mức logic, Patient Mobile và Hospital Web kết nối đến một API Gateway. Gateway
định tuyến đến các service đã đăng ký với Eureka. Mỗi service chạy như một tiến
trình/container riêng, sử dụng vùng dữ liệu riêng trên PostgreSQL và cùng kết
nối đến RabbitMQ khi cần phát hoặc nhận event.

Mục này chỉ xác định node và quan hệ triển khai. Tên container, port, Docker
network, biến môi trường, health check và lệnh Docker Compose được trình bày ở
Chương 4 vì đó là chi tiết hiện thực, không phải thiết kế logic.

## 3.5. Thiết kế trạng thái nghiệp vụ

State Machine được thiết kế theo aggregate sở hữu trạng thái, không theo từng
Use Case. Một Use Case có thể làm nhiều aggregate chuyển trạng thái, nhưng mỗi
transition chỉ được thực hiện bởi service sở hữu dữ liệu tương ứng.

### 3.5.1. Trạng thái lịch khám

Appointment được tạo trực tiếp ở `CONFIRMED` khi slot hợp lệ. Từ trạng thái này,
lịch có thể hoàn tất, bị hủy hoặc chuyển no-show nếu hết cửa sổ check-in.
`PENDING` chỉ là dữ liệu legacy và không thuộc vòng đời mục tiêu.

Nguồn PlantUML: [DGM-STA-01 — Appointment](chapter-03/diagrams/state/dgm-sta-01-appointment.puml).

### 3.5.2. Trạng thái lượt chờ

Queue Entry có ba loại. `CONSULTATION` phase `INITIAL` bắt đầu từ phiếu đã cấp và
cần check-in. `LAB_EXECUTION` được tạo tự động khi order đủ điều kiện.
`CONSULTATION` phase `RESULT_REVIEW` là một entry mới liên kết consultation cũ và
được tự động kích hoạt ngay khi đủ kết quả bắt buộc.
`PHARMACY_DISPENSING` được tạo khi toa chuyển `CONFIRMED`.

`QueueType` biểu diễn công đoạn phục vụ; `ConsultationPhase` phân biệt khám ban
đầu và đọc kết quả; `QueueClass` phân biệt lượt khám ban đầu `PRIORITY`/`NORMAL`;
còn `SchedulingLane` là giá trị suy ra để điều phối tại phòng khám. Ba làn
`PRIORITY`, `NORMAL`, `RESULT_REVIEW` không làm thay đổi state machine: FIFO được
giữ trong từng làn và Queue Service đề xuất theo Round Robin `1:1:1`. Queue cận
lâm sàng và phát thuốc giữ FIFO riêng tại từng điểm phục vụ.

Các trạng thái `ARRIVED` và `READY` không được tách trong MVP;
`CHECKED_IN` mang ý nghĩa bệnh nhân đã đến, đã tiếp nhận và đủ điều kiện được gọi.

Nguồn PlantUML: [DGM-STA-02 — Queue Entry](chapter-03/diagrams/state/dgm-sta-02-queue-entry.puml).

### 3.5.3. Trạng thái phiên khám

Consultation không có cận lâm sàng đi từ `NOT_STARTED` đến `IN_PROGRESS` rồi
`COMPLETED`. Nếu có chỉ định, consultation tạm dừng ở `WAITING_FOR_RESULTS`, sau
đó chuyển `WAITING_FOR_REVIEW` khi đủ kết quả và quay lại `IN_PROGRESS` khi bác
sĩ tiếp tục đọc kết quả. `COMPLETED` là trạng thái kết thúc; chỉnh sửa sau hoàn
tất phải tạo amendment có lý do và audit.

Nguồn PlantUML: [DGM-STA-03 — Consultation](chapter-03/diagrams/state/dgm-sta-03-consultation.puml).

### 3.5.4. Trạng thái chỉ định cận lâm sàng

Laboratory Order được tạo ở `ORDERED`. Nếu cần xác nhận thanh toán/BHYT, order
chuyển `PAYMENT_PENDING`; khi đủ điều kiện, nó chuyển `QUEUED`. Kỹ thuật viên gọi,
bắt đầu và phát hành kết quả. Trạng thái `QUEUED`, `CALLED` và `MISSED` được đồng
bộ từ Queue, trong khi order vẫn là nguồn sự thật của nội dung chỉ định và kết
quả.

Nguồn PlantUML: [DGM-STA-04 — Laboratory Order](chapter-03/diagrams/state/dgm-sta-04-laboratory-order.puml).

### 3.5.5. Trạng thái toa thuốc

Toa được tạo dưới dạng `DRAFT` để bác sĩ kiểm tra và chỉnh sửa. Sau khi xác nhận,
toa chuyển `CONFIRMED` và được phép hiển thị cho bệnh nhân. Toa đã xác nhận không
được sửa trực tiếp; nếu thay đổi phải hủy bằng amendment và tạo toa thay thế để
giữ lịch sử. Khi toa `CONFIRMED`, Queue Service tạo lượt
`PHARMACY_DISPENSING`. Nhân viên chỉ xác nhận phát thuốc sau khi lượt đã được gọi
và bắt đầu; `DISPENSED` hoàn tất lượt tương ứng. Quy trình này không thay thế hệ
thống quản lý kho dược.

Nguồn PlantUML: [DGM-STA-05 — Prescription](chapter-03/diagrams/state/dgm-sta-05-prescription.puml).
## 3.6. Thiết kế dữ liệu

### 3.6.1. Nguyên tắc phân chia và sở hữu dữ liệu

### 3.6.2. Mô hình dữ liệu của các service chính

### 3.6.3. Quan hệ dữ liệu xuyên service

### 3.6.4. Mô tả các thực thể dữ liệu chính

## 3.7. Thiết kế giao tiếp hệ thống

### 3.7.1. Hợp đồng REST API

### 3.7.2. Hợp đồng sự kiện RabbitMQ

### 3.7.3. Cập nhật thời gian thực qua WebSocket

## 3.8. Thiết kế bảo mật và phân quyền

### 3.8.1. Xác thực và quản lý phiên

### 3.8.2. Phân quyền theo vai trò

### 3.8.3. Kiểm soát quyền sở hữu dữ liệu

### 3.8.4. Bảo vệ mã QR và dữ liệu y tế

## 3.9. Kết luận chương
