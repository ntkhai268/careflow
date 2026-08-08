# Patient Service Contract

> Contract ID: `CF-SVC-03` | Version: `1.1` | Module: `careflow-patient-service`

## 1. Trách nhiệm và ranh giới

Sở hữu:

- các hồ sơ hành chính bệnh nhân thuộc cùng một tài khoản `userId`;
- BHYT, liên hệ, địa chỉ;
- dị ứng/tiền sử do bệnh nhân khai;
- hồ sơ, ảnh hoặc kết quả cũ do bệnh nhân chủ động upload.

Không sở hữu consultation, chẩn đoán chính thức, lab result do bệnh viện tạo hoặc toa thuốc. Các dữ
liệu lâm sàng chính thức thuộc Consultation/Lab/Prescription và được EMR tổng hợp.

## 2. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `POST /api/patients` | `PATIENT` chính chủ hoặc `ADMIN` | Tạo profile |
| `GET /api/patients/{patientId}` | Chính chủ, `ADMIN`, hoặc assigned `DOCTOR` | Xem profile lâm sàng |
| `GET /api/patients/{patientId}/operational-summary?appointmentId=&roomId=` | `STAFF` có assignment phòng/lượt hoặc `ADMIN` | Dữ liệu tối thiểu cho check-in |
| `GET /api/patients/user/{userId}` | Chính user hoặc internal | Tra profile theo Identity user |
| `GET /api/patients/user/{userId}/profiles` | Chính user hoặc internal | Liệt kê các profile thuộc cùng tài khoản |
| `PUT /api/patients/{patientId}` | Chính chủ hoặc `STAFF` | Partial update |
| `POST /api/patients/{patientId}/health-records` | Chính chủ | Upload hồ sơ cũ |
| `GET /api/patients/{patientId}/health-records` | Chính chủ hoặc assigned doctor | Danh sách upload |
| `GET /api/patients/{patientId}/health-records/{recordId}` | Như trên | Chi tiết |
| `PUT /api/patients/{patientId}/health-records/{recordId}` | Chính chủ | Sửa metadata |
| `DELETE /api/patients/{patientId}/health-records/{recordId}` | Chính chủ | Xóa upload |
| `GET /api/patients/{patientId}/health-records/files/{fileId}` | Chính chủ hoặc assigned doctor | Tải file |

Create profile request:

```json
{
  "userId": "2f12f672-82d4-4ca2-902d-e8ad333003d5",
  "fullName": "Nguyễn Thị Quỳnh Thương",
  "dateOfBirth": "1992-02-11",
  "gender": "FEMALE",
  "phone": "0900000047",
  "idCardNumber": "079192000047",
  "insuranceNumber": "DN4790000047",
  "occupation": "Nhân viên văn phòng",
  "address": "TP. Hồ Chí Minh"
}
```

Profile response `data` phải có `id`, `userId`, các field trên, `avatarUrl`, `createdAt`, `updatedAt`.
Một tài khoản có thể quản lý tối đa 10 profile cho bản thân và người thân; `userId` không còn là khóa duy nhất.
`idCardNumber` không unique toàn hệ thống: cùng một số CCCD có thể được khai báo lại trên tài khoản khác
khi người dùng mất quyền truy cập tài khoản cũ. Hệ thống chỉ từ chối số CCCD đã tồn tại trong cùng một tài khoản.

Upload hồ sơ dùng `multipart/form-data`:

- part `request`: JSON có `title`, `recordDate`, `facilityName`, `notes` và dữ liệu sức khỏe khai báo;
- part `files`: 0..5 file;
- mỗi file tối đa 10 MB; tổng request tuân theo Gateway limit 20 MB;
- loại MVP: PDF, JPEG, PNG.

## 3. Ownership rules

- Với role `PATIENT`, `X-User-Id` phải map đúng `patient.userId`.
- Không cho bệnh nhân tạo profile bằng `userId` của người khác.
- Khi tạo hoặc cập nhật profile, chỉ kiểm tra trùng `idCardNumber` trong phạm vi cùng `userId`; không dùng CCCD
  làm định danh tài khoản toàn cục.
- Doctor chỉ truy cập khi `Appointment.doctorId` (trusted Identity user ID) khớp với actor và appointment
  chưa bị hủy; Patient Service kiểm tra assignment với Appointment Service, không dựa vào UI hoặc role đơn lẻ.
- Staff không đọc profile đầy đủ. Staff chỉ gọi `operational-summary` với đúng `appointmentId` và `roomId`
  trong queue/phòng đang xử lý; response không chứa CCCD, BHYT, địa chỉ, tiền sử hoặc dị ứng.
- File download phải kiểm tra file → health record → patient, không chỉ kiểm tra `fileId`.
- Path lưu vật lý không xuất hiện trong response.

## 4. Event

Exchange: `patient.exchange`.

| Publish | Routing key | Consumer |
|---|---|---|
| `PatientProfileCreated` v1 | `patient.profile.created` | Appointment, EMR, Analytics |
| `PatientProfileUpdated` v1 | `patient.profile.updated` | EMR |
| `PatientUploadedRecordCreated` v1 | `patient.record-uploaded.created` | EMR |
| `PatientUploadedRecordDeleted` v1 | `patient.record-uploaded.deleted` | EMR |

`PatientProfileCreated.payload`:

```json
{
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "userId": "2f12f672-82d4-4ca2-902d-e8ad333003d5",
  "fullName": "Nguyễn Thị Quỳnh Thương"
}
```

Client gọi `POST /api/patients` sau `UserRegistered` để tạo profile đầu tiên hoặc từ màn quản lý người thân để tạo profile bổ sung.
Patient cũng consume `ConsultationStarted/Completed` để mở/đóng access grant cho đúng assigned doctor.

## 5. Mock cho consumer

Consumer dùng profile fixture:

```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "id": "9c613831-90c2-48f6-81c5-0105c20502a1",
    "userId": "2f12f672-82d4-4ca2-902d-e8ad333003d5",
    "fullName": "Nguyễn Thị Quỳnh Thương",
    "dateOfBirth": "1992-02-11",
    "gender": "FEMALE",
    "insuranceNumber": "DN4790000047"
  },
  "timestamp": "2026-07-30T04:00:00Z"
}
```

Phải mock thêm `404`, ownership `403` và file `413`/`415`.

## 6. Definition of Done

### `CONTRACT_READY`

- Phân biệt rõ patient-uploaded record với EMR chính thức.
- API/profile/file schema, ownership và giới hạn upload đã chốt.

### `FUNCTIONAL_READY`

- Profile CRUD và uploaded records chạy với migration từ database rỗng.
- Ownership theo `userId`, tối đa 10 profile/tài khoản; file metadata và cleanup nhất quán khi transaction lỗi.
- Test validation, duplicate, ownership, file type/size và path traversal.

### `INTEGRATION_READY`

- Token Patient/Doctor/Admin đi qua Gateway và quyền đúng.
- Appointment lấy được patient hợp lệ.
- Event dùng envelope, consumer duplicate không tạo projection trùng.

### `DEMO_READY`

- Mobile tạo/sửa profile, upload/xem/tải hồ sơ.
- Doctor Web chỉ xem được bệnh nhân được phân công; doctor khác nhận `403`.
- Check-in của Staff chỉ nhận dữ liệu tối thiểu trong đúng appointment/room scope.
- File không bị lộ qua URL không kiểm soát; audit được lượt truy cập của doctor.
