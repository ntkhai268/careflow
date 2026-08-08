# CHƯƠNG 1. GIỚI THIỆU ĐỀ TÀI

## 1.1. Mục đích

Khám bệnh ngoại trú tại bệnh viện công thường có sự tham gia của nhiều bộ phận,
từ tiếp nhận, phòng khám, khu cận lâm sàng đến nhà thuốc. Thông tin của một
lượt khám vì vậy được tạo ra và cập nhật ở nhiều thời điểm khác nhau. Nếu thiếu
một công cụ điều phối thống nhất, bệnh nhân khó biết bước tiếp theo, nhân viên
khó theo dõi tình trạng phục vụ, còn bác sĩ phải xử lý nhiều thao tác phối hợp
ngoài hoạt động chuyên môn.

Đề tài nhằm xây dựng CareFlow — hệ thống phần mềm hỗ trợ và điều phối hành trình
khám ngoại trú tại bệnh viện công. Bệnh nhân, bác sĩ, nhân viên tiếp nhận, kỹ
thuật viên cận lâm sàng và nhân viên cấp phát thuốc cùng được hưởng lợi từ một
quy trình có trạng thái rõ ràng và thông tin liên tục. CareFlow hỗ trợ hành trình từ
đặt khám, cấp phiếu và mã QR, check-in, xếp hàng, khám lâm sàng, cận lâm sàng,
đọc kết quả, kê toa, phát thuốc đến lưu lại kết quả và lịch tái khám.

Mục đích của đề tài không phải thay thế toàn bộ hệ thống thông tin bệnh viện,
mà xây dựng một mô hình có thể minh họa cách số hóa và liên kết các công đoạn
khám ngoại trú. Luồng tự chi trả trên Patient Mobile được trình bày ở mức MVP
qua trả trước phí khám và quyết toán cuối lượt; thanh toán và hoàn tiền
production không thuộc phạm vi đề tài.

## 1.2. Mục tiêu

### 1.2.1. Mục tiêu tổng quát

Phân tích, thiết kế và xây dựng hệ thống CareFlow hỗ trợ số hóa hành trình khám
ngoại trú tại bệnh viện công theo kiến trúc Microservices. Hệ thống hướng đến
việc làm cho trạng thái phục vụ minh bạch hơn đối với bệnh nhân, đồng thời hỗ
trợ bác sĩ và nhân viên y tế phối hợp các bước nghiệp vụ trên cùng một quy trình.

### 1.2.2. Mục tiêu cụ thể

1. Khảo sát và mô hình hóa quy trình khám ngoại trú, các vai trò tham gia và
   những vấn đề phát sinh trong cách vận hành hiện tại.
2. Xác định mô hình vận hành đề xuất có CareFlow tham gia, bao gồm các quy tắc
   về lịch khám, phòng khám, phiếu khám, mã QR và các điểm phục vụ.
3. Xây dựng chức năng quản lý hồ sơ bệnh nhân, đặt lịch, chọn dịch vụ khám,
   hiển thị lựa chọn thanh toán MVP và cấp phiếu khám điện tử.
4. Xây dựng cơ chế quản lý hàng đợi gồm các làn `PRIORITY`, `NORMAL` và
   `RESULT_REVIEW`, giữ FIFO trong từng làn và đề xuất lượt theo Round Robin
   `1:1:1`; bác sĩ là người chủ động bấm gọi.
5. Hỗ trợ các giai đoạn khám lâm sàng, tạo và thực hiện chỉ định cận lâm sàng,
   phát hành kết quả, đưa bệnh nhân quay lại đọc kết quả, kê toa, hẹn tái khám
   và phát thuốc theo hàng đợi FIFO.
6. Tích hợp các miền nghiệp vụ thông qua REST API, sự kiện bất đồng bộ và cập
   nhật thời gian thực; bảo đảm phân quyền, quyền sở hữu dữ liệu,
   idempotency, audit và xử lý lỗi phù hợp với hệ thống phân tán.
7. Xây dựng giao diện Patient Mobile và Hospital Web theo hành động tiếp theo
   của từng vai trò, đồng thời hỗ trợ thông báo và theo dõi timeline của lượt
   khám.
8. Kiểm thử các Use Case, trạng thái, API, sự kiện, dữ liệu và yêu cầu phi chức
   năng; đánh giá mức đáp ứng mục tiêu và nêu các giới hạn của phiên bản MVP.

## 1.3. Phương pháp tiến hành

### 1.3.1. Khảo sát hiện trạng

Đề tài bắt đầu bằng việc khảo sát quy trình khám ngoại trú và xác định các vai
trò nghiệp vụ thực tế như bệnh nhân, bác sĩ, nhân viên tiếp nhận, kỹ thuật viên
cận lâm sàng, nhân viên cấp phát thuốc và bộ phận quản lý bệnh viện. Quy trình hiện tại được
mô tả theo dòng công việc và các tương tác giữa các vai trò, tập trung vào các
vấn đề như chờ đợi, cập nhật trạng thái chậm, phối hợp giữa các điểm phục vụ và
khó theo dõi kết quả của một lượt khám.

### 1.3.2. Tìm hiểu nghiệp vụ và quy định

Các quy tắc nghiệp vụ được tổng hợp từ hành trình khám đã thống nhất và các
contract của service. Nội dung tìm hiểu gồm điều kiện đặt và hủy lịch, quan hệ
`Department 1:N ClinicRoom`, cấp phiếu và check-in, điều phối ba làn queue,
chuyển tiếp sang cận lâm sàng, tự động tạo lượt `RESULT_REVIEW`, tạo lượt phát
thuốc, quyền truy cập hồ sơ và quy tắc xử lý các trạng thái `MISSED` hoặc
`CANCELLED`.

### 1.3.3. Nghiên cứu mô hình, phương pháp, giải thuật và công nghệ

Đề tài áp dụng phân tích và thiết kế hướng đối tượng với UML để biểu diễn tác
nhân, Use Case, tương tác, trạng thái và các thành phần của hệ thống. Kiến trúc
Microservices được nghiên cứu theo ranh giới nghiệp vụ và quyền sở hữu dữ liệu.
REST API, RabbitMQ, WebSocket, PostgreSQL, Java/Spring Boot, Flutter và các
công cụ container hóa được lựa chọn dựa trên vai trò cụ thể trong CareFlow; cơ sở
triết lý và lý do lựa chọn được trình bày ở Chương 2.

### 1.3.4. Phân tích, thiết kế, hiện thực, kiểm thử và đánh giá

Sau khi xác định phạm vi và quy tắc, đề tài lập danh mục yêu cầu, mô tả các
tình huống phần mềm tham gia giải quyết và xây dựng các biểu đồ phân tích. Từ
đó, hệ thống được thiết kế theo lớp giao diện, xử lý/API và thực thể/dữ liệu;
kiến trúc, cơ sở dữ liệu, giao tiếp và các cơ chế đáp ứng yêu cầu chất lượng
được xác định trước khi hiện thực. Phiên bản phần mềm được trình bày theo các
Use Case đã phân tích, sau đó được kiểm thử theo ma trận truy vết và đánh giá
trên các tiêu chí chức năng, tin cậy, bảo mật, hiệu năng và khả năng bảo trì.

## 1.4. Phạm vi đề tài

### 1.4.1. Phạm vi thực hiện

Phạm vi của CareFlow là hành trình khám ngoại trú tại bệnh viện công, bắt đầu
từ đăng ký và quản lý hồ sơ, tra cứu khoa và khung giờ, chọn dịch vụ, đặt lịch,
nhận phiếu khám và mã QR. Hành trình tiếp tục qua check-in, quản lý queue tại
phòng khám, khám lâm sàng, chỉ định và thực hiện cận lâm sàng, phát hành kết
quả, quay lại bác sĩ đọc kết quả, kê toa, hẹn tái khám và phát thuốc.

Các điểm phục vụ được mô hình hóa đủ để một khoa có thể có nhiều phòng, trong
khi dữ liệu MVP chỉ cấu hình một phòng active cho mỗi khoa. Queue phòng khám
gồm ba làn logic `PRIORITY`, `NORMAL` và `RESULT_REVIEW`; queue cận lâm sàng và
phát thuốc giữ FIFO riêng theo điểm phục vụ.

Các đối tượng sử dụng trong phạm vi thực hiện gồm:

- **Bệnh nhân:** sử dụng Patient Mobile để quản lý hồ sơ, đặt khám, xem phiếu,
  theo dõi queue, nhận hướng dẫn, xem kết quả, toa và lịch tái khám.
- **Bác sĩ:** sử dụng Hospital Web để xem queue, gọi lượt, thực hiện phiên khám,
  tạo chỉ định, đọc kết quả, kê toa và hoàn tất lượt khám.
- **Nhân viên tiếp nhận:** quét QR, xác nhận bệnh nhân có mặt và hỗ trợ các
  trường hợp lỡ lượt.
- **Kỹ thuật viên cận lâm sàng:** theo dõi order, gọi lượt, thực hiện kỹ thuật
  và phát hành kết quả.
- **Nhân viên cấp phát thuốc:** theo dõi queue FIFO, đối chiếu toa và xác nhận
  đã phát thuốc.
- **Bộ phận quản lý bệnh viện:** duy trì khoa, phòng, lịch làm việc, capacity
  và điểm phục vụ ở mức nghiệp vụ.

### 1.4.2. Phần mô phỏng

Patient Mobile mô phỏng bước chọn và ghi nhận phí khám bằng fixture dịch vụ
`GENERAL_CONSULTATION` với thanh toán bắt buộc `ONLINE_MOCK`. Ở cuối lượt,
adapter demo tổng hợp phí khám, cận lâm sàng
và thuốc, đối trừ khoản đã trả trước rồi trình bày `PAYMENT_DUE`, `SETTLED`,
`REFUND_PENDING` hoặc `REFUNDED`. Phạm vi chỉ xét người bệnh tự chi trả. Các
trạng thái này minh họa trải nghiệm nhưng không phải giao dịch production.

### 1.4.3. Phần ngoài phạm vi

Phiên bản báo cáo không triển khai đầy đủ cấp cứu, điều trị nội trú, quản lý
giường bệnh, kho dược và tồn kho, PACS/DICOM, cổng thanh toán/hoàn tiền
production, catalog giá production, SMS và email.

Trong báo cáo, các chức năng thuộc phạm vi MVP được xem là phiên bản hoàn chỉnh
theo thiết kế và contract đã chốt. Các giới hạn nêu trên là giới hạn phạm vi
nghiệp vụ, không phải lỗi thiếu sót của quá trình hiện thực.
