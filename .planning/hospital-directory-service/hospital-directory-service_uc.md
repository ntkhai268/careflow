# Use Cases & API Specs — Hospital Directory Service

## 1. Tổng quan
Service chịu trách nhiệm quản lý **Master Data (Cơ sở dữ liệu danh bạ chung)** cho toàn hệ thống Bệnh viện:
- Danh mục Khoa chuyên môn (`Department`)
- Danh mục Phòng khám & Điểm phục vụ (`Room`)
- Danh bạ Bác sĩ & Nhân sự y tế (`DoctorProfile`)

---

## 2. API Endpoints Specification

### 2.1. Danh mục Khoa (Departments)
- **`GET /api/directory/departments`**
  - **Auth**: Public (Không yêu cầu JWT)
  - **Response**: Danh sách các khoa active.
- **`GET /api/directory/departments/{code}`**
  - **Auth**: Public
  - **Response**: Chi tiết khoa kèm danh sách phòng thuộc khoa đó.

### 2.2. Danh mục Phòng khám / Điểm phục vụ (Rooms)
- **`GET /api/directory/rooms`**
  - **Auth**: Public
  - **Query Params**: `departmentCode` (tùy chọn), `roomType` (tùy chọn: `CONSULTATION`, `LAB`, `IMAGING`, `PHARMACY`).
  - **Response**: Danh sách phòng khám khả dụng.

### 2.3. Danh bạ Bác sĩ & Nhân sự (Doctor Profiles)
- **`GET /api/directory/doctors`**
  - **Auth**: Public
  - **Query Params**: `departmentCode` (tùy chọn)
  - **Response**: Danh sách thông tin bác sĩ.
- **`GET /api/directory/doctors/{userId}`**
  - **Auth**: Public / Internal Feign Client
  - **Response**: Thông tin profile bác sĩ theo `userId` (dùng cho Feign Client từ Consultation/Appointment Service snapshot).
- **`GET /api/directory/doctors/me`**
  - **Auth**: Yêu cầu JWT
  - **Headers**: `X-User-Id`
  - **Response**: Profile bác sĩ đang đăng nhập.

### 2.4. Quản trị Write APIs (Admin Operations)
- **`POST /api/directory/departments`** | **`PUT /api/directory/departments/{code}`**
  - **Auth**: Yêu cầu JWT (Role: `ADMIN`)
- **`POST /api/directory/rooms`** | **`PUT /api/directory/rooms/{id}`**
  - **Auth**: Yêu cầu JWT (Role: `ADMIN`)
- **`POST /api/directory/doctors`** | **`PUT /api/directory/doctors/{id}`**
  - **Auth**: Yêu cầu JWT (Role: `ADMIN`)
