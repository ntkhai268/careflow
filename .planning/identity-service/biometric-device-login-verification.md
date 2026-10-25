# Persistent biometric device login — 18 October 2026

Delivered as native **Release 1.0.0 (7)** with Identity schema **V12**. Backend feature worktree: `identity-email-recovery` (existing uncommitted security/email prerequisites retained); native: `patient-mobile-ios-login`. No staging, commits, pushes or clinical/demo-data resets.

## Implemented behavior

Ordinary logout revokes the active session and removes its local tokens while retaining a dedicated OS-protected device credential. Face ID/Touch ID unlocks that credential; a DPoP-bound backend endpoint creates a fresh session, replacing prior sessions for the same account/client key. It does not reuse a revoked refresh token. Other devices remain independent.

Password-confirmed enrollment returns a random 256-bit device token once, stores only SHA-256 on the server, binds it to the account/client key and expires it after 90 days. Explicit disable revokes registration while preserving the current session. Logout-all, password reset and administrative account restrictions revoke registrations via the global revocation path. Existing refresh-replay handling remains a session operation rather than interpreting a logged-out refresh token as a biometric credential.

Native session tokens stay in memory while registered, refresh rotation never overwrites the vault, same-account password/question login preserves registration, and account switching removes the prior local secret. Legacy refresh-based registration is removed and needs one explicit re-enrollment. Main login provides a direct biometric button. Public authentication forms survive backgrounding; protected content remains locked.

## Validation

| Area | Evidence |
|---|---|
| Common / Identity / Gateway Maven | **173 passed**, 0 failures/errors: 42 Common, 97 Identity, 34 Gateway |
| Real isolated PostgreSQL tests included above | **11 passed**: 6 email recovery + 5 device tests, including repeated logout/login, one-device disable, re-enrollment, password-reset revocation and concurrent global revocation/login |
| Flutter auth/store/UI/lifecycle/DPoP regressions | **45 passed** |
| Actual loopback HTTP transport contracts | **11 passed**: 4 device login/enrollment/disable/expiry, 3 refresh/question, 4 email recovery |
| Changed native source analysis | No issues |
| Live Gateway / two Identity instances / PostgreSQL | **8 passed**, [sanitized results](biometric-device-live-results.json); three logout/login cycles, stale tokens rejected, fresh sessions usable, cross-device independence, disable, expiry and global logout |
| Native build | Release 7 ARM64, strict codesign check, installed and launched on connected iPhone 11 Pro Max iOS 18.1.1 |
| Compatibility | Existing email code-verification/password separation retained; password/question and refresh regressions passed |

Real API tests use a uniquely created synthetic account and remove its credentials/sessions/account afterwards. No hardware authenticator is simulated to claim physical Face ID success. The opt-in integration database is a separate guarded loopback database; runtime keeps its existing audit data.

## Operations and remaining acceptance

Identity primary/secondary and Gateway were updated with staged JARs. Existing TLS/DPoP validation and the phone hostname bridge remain enabled. The Resend configuration is retained privately, with no device secrets/passwords/API keys in source, logs or mobile configuration. DBML and service contract were updated; sensitive registrations are never seeded.

GitNexus pre-edit analysis warned CRITICAL for RefreshTokenService, Identity/Gateway authentication filters and BiometricSessionStore; resolved native AuthService/ApiService were MEDIUM, login/settings state LOW. The lifecycle wrapper was disambiguated by full UID and returned LOW (one direct consumer). Ambiguous or missing graph entries were not treated as evidence of no impact. Indexes are refreshed after completion.

The user must re-enable Face ID once after upgrading, then accept the actual OS prompt and test logout → direct Face ID login on the iPhone. Logic and real server/session behavior are verified; the new physical sensor interaction awaits user acceptance. Android hardware acceptance is not claimed. Only one local biometric account is remembered per app installation. User steps and repeat conditions live only in [the canonical guide](../../huong-dan-su-dung/12-dang-nhap-mobile.md).
