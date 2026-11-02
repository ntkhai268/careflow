# Spec-Driven Development (SDD): Patient Service (`careflow-patient-service`)

> **Bounded Context**: Quản lý Hồ sơ Hành chính & Dị ứng của Bệnh nhân (`Patient`, `PatientAllergy`), Hồ sơ Sức khỏe Cá nhân tự tải lên kèm tệp đính kèm (`HealthRecord`, `HealthRecordFile`), Tiếp nhận Bệnh nhân Vãng lai không tài khoản tại quầy (`PatientReceptionService`), và Liên kết Hồ sơ Tiếp nhận vào Tài khoản Bệnh nhân có đối chiếu eKYC (`ProfileLinkService`).
> **Schema Ownership**: `patients`, `patient_allergies`, `health_records`, `health_record_files`, `patient_reception_commands`, `patient_reception_search_rate`, `patient_reception_audit`, `patient_link_authorizations`, `patient_profile_links`, `patient_profile_link_audit` (`careflow_patient` DB)
> **Port**: `8082`

---

## 1. Bounded Context & Data Ownership

### 1.1. Bảng CSDL thuộc quyền sở hữu
| Bảng | Vai trò | Ràng buộc (Invariants) |
|---|---|---|
| `patients` | Hồ sơ bệnh nhân (có tài khoản `user_id` hoặc tạo tại quầy tiếp nhận `user_id IS NULL`) | Tối đa 10 hồ sơ/tài khoản (`MAX_PROFILES_PER_USER = 10`); unique `(user_id, id_card_number)` |
| `patient_allergies` | Danh sách dị ứng chi tiết gắn với `patient_id` | Cascade theo `Patient` |
| `health_records` / `health_record_files` | Hồ sơ sức khỏe tự theo dõi (BMI, huyết áp, đường huyết, tiền sử dị ứng/bệnh mãn tính) và tệp đính kèm | Tối đa 5 tệp / 50MB mỗi lần tạo; tổng hạn mức 100 tệp / 200MB mỗi bệnh nhân (`UploadQuotaService`) |
| `patient_reception_commands` / `patient_reception_search_rate` / `patient_reception_audit` | Idempotency khi tạo hồ sơ tiếp nhận, giới hạn tốc độ tìm kiếm (60 lần/phút/nhân viên) và audit log tại quầy | Khóa `pg_advisory_xact_lock` theo CCCD và command key |
| `patient_link_authorizations` / `patient_profile_links` / `patient_profile_link_audit` | Quy trình 2 bước liên kết hồ sơ không tài khoản vào tài khoản bệnh nhân có đối chiếu CCCD/eKYC | Mã ủy quyền hiệu lực 5 phút, yêu cầu liên kết hiệu lực 10 phút |

---

## 2. Quy tắc Phân quyền & Bảo mật (`PatientAccessPolicy`)

1. **Tạo hồ sơ (`requireCreate`)**: Chỉ chủ tài khoản (`PATIENT` với `requesterId == request.userId`) hoặc `ADMIN` được tạo hồ sơ gắn tài khoản.
2. **Đọc lâm sàng đầy đủ (`requireClinicalRead`)**: Chỉ chủ hồ sơ (`PATIENT`) hoặc `DOCTOR` đang được phân công khám hợp lệ (`AssignmentClient.getPatientAccess(...) == true`) mới được đọc toàn bộ hồ sơ bệnh nhân.
3. **Đọc vận hành tối thiểu (`requireOperationalRead`)**: Nhân viên `STAFF` được phân công hoặc `ADMIN` chỉ đọc được thông tin hành chính tối thiểu (`PatientOperationalResponse`: `id`, `fullName`, `dateOfBirth`, `gender`), không lộ tiền sử bệnh hay ghi chú dị ứng.
4. **Hồ sơ Sức khỏe tự tải lên (`HealthRecordAccessService`)**:
   - Đọc (`requireRead`): Chỉ `PATIENT` sở hữu hồ sơ hoặc `DOCTOR` được phân công.
   - Ghi/Sửa/Xóa (`requireWrite`): Chỉ chính `PATIENT` sở hữu hồ sơ mới được thêm/sửa/xóa.
   - Tự động dọn tệp vật lý (`FileStorageService.delete`) nếu transaction DB bị rollback.

---

## 3. Error Matrix

| HTTP Status | Tình huống | Thông điệp chuẩn |
|---|---|---|
| `400 Bad Request` | Truy vấn tìm kiếm < 3 hoặc > 80 ký tự, thiếu tên file, mã liên kết sai định dạng | Thông điệp chi tiết |
| `401 Unauthorized` | Thiếu thông tin người dùng xác thực hoặc caller nội bộ không hợp lệ | `"Missing authenticated user information"` / `"Caller không hợp lệ."` |
| `403 Forbidden` | Truy cập hồ sơ bệnh nhân không thuộc quyền sở hữu hoặc chưa được phân công | `"You are not allowed to view this patient profile"` |
| `404 Not Found` | Không tìm thấy `Patient` hoặc `HealthRecord` | `ResourceNotFoundException` |
| `409 Conflict` | Vượt quá 10 hồ sơ/tài khoản, trùng CCCD trong cùng tài khoản, xung đột Idempotency-Key hoặc trạng thái liên kết hồ sơ | `"Tài khoản đã đạt tối đa 10 hồ sơ bệnh nhân"` |
| `413 Payload Too Large` | Vượt quá 5 tệp / 50MB trong 1 lần tải hoặc vượt quota 100 tệp / 200MB của bệnh nhân | `"Mỗi hồ sơ tối đa 5 tệp, tổng dung lượng tối đa 50MB."` |
| `422 Unprocessable Entity` | Tệp tải lên bị ClamAV (`UploadScanner`) phát hiện mã độc | `"Tệp không vượt qua kiểm tra an toàn."` |
| `429 Too Many Requests` | Nhân viên tra cứu quá 60 lần/phút hoặc bệnh nhân tạo quá 5 mã liên kết trong 5 phút | `"Vượt số lần tra cứu; hãy thử lại sau một phút."` |

---

## 4. Sequence Diagram (Eraser.io DSL)

```eraser
title Luồng Liên kết Hồ sơ Tiếp nhận Vãng lai vào Tài khoản Bệnh nhân (ProfileLinkService)

actor Patient
actor "Reception Staff" as Staff
participant "ProfileLinkController" as PLC
participant "ProfileLinkService" as PLS
participant "PatientReceptionService" as PRS
participant "ConfirmedIdentityClient" as CIC
database "PostgreSQL (Patient)" as DB

Patient -> PLC: POST /api/patients/profile-links/authorization (requestId, token)
PLC -> PLS: authorize(ownerId, "PATIENT", requestId, token)
PLS -> DB: Rate-limit (< 5/5m) & save SHA-256(token) (TTL 5m)
PLS --> Patient: Authorization(requestId, expiresAt)

Staff -> PLC: POST /api/patients/profile-links (requestId, patientId, roomId, token, reference, originalSeen=true)
PLC -> PLS: propose(...)
PLS -> PRS: requireRoom(roomId, staffId, "STAFF")
PLS -> DB: Lock patient & verify token_hash unused and unexpired
PLS -> CIC: match(ownerId, idCardNumber, fullName, dateOfBirth)
CIC --> PLS: Verified identityCheckId
PLS -> DB: Insert patient_profile_links (status = PENDING_OWNER, TTL 10m)
PLS --> Staff: View(status = PENDING_OWNER)

Patient -> PLC: POST /api/patients/profile-links/{id}/decision (accepted = true)
PLC -> PLS: decide(id, true, ownerId, "PATIENT")
PLS -> CIC: Re-verify identityCheckId unchanged
PLS -> DB: Update patients SET user_id = ownerId & link status = LINKED
PLS --> Patient: View(status = LINKED)
```
