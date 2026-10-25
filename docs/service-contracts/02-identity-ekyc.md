# Identity & eKYC Service Contract

> **Chuẩn hóa R0 — 19/10/2026:** Phạm vi bàn giao đầy đủ, ownership và phân biệt CURRENT/TARGET xem [bộ đặc tả chuẩn](../../.planning/architecture/README.md). Tài liệu này không chứng minh chức năng đã được nghiệm thu. Các đoạn MVP/mock cũ phải đọc theo giới hạn và thay đổi tại R0; không loại Analytics/AI khỏi yêu cầu cuối.


> Contract ID: `CF-SVC-02` | Version: `2.0` | Module: `careflow-identity-service`

## 1. Trách nhiệm và dữ liệu sở hữu

Sở hữu:

- tài khoản, username, email, password hash;
- role và trạng thái tài khoản;
- access/refresh token lifecycle;
- trạng thái xác minh VNPT, metadata chống gửi trùng và quota; không lưu ảnh hoặc OCR thô.

Contract VNPT hiện tại: [identity-service/vnpt-ekyc-contract](../../.planning/identity-service/vnpt-ekyc-contract.md). Chờ xác nhận tenant Free và nghiệm thu nhà cung cấp thật.

Không sở hữu hồ sơ bệnh nhân, lịch khám, dữ liệu lâm sàng hoặc phân công bác sĩ.

## 2. Role và trạng thái

```text
Role: PATIENT | DOCTOR | STAFF | LAB_TECHNICIAN | ADMIN
UserStatus: ACTIVE | LOCKED | DISABLED
EkycStatus: PROCESSING | VERIFIED | REJECTED | REVIEW_REQUIRED | ERROR (latest=null khi chưa xác minh)
```

Đăng ký công khai chỉ tạo `PATIENT`. Tài khoản nhân viên do `ADMIN` tạo hoặc seed trong môi trường demo.

## 3. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `POST /api/auth/register` | Public | Đăng ký tài khoản bệnh nhân |
| `POST /api/auth/login` | Public | Nhận access/refresh token |
| `POST /api/auth/refresh` | Public | Rotate refresh token |
| `POST /api/auth/logout` | Public có refresh token | Thu hồi refresh token |
| `GET /api/auth/jwks` | Public qua Gateway | Public signing key, không cấp quyền |
| `GET /api/auth/session` | Private service caller qua mTLS | Kiểm tra phiên, tối thiểu hóa định danh |
| `POST /api/auth/logout-all` | Authenticated | Thu hồi mọi phiên của chính user |
| `* /api/auth/mfa/**` | Legacy compatibility | 410 Gone, enrollment retired |
| `GET /api/auth/login-methods` | Authenticated | Question id and enrolled passkey ids/dates |
| `POST /api/auth/login-methods/question` | Authenticated + current password | Save BCrypt secret answer |
| `POST /api/auth/login/question` | Public + DPoP | Login with pre-enrolled question |
| `POST /api/auth/recovery/question` | Public + DPoP | Verify question, replace password and revoke all sessions |
| `POST /api/auth/passkeys/register/options`, `/finish` | Authenticated; options requires current password | Verified WebAuthn registration |
| `POST /api/auth/passkeys/remove` | Authenticated + current password | Remove own credential; revoke sessions |
| `POST /api/auth/passkeys/login/options`, `/finish` | Public + DPoP | WebAuthn assertion login |
| `POST /api/auth/recovery/passkey/options`, `/finish` | Public + DPoP | Verified assertion and password reset |
| `GET /api/auth/me` | Authenticated | Lấy tài khoản hiện tại |
| `POST /api/auth/ekyc` | Authenticated | Endpoint một ảnh đã ngừng, trả410 sau validation |
| `POST /api/auth/ekyc/verify` | PATIENT | Xác minh hai mặt căn cước và selfie qua VNPT, bắt buộc consent và Idempotency-Key |
| `GET /api/auth/ekyc/status` | PATIENT | Trạng thái cấu hình và kết quả gần nhất của chính tài khoản |
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

eKYC VNPT dùng `multipart/form-data`, các part `front`, `back`, `selfie`, mỗi ảnh JPEG/PNG tối đa 5 MiB, tổng request16 MiB; trường `consent` và header UUID `Idempotency-Key`. Response gồm `verificationId`, `status`, `provider`, `maskedDocumentNumber`, `reason`, `processedAt`. Không trả căn cước đầy đủ, ảnh, vendor hash hoặc response thô; không tự liên kết hồ sơ bệnh án.

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

## 6. Security v2 — breaking migration 2026-10-15

- Access JWT RS256: chỉ Identity giữ RSA private key; kiểm tra `kid`, issuer, audience `careflow-api`, expiry, `sub`, role, `jti` và `cnf.jkt`. Mặc định access 15 phút. `jti` trỏ tới phiên DB; verify luôn kiểm tra thu hồi, hết hạn, user ACTIVE và role hiện tại. Không fallback HMAC/token cũ.
- Login/refresh/logout yêu cầu header `DPoP` ES256 với public P-256 JWK, method, public canonical URI (bỏ query/fragment), iat và unique jti. Resource request thêm `ath` SHA-256 access token. Chống replay bằng DB dùng chung giữa các Identity instance. Login binding được lưu ở `refresh_tokens.dpop_jkt`, giữ qua rotation.
- API vẫn trả `tokenType: Bearer`. Đây là profile CareFlow Bearer + DPoP, không tuyên bố OAuth2 DPoP authorization-server conformance đầy đủ. Client cũ phải nâng cấp và đăng nhập lại; cần coordinated rollout backend/Common/client.
- Since 2026-10-17 login requires usernameOrEmail/password only. Optional legacy otp/recoveryCode fields are ignored; MFA does not gate token issuance. V9 clears legacy credentials/recovery rows, retains legacy tables for migration history and adds login_questions/passkeys/login_challenges.
- Introspection nội bộ cần credential riêng + CN của client certificate đúng caller; delegation còn cần method/path scope và JWT end-user còn active. Không có service credential cấp toàn quyền nghiệp vụ.
- Logout/rotation/lock/disable/role change có hiệu lực với request tiếp theo ở mọi service; socket được kiểm tra trước MESSAGE và quét định kỳ 1 giây. Identity/DB không xác minh được trả 503, không chấp nhận request mặc định.
- V8 thêm MFA/recovery/replay/security event tables và dpop_jkt, không sửa migration đã áp dụng. DBML đi kèm schema thực.
- eKYC MVP remains `mock:true`; excluded from real E2E and never grants login/roles. Passkey is real WebAuthn; positive hardware enrollment/assertion still requires physical user verification and is not reported as accepted in this test.

Chi tiết cấu hình, giới hạn và test thật: [runbook](../../.planning/security/security-runbook.md), [evidence](../../.planning/security/member-a-hardening-results.md).

### Login methods — 2026-10-17

Password sign-in has no second factor step. Web and Mobile Emulator expose separate change-method and password-recovery routes; authenticated settings require current password before enrollment. Public question login/reset returns no secret or question discovery endpoint. Wrong answers share the account's five-failure/15-minute lockout and revoke active sessions on lock. Answer normalization uses NFKC, stripping and lowercasing, BCrypt hashing, and an effective 72-byte input cap.

Passkeys use Yubico webauthn-server-core 2.10.1 to verify challenge, origin, RP ID, credential owner, user verification and signature counter. RP ID defaults to localhost; allowed origins default to http://localhost:3000,http://localhost:5173 and must be configured explicitly for deployment. Device choice of fingerprint/face/PIN belongs to the authenticator. Two-minute registration/login/recovery challenges are stored in PostgreSQL, bound to DPoP key thumbprint and consumed atomically once. Credentials store public keys; the server does not collect biometric templates. Finish rejects copied/malformed/replayed challenges; no authenticator bypass.

Recovery requires an already enrolled method, enforces password strength and revokes all sessions/refresh tokens/challenges. Sensitive POST 401 responses never silently replay after token refresh; authenticated GET status can refresh normally. Responses use Cache-Control: no-store.

Actual API regression: `.planning/identity-service/login-methods-live-results.json`. User actions and current verification scope: [canonical guide](../../huong-dan-su-dung/02-dang-nhap-mfa.md).
