# Danh mục Use Case CareFlow

> Danh mục chuẩn cho Chương 3. Use Case được mô tả theo mục tiêu của tác nhân,
> không ánh xạ máy móc mỗi endpoint thành một Use Case.

## 1. Danh mục tổng quát

| Mã | Tên Use Case | Tác nhân chính | Mức trình bày |
|---|---|---|---|
| `UC-PAT-01` | Quản lý hồ sơ bệnh nhân | Bệnh nhân | Mở rộng |
| `UC-PAT-02` | Tải lên và quản lý hồ sơ cũ | Bệnh nhân | Tóm tắt |
| `UC-PAT-03` | Tra cứu hồ sơ được phân công | Bác sĩ | Tóm tắt |
| `UC-APT-01` | Tra cứu khoa và khung giờ | Bệnh nhân | Gộp vào `UC-APT-02` |
| `UC-APT-02` | Đặt lịch khám | Bệnh nhân | **Chi tiết** |
| `UC-APT-03` | Xem danh sách và chi tiết lịch | Bệnh nhân | Tóm tắt |
| `UC-APT-04` | Hủy lịch khám | Bệnh nhân/Nhân viên tiếp nhận | Mở rộng |
| `UC-APT-05` | Tạo lịch tái khám | Bác sĩ | Gộp vào `UC-PRE-01` |
| `UC-QUE-01` | Xem phiếu khám và thông tin check-in | Bệnh nhân | Gộp vào `UC-APT-02` |
| `UC-QUE-02` | Check-in bằng QR tại bệnh viện | Bệnh nhân/Nhân viên hỗ trợ | **Chi tiết** |
| `UC-QUE-03` | Theo dõi lượt chờ | Bệnh nhân | Gộp vào `UC-QUE-04` |
| `UC-QUE-04` | Theo dõi, đề xuất và gọi lượt khám | Bác sĩ | **Chi tiết** |
| `UC-QUE-05` | Gọi lại, đánh dấu lỡ lượt và xếp lại | Bác sĩ/Nhân viên tiếp nhận | Gộp vào `UC-QUE-04` |
| `UC-CON-01` | Bắt đầu và thực hiện phiên khám | Bác sĩ | **Chi tiết** |
| `UC-CON-02` | Cập nhật sinh hiệu và thông tin lâm sàng | Bác sĩ | Gộp vào `UC-CON-01` |
| `UC-LAB-01` | Tạo chỉ định cận lâm sàng | Bác sĩ | **Chi tiết** |
| `UC-PAY-01` | Thanh toán và quyết toán lượt khám | Thu ngân/hệ thống thanh toán ngoài | Tóm tắt; hỗ trợ hai giai đoạn |
| `UC-LAB-03` | Thực hiện và phát hành kết quả | Kỹ thuật viên cận lâm sàng | **Chi tiết** |
| `UC-LAB-04` | Quay lại bác sĩ đọc kết quả | Bác sĩ | Gộp vào `UC-LAB-03` |
| `UC-PRE-01` | Kê toa, hẹn tái khám và hoàn tất | Bác sĩ | **Chi tiết** |
| `UC-PRE-02` | Xem toa thuốc và kết quả sau khám | Bệnh nhân | Tóm tắt |
| `UC-PHA-01` | Gọi lượt và phát thuốc | Nhân viên cấp phát thuốc | **Chi tiết** |
| `UC-NOT-01` | Xem và đánh dấu thông báo | Bệnh nhân | Tóm tắt |
| `UC-CFG-01` | Duy trì khoa, phòng và điểm phục vụ | Bộ phận quản lý bệnh viện | Tóm tắt/thiết kế mục tiêu |
| `UC-CFG-02` | Duy trì lịch làm việc, slot và capacity | Bộ phận quản lý bệnh viện | Tóm tắt/thiết kế mục tiêu |

Xác thực, làm mới phiên và phân quyền là yêu cầu nền tảng/tiền điều kiện. Chúng
được thiết kế và kiểm thử ở phần bảo mật, không được dùng làm Use Case nghiệp
vụ cốt lõi.

## 2. Tám Use Case cốt lõi trình bày đầy đủ

| Thứ tự | Mã | Use Case | Đặc tả | Activity | Sequence |
|---:|---|---|:---:|:---:|:---:|
| 1 | `UC-APT-02` | Đặt lịch khám | Có | Có | Có |
| 2 | `UC-QUE-02` | Check-in bằng QR tại bệnh viện | Có | Có | Có |
| 3 | `UC-QUE-04` | Theo dõi, đề xuất và gọi lượt khám | Có | Có | Có |
| 4 | `UC-CON-01` | Bắt đầu và thực hiện phiên khám | Có | Có | Có |
| 5 | `UC-LAB-01` | Tạo chỉ định cận lâm sàng | Có | Có | Có |
| 6 | `UC-LAB-03` | Thực hiện, phát hành và đọc kết quả | Có | Có | Có |
| 7 | `UC-PRE-01` | Kê toa, hẹn tái khám và hoàn tất | Có | Có | Có |
| 8 | `UC-PHA-01` | Gọi lượt và phát thuốc | Có | Có | Có |

Hai Use Case mở rộng `UC-PAT-01` và `UC-APT-04` có bảng đặc tả và Activity
Diagram nhưng không bắt buộc Sequence Diagram riêng nếu lời gọi đã được thể
hiện trong luồng khác.

## 3. Phạm vi của từng Use Case cốt lõi

Use Case hỗ trợ `UC-PAY-01` được gọi tại `UC-APT-02` để ghi nhận phí khám trả
trước và tại `UC-PHA-01` để quyết toán cuối lượt trước khi phát thuốc. Cận lâm
sàng chỉ ghi nhận chi phí, không gọi Use Case thanh toán. Adapter demo mô phỏng
trạng thái; Use Case này không làm tăng số Use Case cốt lõi cần vẽ chi tiết.

### `UC-APT-02` — Đặt lịch khám

- Bao gồm chọn hồ sơ, khoa, ngày, slot, dịch vụ, lý do và xác nhận.
- Trên Patient Mobile, bệnh nhân chọn fixture `GENERAL_CONSULTATION`/“Khám
  thường” giá demo `150.000 ₫` và ghi nhận `ONLINE_MOCK` trước khi hoàn tất đặt
  lịch.
- Nếu sau khám còn `amountDue`, thu ngân xác nhận khoản quyết toán cuối lượt;
  khoản này không phải lựa chọn thanh toán trong booking.
- Bệnh nhân không chọn phòng. Appointment Service truy vấn `ClinicRoom` active
  của khoa và, theo chính sách MVP, chỉ tiếp tục khi có đúng một phòng phù hợp.
- Kết thúc: Appointment lưu `roomId`, chuyển `CONFIRMED`, sau đó phát sự kiện cấp
  Visit Ticket cho đúng phòng.
- Appointment Service chưa nhận payment/service contract. Mobile lưu
  `AppointmentPaymentReceipt` qua local adapter; lỗi local không làm hỏng lịch
  hẹn đã tạo thành công.
- Ngoại lệ: ca đã qua, trùng lịch, hết capacity, không sở hữu hồ sơ hoặc cấu hình
  phòng trả 0/nhiều hơn 1 kết quả trong MVP.

### `UC-QUE-02` — Check-in bằng QR

- Bắt đầu: bệnh viện hiển thị QR phòng/phiên và bệnh nhân quét bằng Patient Mobile.
- Kết thúc: Queue Entry từ `TICKET_ISSUED` sang `CHECKED_IN`.
- Điều kiện thành công: token QR, Appointment/ticket và phòng/phiên hợp lệ; vị trí
  thiết bị nằm trong Hospital Geofence.
- Ngoại lệ: QR sai/hết hạn, lịch bị hủy, sai phòng/phiên, ngoài bán kính, không
  lấy được vị trí hoặc đã check-in. Nhân viên có thể hỗ trợ tại quầy cho bệnh
  nhân không dùng Mobile.

### `UC-QUE-04` — Theo dõi, đề xuất và gọi lượt khám

- Doctor Web lấy khoa từ hồ sơ bác sĩ, xác định phòng active tương ứng rồi gọi
  API Queue theo `roomId`; Queue Service kiểm tra lại quyền truy cập phòng.
- Doctor Web hiển thị ba làn `PRIORITY`, `NORMAL`, `RESULT_REVIEW` và lượt được
  Queue Service đề xuất theo Round Robin `1:1:1`.
- Bác sĩ chủ động bấm gọi tại bất kỳ entry đủ điều kiện nào; server gọi đúng entry
  được chọn. `call-next` là lệnh gọi nhanh và phải tính lại gợi ý tại thời điểm xử lý.
  Scheduler không tự động gọi bệnh nhân.
- Active queue phòng khám gồm lượt khám ban đầu `CHECKED_IN` và lượt đọc kết quả
  được tự động tạo khi đủ kết quả bắt buộc.
- Bao gồm gọi lại và đánh dấu `MISSED`; nhân viên có quyền chỉ hỗ trợ requeue
  theo chính sách, không thực hiện command gọi của phòng khám.
- Bệnh nhân nhận cập nhật trạng thái và thông báo gọi lượt.

### `UC-CON-01` — Bắt đầu và thực hiện phiên khám

- Chỉ bác sĩ được phân công thao tác trên Queue Entry đã `CALLED`.
- Bác sĩ nhập sinh hiệu, triệu chứng, khám thực thể và chẩn đoán sơ bộ.
- Kết thúc theo một trong hai nhánh: hoàn tất trực tiếp hoặc chờ cận lâm sàng.

### `UC-LAB-01` — Tạo chỉ định cận lâm sàng

- Bác sĩ tạo order từ consultation đang `IN_PROGRESS`.
- Order hợp lệ được ghi nhận đơn giá/thành tiền và Queue tự tạo
  `LAB_EXECUTION`; bệnh nhân không thanh toán hay check-in lại tại bước này.

### `UC-LAB-03` — Thực hiện, phát hành và đọc kết quả

- Kỹ thuật viên gọi, bắt đầu, nhập và finalize kết quả.
- Khi đủ kết quả bắt buộc, consultation chờ review; Queue tự động tạo và kích
  hoạt entry `CONSULTATION` phase `RESULT_REVIEW`.
- Bệnh nhân được bộ phận cận lâm sàng hướng dẫn quay lại và có thể nhận thêm
  thông báo; không cần dùng Mobile hay xác nhận có mặt. Lượt vẫn thuộc đúng bác
  sĩ trong consultation cũ và không tạo lịch mới.

### `UC-PRE-01` — Kê toa, hẹn tái khám và hoàn tất

- Bác sĩ tạo, kiểm tra và xác nhận toa.
- Có thể tạo follow-up appointment.
- Hoàn tất Consultation, Queue Entry và Appointment; hệ thống chốt
  `totalVisitCost` từ phí khám, cận lâm sàng và thuốc để chuẩn bị quyết toán.

### `UC-PHA-01` — Gọi lượt và phát thuốc

- Toa `CONFIRMED` làm Queue Service tạo một entry `PHARMACY_DISPENSING` tại điểm
  cấp phát mặc định; bệnh nhân không check-in lại.
- Nhân viên cấp phát xem queue FIFO, gọi lượt, đối chiếu bệnh nhân và bắt đầu phục vụ.
- Hệ thống tính `amountDue = max(0, totalVisitCost - prepaidAmount)` và
  `refundDue = max(0, prepaidAmount - totalVisitCost)`. Chỉ `PAYMENT_DUE` chặn
  xác nhận phát thuốc; `SETTLED`, `REFUND_PENDING` và `REFUNDED` đều cho phép.
- Nhân viên xác nhận đã phát thuốc; Prescription chuyển `DISPENSED` và Queue Entry
  tương ứng chuyển `COMPLETED`.
- Ngoại lệ chính: toa đã hủy/đã phát, sai điểm phục vụ, lượt chưa được gọi hoặc
  actor không được phân công.

## 4. Quan hệ include/extend dự kiến

- `Đặt lịch khám` **include** `Tra cứu khoa và khung giờ`.
- `Đặt lịch khám` **include** `Chọn hồ sơ bệnh nhân`.
- `Đặt lịch khám` **include** `Chọn dịch vụ và ghi nhận lựa chọn thanh toán Mobile`
  ở mức UX; đây không phải lời gọi Payment Service.
- `UC-PAY-01 — Thanh toán và quyết toán lượt khám` **extend** `Đặt lịch khám` để
  ghi nhận phí khám trả trước.
- `Quản lý và gọi lượt` **extend** `Gọi lại/đánh dấu lỡ lượt` khi bệnh nhân vắng.
- `Thực hiện phiên khám` **extend** `Tạo chỉ định cận lâm sàng` khi cần xét nghiệm.
- `Thực hiện và phát hành kết quả` **include** `Tạo lượt quay lại đọc kết quả` khi đủ kết quả.
- `Kê toa và hoàn tất` **extend** `Tạo lịch tái khám` khi bác sĩ yêu cầu.
- `Gọi lượt và phát thuốc` **include** `Đối chiếu toa và Queue Entry` trước khi
  xác nhận cấp phát.
- `UC-PAY-01 — Thanh toán và quyết toán lượt khám` **extend** `Gọi lượt và phát
  thuốc` để quyết toán cuối lượt trước khi xác nhận cấp phát.

Không lạm dụng `include`/`extend` cho các bước kỹ thuật nội bộ hoặc lời gọi API.
