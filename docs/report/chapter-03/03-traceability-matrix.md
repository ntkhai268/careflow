# Ma trận truy vết Giai đoạn 2

Tài liệu này liên kết Use Case cốt lõi với yêu cầu, quy tắc, aggregate và service
để các nhóm viết chi tiết ở Giai đoạn 3 không tự thay đổi phạm vi.

## 1. Use Case cốt lõi

| Use Case | Yêu cầu chính | Quy tắc chính | Aggregate bị tác động | Service tham gia chính |
|---|---|---|---|---|
| `UC-APT-02` Đặt lịch | `FR-PAT-01`, `FR-APT-01..03`, `FR-APT-06`, `FR-MOB-01..03`, `FR-QUE-01` | `BR-APT-01`, `BR-APT-02`, `BR-APT-03`, `BR-APT-04`, `BR-MOB-01`, `BR-MOB-02`, `BR-PAT-01` | Patient, Department, ClinicRoom, Appointment, Visit Ticket, AppointmentPaymentReceipt | Patient, Appointment, Queue, Notification, Mobile local adapter |
| `UC-QUE-02` Check-in QR | `FR-QUE-02`, `FR-QUE-03` | `BR-QUE-01`, `BR-QUE-04` | Appointment, Queue Entry | Queue, Appointment, Notification |
| `UC-QUE-04` Theo dõi/đề xuất/gọi lượt | `FR-QUE-03..09`, `NFR-SEC-05` | `BR-QUE-02`, `BR-QUE-03`, `BR-QUE-05`, `BR-QUE-06`, `BR-QUE-07`, `BR-REV-02` | ClinicRoom reference, Queue Entry, Queue Scheduler | Appointment, Queue, Notification |
| `UC-CON-01` Thực hiện phiên khám | `FR-CON-01..05`, `FR-PAT-03` | `BR-CON-01`, `BR-PAT-01` | Queue Entry, Consultation | Queue, Consultation, Patient, EMR |
| `UC-LAB-01` Tạo chỉ định | `FR-LAB-01..03`, `FR-CON-03` | `BR-LAB-01`, `BR-CON-01` | Consultation, Laboratory Order, Queue Entry | Consultation, Laboratory Order, Queue, Notification |
| `UC-LAB-03` Thực hiện/phát hành/đọc kết quả | `FR-LAB-04..06`, `FR-CON-04`, `FR-QUE-08` | `BR-LAB-02`, `BR-REV-01`, `BR-REV-02` | Laboratory Order, Consultation, Queue Entry | Laboratory Order, Queue, Consultation, EMR, Notification |
| `UC-PRE-01` Kê toa/tái khám/hoàn tất | `FR-PRE-01..03`, `FR-MOB-04`, `FR-APT-05`, `FR-CON-05` | `BR-PRE-01`, `BR-MOB-03`, `BR-CON-01` | Prescription, Consultation, Appointment, Queue Entry | Prescription, Consultation, Appointment, Queue, EMR, Notification |
| `UC-PHA-01` Gọi lượt và phát thuốc | `FR-QUE-10`, `FR-PRE-03`, `FR-PRE-04`, `FR-PAY-01..04` | `BR-PRE-01`, `BR-PRE-02`, `BR-QUE-03`, `BR-PAY-01..03` | Prescription, Queue Entry, VisitSettlement, Service Point reference | Prescription, Queue, Notification, Mobile settlement adapter |

Ký hiệu `FR-XXX-01..03` nghĩa là toàn bộ dải yêu cầu từ 01 đến 03 trong cùng
nhóm, không phải một mã yêu cầu mới.

`FR-AUTH-*` và các yêu cầu phân quyền được truy vết như yêu cầu vận hành/bảo
mật đến thiết kế Gateway–Identity và nhóm kiểm thử API/NFR; chúng không tạo một
Use Case nghiệp vụ riêng.

## 2. Tác nhân và phân hệ

| Tác nhân | Giao diện | Use Case chi tiết liên quan |
|---|---|---|
| Bệnh nhân | Patient Mobile | `UC-APT-02`; nhận trạng thái từ các Use Case queue/lâm sàng và xem quyết toán demo |
| Bác sĩ | Hospital Web | `UC-QUE-04`, `UC-CON-01`, `UC-LAB-01`, `UC-LAB-03`, `UC-PRE-01` |
| Nhân viên tiếp nhận | Hospital Web | `UC-QUE-02`, `UC-QUE-04` |
| Kỹ thuật viên cận lâm sàng | Hospital Web | `UC-LAB-03` |
| Nhân viên cấp phát thuốc | Hospital Web | `UC-PHA-01` |
| Bộ phận quản lý bệnh viện | Hospital Web | Các Use Case duy trì cấu hình nghiệp vụ được mô tả tóm tắt |
| Thu ngân/hệ thống thanh toán ngoài | Hospital Web, hệ thống ngoài hoặc mock | `UC-PAY-01` hỗ trợ `UC-APT-02` và `UC-PHA-01`; Lab chỉ cộng chi phí vào quyết toán cuối lượt |

## 3. Quy tắc kiểm soát khi viết chi tiết

1. Mọi hậu điều kiện phải ánh xạ được tới một trạng thái trong State Diagram.
2. Mọi service xuất hiện trong Sequence Diagram phải có trách nhiệm tương ứng ở
   mục 3.4.2–3.4.3.
3. REST command phải ánh xạ tới một yêu cầu cần kết quả trực tiếp; sự thật đã
   xảy ra phải dùng event.
4. Phải thể hiện ít nhất một luồng lỗi về quyền sở hữu đối với Use Case có dữ
   liệu bệnh nhân.
5. Phải ghi rõ `TARGET_DESIGN` hoặc `DEMO_MOCK` khi mô tả phần chưa có
   implementation đầy đủ trên `develop`.
