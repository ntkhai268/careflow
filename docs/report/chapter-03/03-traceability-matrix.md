# Ma trận truy vết Giai đoạn 2

Tài liệu này liên kết Use Case cốt lõi với yêu cầu, quy tắc, aggregate và service
để các nhóm viết chi tiết ở Giai đoạn 3 không tự thay đổi phạm vi.

## 1. Use Case cốt lõi

| Use Case | Yêu cầu chính | Quy tắc chính | Aggregate bị tác động | Service tham gia chính |
|---|---|---|---|---|
| `UC-AUTH-02` Đăng nhập | `FR-AUTH-02`, `FR-AUTH-03` | — | User, RefreshToken | Gateway, Identity |
| `UC-APT-02` Đặt lịch | `FR-PAT-01`, `FR-APT-01..03`, `FR-APT-06`, `FR-QUE-01` | `BR-APT-01`, `BR-APT-02`, `BR-APT-03`, `BR-APT-04`, `BR-PAT-01` | Patient, Department, ClinicRoom, Appointment, Visit Ticket | Patient, Appointment, Queue, Notification |
| `UC-QUE-02` Check-in QR | `FR-QUE-02`, `FR-QUE-03` | `BR-QUE-01`, `BR-QUE-04` | Appointment, Queue Entry | Queue, Appointment, Notification |
| `UC-QUE-04` Theo dõi/đề xuất/gọi lượt | `FR-QUE-03..09`, `NFR-SEC-05` | `BR-QUE-02`, `BR-QUE-03`, `BR-QUE-05`, `BR-QUE-06`, `BR-QUE-07`, `BR-REV-02` | ClinicRoom reference, Queue Entry, Queue Scheduler | Appointment, Queue, Notification |
| `UC-CON-01` Thực hiện phiên khám | `FR-CON-01..05`, `FR-PAT-03` | `BR-CON-01`, `BR-PAT-01` | Queue Entry, Consultation | Queue, Consultation, Patient, EMR |
| `UC-LAB-01` Tạo chỉ định | `FR-LAB-01..03`, `FR-CON-03` | `BR-LAB-01`, `BR-CON-01` | Consultation, Laboratory Order, Queue Entry | Consultation, Laboratory Order, Queue, Notification |
| `UC-LAB-03` Thực hiện/phát hành/đọc kết quả | `FR-LAB-04..06`, `FR-CON-04`, `FR-QUE-08` | `BR-LAB-02`, `BR-REV-01`, `BR-REV-02` | Laboratory Order, Consultation, Queue Entry | Laboratory Order, Queue, Consultation, EMR, Notification |
| `UC-PRE-01` Kê toa/tái khám/hoàn tất | `FR-PRE-01..03`, `FR-APT-05`, `FR-CON-05` | `BR-PRE-01`, `BR-CON-01` | Prescription, Consultation, Appointment, Queue Entry | Prescription, Consultation, Appointment, Queue, EMR, Notification |
| `UC-PHA-01` Gọi lượt và phát thuốc | `FR-QUE-10`, `FR-PRE-03`, `FR-PRE-04` | `BR-PRE-01`, `BR-PRE-02`, `BR-QUE-03` | Prescription, Queue Entry, Service Point reference | Prescription, Queue, Notification |

Ký hiệu `FR-XXX-01..03` nghĩa là toàn bộ dải yêu cầu từ 01 đến 03 trong cùng
nhóm, không phải một mã yêu cầu mới.

## 2. Tác nhân và phân hệ

| Tác nhân | Giao diện | Use Case chi tiết liên quan |
|---|---|---|
| Bệnh nhân | Patient Mobile | `UC-AUTH-02`, `UC-APT-02`; nhận trạng thái từ các Use Case queue/lâm sàng |
| Bác sĩ | Hospital Web | `UC-AUTH-02`, `UC-QUE-04`, `UC-CON-01`, `UC-LAB-01`, `UC-LAB-03`, `UC-PRE-01` |
| Nhân viên tiếp nhận | Hospital Web | `UC-AUTH-02`, `UC-QUE-02`, `UC-QUE-04` |
| Kỹ thuật viên cận lâm sàng | Hospital Web | `UC-AUTH-02`, `UC-LAB-03` |
| Nhân viên cấp phát thuốc | Hospital Web | `UC-AUTH-02`, `UC-PHA-01` |
| Quản trị viên | Hospital Web | Đăng nhập và các Use Case cấu hình được mô tả tóm tắt |
| Thu ngân/hệ thống thanh toán ngoài | Hospital Web hoặc mock | Luồng thay thế của `UC-LAB-01` |

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
