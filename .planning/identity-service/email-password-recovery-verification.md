# Email-only password recovery — verification, 18 October 2026

Final implementation: V10/V11, native iOS **1.0.0 (6)**. Backend branch `feature/dangkhoii/identity-email-recovery`; native branch `feature/dangkhoii/patient-mobile-ios-login`. Changes are uncommitted/unpushed. Backend includes the prerequisite running Identity/Common/Gateway security source snapshot; see [baseline and impact assessment](email-password-recovery-plan.md). Other dirty worktrees were preserved.

## Result

- Separate request, verify-code and confirm-password APIs. Native password controls exist only after successful server verification. Direct reset, a code passed instead of a verified permission, wrong device, expiry and replay are rejected.
- OTP: six CSPRNG digits, BCrypt hash, five minutes, five wrong attempts, resend after 60 seconds and maximum five requests/email/hour persisted in PostgreSQL. Unknown emails follow the hashing/counter path without sending mail. No OTP/Resend key in API responses or logs.
- Verify consumes OTP and returns an opaque 256-bit permission with five-minute expiry; only its SHA-256 hash is persisted. Verification alone changes neither password nor sessions. Permission remains only in the native screen's memory. Confirm atomically updates password, consumes permission, revokes all sessions and emits a security event. Failed reset transactions preserve old password and retryable permission.
- Vietnamese HTML/plaintext CareFlow emails use `careflow@supersignal-resend.id.vn`. Brand is HTML table/text: **zero images, CID references or attachments**. Real Resend sent-email attachment API returned **0** for both the code email and password-change notification.
- Returning from Mail keeps `/login/recovery` and its form while locking the previous biometric session. Password/question/biometric login behavior remains available; legacy question/passkey password-reset APIs return 410.

## Evidence

| Check | Result |
|---|---|
| Maven Common / Identity / Gateway | **155 passed**, 0 failures/errors: 42 Common, 79 Identity (including 6 opt-in real PostgreSQL tests), 34 Gateway |
| Native auth/store/UI/lifecycle/DPoP/error regressions | **38 passed** |
| Native anonymous email HTTP contracts | **4 passed**, real loopback HTTP fixture: request, verify, reset, rejection without retries |
| Native refresh/question-settings HTTP regressions | **3 passed** |
| Dart analyze, changed production/auth files | No issues |
| Isolated PostgreSQL V1–V11 migration and transactions | **6 passed**: counters/resend, concurrent requests, atomic OTP verification, atomic permission redemption/session revocation, unknown-email verification, rollback |
| Real Gateway → two Identity instances → audit PostgreSQL → Resend | **10 passed**; [sanitized results](email-recovery-live-results.json) |
| Actual Resend attachment lists | 0 code attachments; 0 change-notification attachments |
| HTML render at 390px | Reviewed both templates; no horizontal overflow or external image dependencies |
| iPhone 11 Pro Max, iOS 18.1.1 | Release 6 signed, strict signature verification passed, installed and launched via devicectl |
| Phone HTTPS bridge | Hostname endpoint health `200 UP`; TLS verification retained |
| Secret isolation | Private key is outside Git/mobile bundle; source, JAR resources and App.framework checked |

Runtime Identity ports 18081 and 18092 and Gateway 18080 were updated with staged JARs; migration 11 applied to the existing audit database. Other clinical services/queues and shared demo credentials were not reset. Live regression created and removed its own synthetic accounts; mail was sent only to the official labeled Resend test sink. Test times/counters were adjusted only on those synthetic challenges to cover expiry/cooldown deterministically. Isolated PostgreSQL tests use a separately created database guarded by an exact loopback test URL.

Build/test/install logs, signing configuration, Resend key, runtime state and preview screenshots are in the private `~/Library/Developer/CareFlow/iphone11pm-20261018/` directory, outside Git. Maven used Temurin 25 with the Byte Buddy experimental test flag; no production Mockito dependency was changed.

## Risk and acceptance limits

Pre-edit GitNexus reported MEDIUM for UserRepository/AuthService/ApiService, LOW for the native form and CRITICAL fan-outs for recovery controller/service/sender and the application lifecycle wrapper. Warnings and counts were reported before edits. Some method-only lookups returned UNKNOWN, and generic setup/build relationships widened graph results; source review plus live execution were used rather than treating missing edges as evidence of safety. Final indexes are refreshed without generated instruction changes.

The user supplied Gmail screenshots proving receipt of the previous email and the CID/attachment defect. Newly requested email rendering in that Gmail Inbox and the complete split-screen interaction on iPhone Release 6 still require user acceptance. The automatic checks do not constitute Face ID/Touch ID hardware acceptance. No new physical Android acceptance is claimed.

Mail is queued after commit with a bounded executor; a crash before sending requires requesting another code. There is no durable mail outbox in this version. User steps, limits and repeat conditions are maintained only in [the canonical mobile guide](../../huong-dan-su-dung/12-dang-nhap-mobile.md).
