# CHƯƠNG 6. KẾT LUẬN VÀ ĐÁNH GIÁ

Chương này tổng kết kết quả xây dựng hệ thống CareFlow, đánh giá mức độ đáp ứng mục tiêu đề tài, nêu các ưu điểm, hạn chế và hướng phát triển trong tương lai.

## 6.1. Kết quả đạt được

### 6.1.1. Kết quả về nghiệp vụ

CareFlow đã mô hình hóa và hỗ trợ các bước chính trong quy trình khám ngoại trú, gồm:

- Quản lý hồ sơ bệnh nhân.
- Đặt lịch và nhận phiếu khám.
- Check-in bằng QR do bệnh viện hiển thị.
- Kiểm tra vị trí bệnh nhân trong bán kính bệnh viện.
- Theo dõi và điều phối hàng đợi.
- Khám lâm sàng, chỉ định cận lâm sàng và đọc kết quả.
- Kê toa, hẹn tái khám và phát thuốc.
- Thanh toán trước phí khám và quyết toán cuối lượt.

Hệ thống cũng hỗ trợ ba làn khám ban đầu gồm `PRIORITY`, `NORMAL` và `RESULT_REVIEW`, được điều phối theo cơ chế Round Robin `1:1:1`.

### 6.1.2. Kết quả về kiến trúc và kỹ thuật

Hệ thống được xây dựng theo kiến trúc Microservices, trong đó các chức năng được phân tách thành những service có trách nhiệm riêng như Appointment, Queue, Consultation, Laboratory, Prescription và Notification.

CareFlow sử dụng API Gateway để tiếp nhận yêu cầu, JWT để xác thực và phân quyền, RabbitMQ để trao đổi sự kiện giữa các service. Patient Mobile và Hospital Web được thiết kế phù hợp với vai trò của bệnh nhân và nhân viên bệnh viện.

### 6.1.3. Kết quả về trải nghiệm người dùng

Patient Mobile giúp bệnh nhân chủ động đặt lịch, theo dõi phiếu khám, quét QR khi đến bệnh viện và nhận thông báo. Hospital Web hỗ trợ nhân viên theo dõi hàng đợi, xem lượt được đề xuất, gọi bệnh nhân và cập nhật thông tin khám.

Việc tách QR hiển thị tại bệnh viện khỏi phiếu khám cá nhân giúp hạn chế việc check-in từ xa và phù hợp hơn với quy trình thực tế.

## 6.2. Đánh giá hệ thống

### 6.2.1. Ưu điểm

- Quy trình nghiệp vụ được mô hình hóa xuyên suốt từ đặt lịch đến phát thuốc.
- Kiến trúc Microservices giúp các chức năng có thể phát triển và mở rộng độc lập.
- Cơ chế hàng đợi hỗ trợ nhiều nhóm bệnh nhân và hạn chế việc một làn bị phục vụ quá lâu.
- QR check-in kết hợp geofence giúp xác nhận bệnh nhân thực sự đã đến bệnh viện.
- Phân quyền được tách theo vai trò bệnh nhân, bác sĩ, nhân viên và quản lý.
- Hệ thống có thể mở rộng cho nhiều phòng khám, khoa và điểm phục vụ.

### 6.2.2. Hạn chế

- Phiên bản hiện tại chủ yếu tập trung vào người bệnh tự chi trả, chưa xử lý đầy đủ nghiệp vụ bảo hiểm y tế.
- Thanh toán trực tuyến và quyết toán cuối lượt mới dừng ở mức mô phỏng/adaptor.
- Dữ liệu cận lâm sàng, kho thuốc và hệ thống bệnh viện bên ngoài chưa được tích hợp đầy đủ.
- Cấu hình MVP hiện chỉ sử dụng một phòng hoạt động cho mỗi khoa.
- Chức năng phân tích dữ liệu và hỗ trợ AI chưa nằm trong phạm vi hiện tại.

## 6.3. Mức độ đáp ứng mục tiêu

| Mục tiêu | Mức độ đáp ứng |
|---|---|
| Số hóa quy trình khám ngoại trú | Đáp ứng |
| Quản lý lịch khám và phiếu khám | Đáp ứng |
| Check-in bằng QR và xác nhận vị trí | Đáp ứng trong phạm vi MVP |
| Điều phối hàng đợi nhiều làn | Đáp ứng |
| Hỗ trợ bác sĩ và nhân viên bệnh viện | Đáp ứng |
| Tích hợp thanh toán production | Chưa đầy đủ |
| Tích hợp HIS, PACS và hệ thống dược thực tế | Chưa thực hiện |
| Phân tích dữ liệu và AI | Chưa thuộc phạm vi |

## 6.4. Hướng phát triển

Trong tương lai, CareFlow có thể được phát triển theo các hướng:

- Tích hợp với HIS, PACS, cổng thanh toán và hệ thống bảo hiểm y tế.
- Hỗ trợ nhiều phòng khám và nhiều điểm phục vụ đồng thời.
- Hoàn thiện nghiệp vụ kho thuốc và phát thuốc.
- Bổ sung báo cáo thống kê và phân tích hiệu suất bệnh viện.
- Cải thiện cơ chế xác nhận vị trí và chống giả lập GPS.
- Phát triển chức năng AI hỗ trợ bác sĩ khi có đủ dữ liệu và phạm vi phù hợp.

## 6.5. Kết luận

Đề tài đã xây dựng được mô hình CareFlow nhằm hỗ trợ số hóa quy trình khám ngoại trú tại bệnh viện. Hệ thống tập trung giải quyết các vấn đề thường gặp như đăng ký khám, xác nhận bệnh nhân đã đến, quản lý hàng đợi, phối hợp giữa các bộ phận và theo dõi kết quả khám.

Mặc dù còn một số giới hạn về tích hợp hệ thống bên ngoài, bảo hiểm và thanh toán thực tế, CareFlow đã tạo được nền tảng phù hợp để tiếp tục mở rộng thành một hệ thống quản lý quy trình khám chữa bệnh hoàn chỉnh.
