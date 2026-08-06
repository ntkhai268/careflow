# Hospital Directory Service — Technical & Business Specification

## 1. Tổng quan & Vai trò Hệ thống
`careflow-hospital-directory-service` chịu trách nhiệm quản lý **Master Data (Cơ sở dữ liệu danh bạ chung)** cho toàn bộ hệ thống Bệnh viện CareFlow:
- **Danh mục Khoa chuyên môn (`Department`)**: Đơn vị quản lý hành chính & chuyên môn (Nội, Nhi, Ngoại, Sản, Tim mạch...).
- **Danh mục Phòng khám & Điểm phục vụ (`Room`)**: Các phòng khám thực tế (`ROOM-01`), phòng xét nghiệm (`LAB-01`), quầy thuốc (`PHARMACY-01`).
- **Danh bạ Bác sĩ & Nhân sự y tế (`DoctorProfile`)**: Thông tin hành nghề của bác sĩ (Họ tên, bằng cấp, chuyên khoa, số CCHN, phòng được phân công).

---

## 2. API Endpoints Specification

### 2.1. Danh mục Khoa (Departments)
- `GET /api/directory/departments`: Danh sách tất cả các khoa active (Public).
- `GET /api/directory/departments/{code}`: Thông tin chi tiết một khoa theo mã (Public).

### 2.2. Danh mục Phòng khám / Điểm phục vụ (Rooms)
- `GET /api/directory/rooms`: Lấy danh sách phòng khả dụng (Public).
  - Query Params: `departmentCode` (tùy chọn), `roomType` (tùy chọn: `CONSULTATION`, `LAB`, `IMAGING`, `PHARMACY`).

### 2.3. Danh bạ Bác sĩ & Nhân sự (Doctor Profiles)
- `GET /api/directory/doctors`: Lấy danh sách bác sĩ (Public, tùy chọn lọc theo `departmentCode`).
- `GET /api/directory/doctors/{userId}`: Lấy thông tin profile bác sĩ theo `userId` (Dùng cho Internal Feign Client từ `appointment-service` & `consultation-service`).
- `GET /api/directory/doctors/me`: Lấy thông tin profile của Bác sĩ đang đăng nhập (Auth JWT via `X-User-Id`).

### 2.4. Quản trị Write APIs (Admin Operations — Trong Scope mở rộng)
- `POST /api/directory/departments` | `PUT /api/directory/departments/{code}`: Tạo / Cập nhật khoa (Role: `ADMIN`).
- `POST /api/directory/rooms` | `PUT /api/directory/rooms/{id}`: Tạo / Cập nhật phòng khám (Role: `ADMIN`).
- `POST /api/directory/doctors` | `PUT /api/directory/doctors/{id}`: Tạo / Cập nhật profile bác sĩ (Role: `ADMIN`).

---

## 3. Trạng thái Hiện tại (Implementation Status)
- [x] **Read APIs**: Đã hoàn thành toàn bộ các API Read cho Khoa, Phòng và Profile Bác sĩ.
- [x] **Feign Integration**: `DirectoryClient` trong `appointment-service` và `consultation-service` kết nối lấy dữ liệu bác sĩ thành công.
- [x] **Auto Data Seeding**: Khởi tạo 11 Khoa chuyên môn, 12 Phòng khám/Điểm phục vụ và 2 Bác sĩ mẫu.
- [x] **Admin Write APIs**: Đã hoàn thành triển khai đầy đủ các REST APIs và Giao diện Quản trị Admin Web (Khoa, Phòng khám & Profile Bác sĩ).
