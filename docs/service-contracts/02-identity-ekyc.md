# Identity & eKYC Service Contract

> Contract ID: `CF-SVC-02` | Version: `1.0` | Module: `careflow-identity-service`

## 1. Trách nhiệm và dữ liệu sở hữu

Sở hữu:

- tài khoản, username, email, password hash;
- role và trạng thái tài khoản;
- access/refresh token lifecycle;
- kết quả eKYC mock của MVP.

Không sở hữu hồ sơ bệnh nhân, lịch khám, dữ liệu lâm sàng hoặc phân công bác sĩ.

## 2. Role và trạng thái

```text
Role: PATIENT | DOCTOR | STAFF | LAB_TECHNICIAN | ADMIN
UserStatus: ACTIVE | LOCKED | DISABLED
EkycStatus: NOT_VERIFIED | VERIFIED | REJECTED
```

Đăng ký công khai chỉ tạo `PATIENT`. Tài khoản nhân viên do `ADMIN` tạo hoặc seed trong môi trường demo.

## 3. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `POST /api/auth/register` | Public | Đăng ký tài khoản bệnh nhân |
| `POST /api/auth/login` | Public | Nhận access/refresh token |
| `POST /api/auth/refresh` | Public | Rotate refresh token |
| `POST /api/auth/logout` | Public có refresh token | Thu hồi refresh token |
| `GET /api/auth/me` | Authenticated | Lấy tài khoản hiện tại |
| `POST /api/auth/ekyc` | Chính user | Upload ảnh eKYC mock |
| `GET /api/auth/ekyc` | Chính user | Lấy trạng thái eKYC gần nhất |
| `PATCH /api/users/{userId}/status` | `ADMIN` | Khóa/mở/vô hiệu hóa |
| `POST /api/users/staff` | `ADMIN` | Tạo tài khoản nội bộ |

Register request:

```json
{
  "username": "patient47",
  "email": "patient47@example.test",
  "password": "CareFlow@123"
}
```

Login response `data`:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<opaque-token>",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "refreshTokenExpiresInSeconds": 604800,
  "user": {
    "id": "2f12f672-82d4-4ca2-902d-e8ad333003d5",
    "username": "patient47",
    "email": "patient47@example.test",
    "role": "PATIENT",
    "status": "ACTIVE"
  }
}
```

Staff creation request:

```json
{
  "username": "doctor01",
  "email": "doctor01@careflow.test",
  "temporaryPassword": "CareFlow@123",
  "role": "DOCTOR"
}
```

eKYC dùng `multipart/form-data`, part `image`, JPEG/PNG tối đa 5 MB. Response phải có
`verificationId`, `userId`, `documentNumber`, `fullName`, `dateOfBirth`, `confidence`, `status`,
`mock: true`, `processedAt`.

## 4. Security và lỗi

- Password 8–72 ký tự, có hoa, thường, số và ký tự đặc biệt.
- Password chỉ lưu dạng adaptive hash; không log token/password.
- Refresh token rotate một lần; token cũ dùng lại phải bị từ chối.
- `409` khi username/email trùng.
- `401` khi credential/token sai; `403` khi account `LOCKED`/`DISABLED`.
- `413` khi ảnh quá 5 MB; `415` khi không phải JPEG/PNG hợp lệ.

## 5. Event

Exchange mục tiêu: `identity.exchange`.

| Publish | Routing key | Consumer |
|---|---|---|
| `UserRegistered` v1 | `identity.user.registered` | Patient, Analytics |
| `UserStatusChanged` v1 | `identity.user.status-changed` | Gateway cache nếu có, Analytics |
| `EkycVerified` v1 | `identity.ekyc.verified` | Patient, Analytics |

`UserRegistered.payload`:

```json
{
  "userId": "2f12f672-82d4-4ca2-902d-e8ad333003d5",
  "role": "PATIENT",
  "email": "patient47@example.test"
}
```

## 6. Mock cho consumer

- JWT test phải có `iss=careflow-identity`, `sub=<userId>`, `role`, `iat`, `exp`.
- Cung cấp fixture cho `PATIENT`, `DOCTOR`, `ADMIN`, token hết hạn và token signature sai.
- Patient Service mock `UserRegistered`; không gọi database Identity.
- Không dùng access token hard-code lâu dài trong repo.

## 7. Definition of Done

### `CONTRACT_READY`

- API, claim, role, token TTL và event schema được công bố.
- Gateway và Patient có fixture hợp lệ.

### `FUNCTIONAL_READY`

- Register/login/refresh/logout/me/eKYC/status/staff creation chạy đúng.
- Migration có unique constraint cho username/email.
- Unit/service test phủ duplicate, password policy, token rotation và account status.

### `INTEGRATION_READY`

- Gateway verify được token thật.
- `UserRegistered` dùng envelope chuẩn và Patient xử lý duplicate an toàn.
- Ownership của `/me` và `/ekyc` không nhận userId tùy ý từ client.

### `DEMO_READY`

- Demo đăng ký → đăng nhập → tạo Patient Profile → eKYC mock.
- Demo khóa user khiến request tiếp theo bị từ chối.
- Có audit log cho admin status change, không lộ credential.
