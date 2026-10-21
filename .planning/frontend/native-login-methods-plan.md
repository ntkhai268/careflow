# Native mobile login methods — 2026-10-18

Scope: Flutter app `frontend/patient-mobile`, branch `feature/dangkhoii/patient-mobile-ios-login`. Reuse the integrated emulator's method-selection, question picker, notice and settings layouts; implement transport/authentication natively. Do not change the emulator or backend schema. Existing HTTPS/DPoP setup remains required.

## Plan and acceptance

1. Discover device capabilities with `local_auth`; use biometric-gated `biometric_storage` (iOS Keychain `biometryCurrentSet`, Android Keystore) for the refresh token. iPhone face -> Face ID, iPhone fingerprint -> Touch ID, Android -> available secure biometrics. No enrolled biometrics/unsupported device must explain the condition and keep password/question available. Do not save account passwords or biometric data.
2. Enroll only from an authenticated account after current-password verification. Keep active biometric session tokens in memory, with refresh token only in the biometric vault. A new process starts locked; an explicit Lock action also clears in-memory tokens. Unlock requires OS biometric approval followed by server refresh and identity verification. Full logout revokes the current session and removes local enrollment. Disabling biometrics migrates the active session back to regular secure storage. Server expiry/revocation requires password/question login; cancellation and network failures do not bypass authentication.
3. Implement existing Identity question APIs: status, password-confirmed question setup, question login, question password recovery. Reuse the three server-defined question IDs. Never persist/log answers. Validate required fields, confirmation and UTF-8 answer limits; show server lockout/errors.
4. Expose login methods from login and security settings from Account. Native OS prompts must replace the web/browser bridge. Preserve layouts, spacing and palette from emulator; replace browser/passkey-specific wording with actual native behavior.
5. Verify token isolation, process restart/lock, cancellation/no HTTP, rotation, invalid refresh, question contract and validation, screen layout/navigation and existing auth/DPoP regression. Build signed iOS Release, install/launch on the connected iPhone. Face ID approval and any Android fingerprint behavior require physical-device acceptance; do not claim these based on mocks or Mac API checks.

## Implementation distinction

The current browser passkey RP is `localhost`. Native WebAuthn passkeys require associated domains and a matching deployed relying-party domain ([Apple native passkey sample](https://developer.apple.com/documentation/authenticationservices/connecting-to-a-service-with-passkeys)), which the current Personal Team/local test setup does not provide. This implementation uses OS-gated session unlock with server refresh; it must not label this as a newly registered server passkey. No password is stored. Enrolled biometrics changing invalidates access to the vault; user re-enrolls after password/question login. All API requests retain TLS validation and DPoP.

## Delivery

Canonical usage/acceptance steps live in `huong-dan-su-dung/`; record device/build and unverified paths there. Keep runtime endpoint, CA asset, TLS private keys and personal signing outside Git. No automatic staging, commits or pushes.

## Current result

Implementation complete in native source; 36 targeted checks passed, including a real local HTTP fixture for single-flight refresh and no retries of wrong credentials. Live question API audit passed. Signed Release 1.0.0 (4) installed/launched on the connected iPhone. Sensor approval and native end-to-end acceptance remain pending user confirmation. Android debug APK compilation passed after installing the required toolchain and applying narrow Gradle compatibility fixes. Physical Android/Touch ID sensor coverage remains pending. See the verification report for evidence and limits.
