# Spec-Driven Development (SDD): Hospital Directory Service (`careflow-hospital-directory-service`)

> **Bounded Context**: Quản lý Danh mục Cơ sở Y tế (Khoa chuyên môn `Department`, Phòng khám/Cận lâm sàng/Nhà thuốc `Room`, Hồ sơ Bác sĩ `DoctorProfile`, Phân công Nhân viên theo phòng `StaffAssignment`, và Bảng giá Dịch vụ Y tế `medical_service_prices`).
> **Schema Ownership**: `departments`, `rooms`, `doctor_profiles`, `staff_assignments`, `medical_service_prices` (`careflow_directory` DB)
> **Port**: `8090`

---

## 1. Bounded Context & Data Ownership

### 1.1. Bảng CSDL thuộc quyền sở hữu
| Bảng | Vai trò | Ràng buộc (Invariants) |
|---|---|---|
| `departments` | Danh mục khoa chuyên môn (`code` PK, `id` UUID, `name`, `description`, `is_active`) | Mã khoa duy nhất (`NOI_TONG_QUAT`, `TIM_MACH`, v.v.) |
| `rooms` | Danh mục phòng (`id` PK như `ROOM-01`, `LAB-HEMATOLOGY-01`, `PHARMACY-MAIN-01`, `department_code`, `display_name`, `room_type`, `is_active`) | `room_type ∈ {CONSULTATION, LAB, IMAGING, PHARMACY}` |
| `doctor_profiles` | Hồ sơ chuyên môn & phòng phân công của bác sĩ (`id`, `user_id` UNIQUE, `department_code`, `assigned_room_id`, `primary_for_booking`, `is_active`) | Mỗi phòng chỉ có tối đa 1 bác sĩ `primary_for_booking = true` đang hoạt động |
| `staff_assignments` | Phân công nhân viên vận hành (`STAFF`, `LAB_TECHNICIAN`) vào từng phòng (`user_id`, `room_id`, `department_code`, `is_active`) | Cặp `(user_id, room_id)` là duy nhất |
| `medical_service_prices` | Bảng giá dịch vụ khám chữa bệnh bất biến theo phiên bản (`code`, `version`, `amount`, `currency`, `effective_at`, `active`, `estimated_minutes`) | Append-only revision theo `(code, version)` với khóa `pg_advisory_xact_lock` |

### 1.2. Liên kết ngoại vi
- `StaffIdentityVerifier` → `careflow-identity-service` (`GET /api/users/staff/{id}`): Xác minh tài khoản nội bộ tồn tại, có đúng vai trò (`DOCTOR`, `STAFF`, `LAB_TECHNICIAN`) và đang ở trạng thái `ACTIVE` trước khi tạo/kích hoạt phân công tại Directory.

---

## 2. Quy tắc Nghiệp vụ & Bất biến (Invariants)

1. **Doctor Assignment Validation**:
   - Không được gán `assignedRoomId` nếu thiếu `departmentCode`.
   - Khoa được chọn phải tồn tại và `isActive = true`.
   - Phòng được gán (nếu có) phải tồn tại, `isActive = true`, và thuộc đúng `departmentCode` của bác sĩ.
   - Tài khoản Identity của bác sĩ phải có vai trò `DOCTOR` và đang `ACTIVE` (nếu hồ sơ bác sĩ active).
2. **Staff Room Assignment & Role Matrix**:
   - Khoa và phòng phải đang `isActive = true` và khớp `departmentCode`.
   - Loại phòng quyết định vai trò bắt buộc trên Identity Service:
     - `CONSULTATION`, `PHARMACY` → bắt buộc vai trò `STAFF`
     - `LAB`, `IMAGING` → bắt buộc vai trò `LAB_TECHNICIAN`
3. **Append-Only Service Catalog Pricing (`ServiceCatalogService`)**:
   - Mỗi lần công bố giá mới (`publish`) phải khóa `pg_advisory_xact_lock` theo `code`, kiểm tra `expectedVersion == MAX(version)` hiện tại (Optimistic Concurrency → `409 Conflict` nếu lệch), và không cho phép `effectiveAt` lùi quá khứ.

---

## 3. Error Matrix

| HTTP Status | Tình huống | Thông điệp chuẩn |
|---|---|---|
| `400 Bad Request` | Trùng mã khoa/phòng/bác sĩ khi tạo mới, gán phòng/khoa không active hoặc khác khoa, công bố giá lùi ngày | Thông điệp chi tiết theo trường |
| `401 Unauthorized` | Thiếu Bearer token quản trị khi xác minh tài khoản nội bộ hoặc công bố bảng giá | `"Thiếu phiên quản trị khi xác minh tài khoản."` |
| `403 Forbidden` | Người gọi không phải `ADMIN` khi sửa danh mục/bảng giá | `"Chỉ quản trị viên được quản lý bảng giá."` |
| `404 Not Found` | Không tìm thấy `Department`, `Room`, `DoctorProfile`, `StaffAssignment` hoặc dịch vụ | `ResourceNotFoundException` |
| `409 Conflict` | Trùng phân công `(userId, roomId)`, sai vai trò/trạng thái Identity, trùng bác sĩ `primaryForBooking`, hoặc lệch `expectedVersion` bảng giá | `"Vai trò hoặc trạng thái Identity không phù hợp với phân công."` |
| `503 Service Unavailable` | Không kết nối được Identity Service khi xác minh tài khoản nội bộ | `"Không thể kết nối Identity để xác minh tài khoản."` |

---

## 4. Sequence Diagram (Eraser.io DSL)

```eraser
title Luồng Phân công Nhân viên / Bác sĩ vào Phòng có Đối chiếu Identity

actor Admin
participant "StaffAssignmentController" as SAC
participant "DirectoryService" as DS
participant "StaffIdentityVerifier" as SIV
participant "IdentityService" as IDS
database "PostgreSQL (Directory)" as DB

Admin -> SAC: POST /api/directory/staff-assignments (userId, roomId, departmentCode)
SAC -> DS: createStaffAssignment(request)
DS -> DB: Verify Department active & Room active in Department
DS -> SIV: require(userId, expectedRoleForRoomType, active)
SIV -> IDS: GET /api/users/staff/{userId} (Bearer Admin Token)
IDS --> SIV: 200 OK (id, role, status)
SIV --> DS: Verified (role matches ROOM_TYPE & status == ACTIVE)
DS -> DB: Save StaffAssignment (unique userId + roomId)
DS --> SAC: StaffAssignmentResponse
SAC --> Admin: 201 Created
```
