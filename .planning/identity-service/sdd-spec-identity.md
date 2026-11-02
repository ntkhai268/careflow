# Spec-Driven Development (SDD) Specification: Identity & API Gateway Services (`careflow-identity-service` & `careflow-api-gateway`)

## 1. Bounded Context & Responsibility

- **Services**:
  - `careflow-identity-service` (Schema: `identity`)
  - `careflow-api-gateway` (Reactive Spring Cloud Gateway Edge Proxy)
- **Bounded Context**: Authentication, Session & Token Lifecycle (JWT + DPoP-bound Refresh Tokens), WebAuthn/Passkey Registration & Step-Up Authentication, Mobile Biometric Device Enrollment, Email OTP Registration & Password Recovery (with AES-GCM Outbox), eKYC / Document Identity Verification (`CCCD` 12-digit in-person check), Staff Account Provisioning, and Edge API Gateway Routing & Session Introspection.
- **Core Responsibilities**:
  - **Authentication & Lockout Policy (`AuthService`, `User`)**:
    - Authenticates credentials with timing-safe dummy hash fallback on unknown accounts.
    - Enforces temporary lockout (`LOCKED` for 15 minutes after 5 consecutive failed attempts) and automatic unlock once `lockedUntil` expires.
    - Enforces passkey-only login for provisioned staff accounts or when `security.auth.require-staff-passkey=true`.
  - **DPoP-Bound Session & Refresh Token Lifecycle (`RefreshTokenService`, `RefreshToken`)**:
    - Issues, rotates, and revokes refresh tokens bound to client DPoP key thumbprints (`dpop_jkt`).
    - Detects refresh token reuse and revokes all active tokens sharing the compromised client `dpop_jkt`.
    - Enforces interactive idle timeout (`InteractiveSessionPolicy`) and absolute session expiration.
  - **WebAuthn / FIDO2 Passkeys & Staff Grants (`LoginMethodsService`, `StaffPasskeyService`)**:
    - Manages WebAuthn registration and assertion ceremonies via Yubico `RelyingParty`.
    - Supports dual-factor staff passkey enrollment and recovery (`staff_passkey_grants` with email token + administrator-delivered activation code).
  - **Biometric Device Enrollment (`BiometricDeviceService`)**:
    - Enrolls and authenticates mobile biometric devices for `ACTIVE` `PATIENT` accounts bound to `dpop_jkt` with 90-day expiry.
  - **Email Registration, Password Recovery & Encrypted Outbox (`EmailRegistrationService`, `PasswordRecoveryService`, `RecoveryMailOutbox`)**:
    - Enumeration-safe email OTP signup and password recovery with rate-limiting (`60s` resend cooldown, max `5` per hour) and AES-256-GCM encrypted `recovery_mail_outbox`.
  - **eKYC Document Identity Verification (`DocumentIdentityService`)**:
    - Seals 12-digit Citizen ID (`CCCD`), full name, and birth date using `DocumentCipher` (`AES-GCM` + `HMAC` canonical fingerprint) for in-person staff review (`PENDING_REVIEW` -> `CONFIRMED` / `REJECTED` / `WITHDRAWN` / `EXPIRED`) and cross-service confirmed identity matching (`/internal/identity/document-checks/match`).
  - **Staff Account Administration (`StaffAccountService`)**:
    - Provisions internal staff accounts (`DOCTOR`, `STAFF`, `LAB_TECHNICIAN`, `ADMIN`) under PostgreSQL transaction advisory lock (`0x43464c4f5741444dL`) and requires recent admin Passkey step-up (`<= 5 minutes`), preventing removal of the last active passkey-enrolled administrator.
  - **API Gateway Edge Security (`JwtAuthenticationFilter`, `AuthSourceRateFilter`)**:
    - Strips spoofed internal headers (`X-User-Id`, `X-User-Role`, `X-Careflow-Caller*`), enforces per-source IP rate limits on `POST /api/auth/**`, and delegates bearer token introspection to `ActiveSessionClient`.

---

## 2. Database Ownership (`identity` Schema)

| Table | Primary Key | Key Columns & Domain Invariants |
| :--- | :--- | :--- |
| `identity.users` | `id` (UUID) | `username` (UNIQUE), `email` (UNIQUE), `password_hash`, `role` (`PATIENT`, `DOCTOR`, `STAFF`, `LAB_TECHNICIAN`, `ADMIN`), `status` (`ACTIVE`, `LOCKED`, `DISABLED`), `failed_login_attempts`, `locked_until`, `last_login_at`, `version` |
| `identity.refresh_tokens` | `id` (UUID) | `user_id` (FK), `token_hash` (SHA-256 UNIQUE), `dpop_jkt`, `authentication_method` (`PASSWORD`, `PASSKEY`, `BIOMETRIC`), `expires_at`, `absolute_expires_at`, `authenticated_at`, `last_interactive_at`, `revoked_at`, `replaced_by_token_hash` |
| `identity.passkeys` | `credential_id` | `user_id` (FK), `public_key_cose`, `signature_count`, `created_at` (Max 10 passkeys per user) |
| `identity.login_challenges` | `id` (UUID) | `user_id`, `kind` (`register`, `login`, `reset`), `request_json`, `dpop_jkt`, `staff_grant_id`, `expires_at` (2-minute TTL, max 5 concurrent per user) |
| `identity.staff_passkey_grants` | `id` (UUID) | `user_id`, `token_hash`, `activation_hash`, `kind` (`ENROLL`, `RECOVER`), `issued_by`, `identity_check_reference`, `failed_attempts`, `expires_at`, `used_at` |
| `identity.biometric_devices` | `id` (UUID) | `user_id`, `secret_hash`, `dpop_jkt`, `created_at`, `expires_at` (90 days), `last_used_at`, `revoked_at` |
| `identity.signup_challenges` | `email_fingerprint` | `request_id`, `email`, `username`, `password_hash`, `code_hash`, `dpop_jkt`, `eligible`, `delivery_state`, `failed_attempts`, `send_count`, `window_started_at`, `expires_at`, `used_at` |
| `identity.password_reset_codes` | `email_fingerprint` | `challenge_id`, `user_id`, `code_hash`, `dpop_jkt`, `delivery_state`, `failed_attempts`, `verified_at`, `reset_token_hash`, `reset_expires_at`, `used_at` |
| `identity.recovery_mail_outbox` | `id` (UUID) | `challenge_id`, `kind` (`CODE`, `SIGNUP`, `STAFF_INVITE`, `CHANGED`), `payload` (AES-256-GCM), `state` (`PENDING`, `SENT`, `FAILED`, `EXPIRED`), `attempts`, `available_at`, `expires_at` |
| `identity.document_checks` | `id` (UUID) | `user_id`, `request_key`, `status` (`PENDING_REVIEW`, `CONFIRMED`, `REJECTED`, `WITHDRAWN`, `EXPIRED`), `payload_fingerprint`, `sealed_fields`, `masked_document_number`, `reviewed_by`, `review_reference`, `reviewed_at` |
| `identity.staff_account_provisioning` | `user_id` (UUID) | `created_by`, `request_id`, `request_fingerprint`, `display_name`, `created_at` |

---

## 3. Domain State Machines & Invariants

### 3.1. User Account Lockout & Status Lifecycle (`User.java`)
- **`unlockIfLockExpired(Instant now)`**:
  - If `status == LOCKED` and `lockedUntil != null && !lockedUntil.isAfter(now)`, transitions `status -> ACTIVE`, `lockedUntil -> null`, and `failedLoginAttempts -> 0`.
- **`recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration)`**:
  - Increments `failedLoginAttempts`. When `failedLoginAttempts >= maxAttempts` (default `5`), transitions `status -> LOCKED` and sets `lockedUntil = now.plus(lockDuration)` (`15 minutes`).
- **`recordSuccessfulLogin(Instant now)`**:
  - Resets `failedLoginAttempts = 0`, `lockedUntil = null`, and updates `lastLoginAt = now`.

### 3.2. eKYC Document Check State Machine (`DocumentIdentityService.java`)
- `PENDING_REVIEW` -> `CONFIRMED` (Staff verified original document, holder, and information; `sealed_fields` erased to `NULL`, `payload_fingerprint` retained for matching).
- `PENDING_REVIEW` -> `REJECTED` (Staff rejected in-person check; `sealed_fields` erased to `NULL`).
- `PENDING_REVIEW` -> `WITHDRAWN` (Patient withdrew pending request; `sealed_fields` erased to `NULL`).
- `PENDING_REVIEW` -> `EXPIRED` (Automatically expires after 7 days; `sealed_fields` erased to `NULL`).

---

## 4. Error Matrix

| Scenario | HTTP Status | Vietnamese Message |
| :--- | :--- | :--- |
| Invalid login credentials | `401 Unauthorized` | `Thông tin đăng nhập không hợp lệ` |
| Account disabled | `403 Forbidden` | `Tài khoản đã bị vô hiệu hóa` |
| Account temporarily locked | `403 Forbidden` | `Tài khoản đang bị khóa` |
| Staff attempting password login when passkey required | `403 Forbidden` | `Tài khoản nội bộ phải đăng nhập bằng passkey đã đăng ký.` |
| Refresh token DPoP thumbprint mismatch | `401 Unauthorized` | `Refresh token không khớp khóa client` |
| Refresh token revoked / reuse detected | `401 Unauthorized` | `Refresh token đã bị thu hồi` |
| Admin step-up passkey older than 5 minutes | `403 Forbidden` | `Vui lòng đăng nhập lại bằng passkey quản trị trước thao tác này.` |
| Removing last active passkey-enabled admin | `409 Conflict` | `Phải giữ ít nhất một quản trị viên hoạt động có passkey.` |
| Unconfirmed eKYC document match | `409 Conflict` | `Hồ sơ chưa khớp thông tin đã được đối chiếu trực tiếp của tài khoản.` |

---

## 5. Sequence Diagram (Eraser.io DSL)

```eraser
title CareFlow Gateway Introspection, Passkey Login & Staff Step-Up Provisioning

Client [icon: monitor]
Gateway [icon: shield, label: "careflow-api-gateway"]
Identity [icon: lock, label: "careflow-identity-service"]
Postgres [icon: database, label: "PostgreSQL (identity)"]

// 1. WebAuthn Passkey Login with DPoP Binding
Client -> Gateway: POST /api/auth/passkeys/login/options (identifier, DPoP)
Gateway -> Identity: Forward public auth route (rate-limited by AuthSourceRateFilter)
Identity -> Postgres: Lock user & insert 2-min challenge in identity.login_challenges
Identity --> Client: 200 OK (requestId, PublicKeyCredentialRequestOptions)

Client -> Gateway: POST /api/auth/passkeys/login/finish (requestId, assertion, DPoP)
Gateway -> Identity: Forward assertion + DPoP proof
Identity -> Postgres: DELETE challenge RETURNING user_id, request_json
Identity -> Identity: Verify WebAuthn assertion & update signature_count
Identity -> Postgres: User.recordSuccessfulLogin() & issue DPoP-bound PASSKEY RefreshToken
Identity --> Client: 200 OK (accessToken, refreshToken, expiresIn)

// 2. Authenticated Admin Step-Up Staff Account Provisioning
Client -> Gateway: POST /api/auth/admin/staff-accounts (Bearer JWT, DPoP)
Gateway -> Identity: ActiveSessionClient.verify(Bearer, X-Correlation-Id)
Identity -> Postgres: Validate active session & non-expired token
Identity --> Gateway: Principal (userId, role=ADMIN)
Gateway -> Identity: Forward with X-User-Id, X-User-Role, X-Correlation-Id
Identity -> Postgres: SELECT pg_advisory_xact_lock(ADMINISTRATION_LOCK)
Identity -> Postgres: Verify ADMIN active PASSKEY session authenticated_at <= 5m
Identity -> Postgres: Insert User + staff_account_provisioning + staff_account_audit
Identity --> Client: 201 Created (StaffAccount summary)
```
