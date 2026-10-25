# Email-only forgot password — 2026-10-18

Authorized scope: forgot password only. Use Resend, sender `CareFlow <careflow@supersignal-resend.id.vn>`; domain verified by user. Keep password login, biometric session unlock and question login. Disable legacy question/passkey password recovery on server. Native app only; emulator is not the delivery target.

1. Add V10 migration and DBML for hashed OTP challenges and persisted resend/attempt limits. Six secure random digits, five-minute expiry, five guesses, 60-second resend cooldown, five requests per email/hour; bind challenge to client DPoP key. No plaintext OTP or Resend key in logs/API/Git; the one-use reset permission is returned only after verification with no-store and stored only as a hash. Unknown/ineligible emails receive the same response and timing path.
2. Implement request/verify/confirm endpoints, one-use transactional verification, password policy, all-session revocation, confirmation email. Send HTML/text templates through backend Resend HTTPS only, with private runtime key. Check provider acceptance; never claim inbox delivery from a successful API response alone.
3. Replace native question recovery UI with email → code verification → new-password flow. Keep question login and security setup. Clear local sessions after successful reset. Explain resend timer and failure states.
4. Test wrong/expired/consumed codes, limits, resend invalidation, races, sender/template, request authentication, legacy route rejection, session revocation, native form/navigation. Build/install iPhone Release; test real mailbox only with user-provided recipient. Update canonical usage guides and contract.

## GitNexus Impact Assessment

Index refreshed before work. UserRepository: MEDIUM, 7 direct dependents / 10 total (auth, login methods, refresh and initializer). Identity JwtAuthenticationFilter: LOW, direct SecurityConfig consumer. Controller, Gateway filter and SecurityConfig framework entry points: UNKNOWN/no callers enumerated; this is not proof of no impact. Source-confirmed flows: public recovery, protected identity, question/passkey login and gateway authentication. Mobile impacts recorded in verification report. No main push, staging or commits.

## Baseline isolation

Backend branch `feature/dangkhoii/identity-email-recovery` is based on security-integration HEAD plus a source snapshot of its running uncommitted Identity/Common/Gateway security baseline. This copied baseline is a prerequisite, not new email-recovery implementation. Native source remains on `feature/dangkhoii/patient-mobile-ios-login`. Both pulled origin main before implementation. Preserve all other dirty checkouts.

## Final flow and scope refinement

User requested separate code verification before the password screen and removal of Gmail logo attachments. V11 adds verified_at/reset_token_hash/reset_expires_at; verify consumes OTP and issues a 256-bit five-minute device-bound permission without changing the password or sessions. Confirm requires and atomically consumes that permission. Both code and permission race conditions are covered. HTML branding replaces CID/images; no attachments are sent.

Further pre-edit GitNexus assessments: PasswordRecoveryService CRITICAL (1 direct / 134 processes), PasswordRecoveryController CRITICAL (1 / 134), ResendEmailSender CRITICAL (1 / 135), _BiometricAppLockState CRITICAL (2 / 105), _ForgotPasswordScreenState LOW (1 direct / 5 total), PasswordResetCode UNKNOWN (0 resolved). Warnings were reported before changes. Context confirms controller/service and main.dart/lifecycle-test entry points; some graph relationships fan out through generic setup/build names and method-only lookups return UNKNOWN. These limitations do not replace source review or downgrade warnings. Lifecycle exception applies only to /login/recovery, while the previous authenticated session remains locked. Tests and live API validation are required evidence.

See email-password-recovery-verification.md for final build/test/runtime status. Canonical usage docs were snapshotted into the backend worktree because its older baseline predates that directory; no separate user guide was created outside huong-dan-su-dung.
