# Danh sách Tài khoản Test Hệ thống CareFlow (Xác minh trực tiếp từ DB Remote)

> **Mật khẩu mặc định chung cho tất cả các tài khoản test**: **`Password123@`**  
> *(Ghi chú: Đối với các tài khoản tạo mới qua ứng dụng, mật khẩu là mật khẩu đã đăng ký)*

---

## 1. Tài khoản Nhân sự & Hệ thống (Staff & Clinical Accounts)

| Role | Username | Email | User ID (`UUID`) | Họ và tên / Chức danh | Quyền & Giao diện tương ứng |
|---|---|---|---|---|---|
| **`DOCTOR`** | `an_nguyen` | `an@careflow.vn` | `d0000001-0000-0000-0000-000000000001` | BS. Nguyễn Văn An<br>*(Khoa Nội tổng quát)* | Bảng điều khiển Bác sĩ: Gọi số khám, nhập sinh hiệu, chẩn đoán, tạo chỉ định CLS, kê đơn |
| **`DOCTOR`** | `doctor` | `doctor@careflow.com` | `11111111-1111-1111-1111-111111111111` | BS. Phạm Hoàng Nam<br>*(Bác sĩ CKI)* | Bảng điều khiển Bác sĩ: Khám bệnh, kê đơn |
| **`DOCTOR`** | `binh_tran` | `binh@careflow.vn` | `d0000001-0000-0000-0000-000000000002` | BS. Trần Thị Bình<br>*(Khoa Nhi)* | Bảng điều khiển Bác sĩ: Khám chuyên khoa Nhi |
| **`DOCTOR`** | `cuong_le` | `cuong@careflow.vn` | `d0000001-0000-0000-0000-000000000003` | BS. Lê Hoàng Cường<br>*(Khoa Ngoại tổng quát)* | Bảng điều khiển Bác sĩ: Khám chuyên khoa Ngoại |
| **`LAB_TECHNICIAN`** | `lab` | `lab@careflow.com` | `44444444-4444-4444-4444-444444444444` | KTV. Trần Văn Lab<br>*(Khu Xét nghiệm)* | Bảng điều khiển Kỹ thuật viên: Hàng đợi xét nghiệm, gọi số CLS, nhập chỉ số, phát hành kết quả |
| **`STAFF`** | `staff` | `staff@careflow.com` | `55555555-5555-5555-5555-555555555555` | NV. Lê Thị Staff<br>*(Tiếp nhận & Dược)* | Bảng điều khiển Nhân viên: Quét QR Check-in tiếp nhận, Quầy cấp phát thuốc |
| **`ADMIN`** | `admin` | `admin@careflow.com` | `33333333-3333-3333-3333-333333333333` | Quản trị hệ thống | Quản trị toàn bộ danh mục, phân quyền và cấu hình hàng đợi |

---

## 2. Tài khoản Bệnh nhân (Patient Mobile Accounts)

| Role | Username | Email | User ID (`UUID`) | Tên Bệnh nhân trong hồ sơ | Công dụng |
|---|---|---|---|---|---|
| **`PATIENT`** | `patient` | `patient@careflow.com` | `22222222-2222-2222-2222-222222222222` | Bệnh nhân Nguyễn Văn Bệnh | Sử dụng cho app Patient Mobile (Đặt lịch khám, Check-in, xem kết quả, nhận đơn thuốc) |
| **`PATIENT`** | `ntkhai268` | `khai@gmail.com` | `784406d1-9f31-4f38-ac29-9e3ebe4ff601` | Nguyễn Thanh Khai | Tài khoản test cá nhân |
| **`PATIENT`** | `doc_test` | `doc_test@careflow.vn` | `3e4d2c70-b4bc-4aa8-872d-d3541869f615` | — | Tài khoản test bổ sung |
| **`PATIENT`** | `testuser` | `test@careflow.com` | `61f93e25-dbbb-45bf-a703-863b5808de9f` | — | Tài khoản test quy trình |
