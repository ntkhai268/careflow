# Persistent native biometric registration — 18 October 2026

Authorized behavior: ordinary logout revokes the session and keeps a separate biometric device registration. Face ID/Touch ID then unlocks an OS-protected, random device credential and the server issues a fresh session; never retain a logged-out refresh token as a workaround. Native mobile only; no emulator changes.

1. Add V12 biometric_devices with a SHA-256 hash of a 256-bit device token, account/client-key binding, 90-day expiry and revocation. Password-confirmed enrollment; anonymous DPoP-bound login; authenticated disable. Secrets never seeded/logged. No biometric images/password persistence. Re-enrollment revokes the prior registration for that account/key.
2. Single-session logout keeps device registration. Revoke-all (including password reset/admin restriction) revokes registrations. Biometric login replaces previous sessions for the same account/client key, avoiding accumulated sessions across background locks. Disabled, locked, expired/revoked or different-key credentials cannot log in.
3. Native vault stores only the dedicated credential, active tokens stay in memory. Refresh rotation no longer rewrites the biometric vault. Same-account password/question login preserves enrollment; switching account clears the old local credential. Explicit disable revokes on server and removes local credential while preserving the current session. Release-6 refresh-based enrollment is removed; user re-enables once after upgrading.
4. Add a direct Face ID/Touch ID button for the remembered account. Test logout→fresh biometric login, cancellation/offline, credential failures, account switching, reset/global revocation, transaction races and regression of email recovery. Build/install iPhone Release 7, update DBML/contracts/canonical guides and document remaining hardware acceptance.

## GitNexus Impact Assessment

Both feature worktrees pulled origin main (already current). GitNexus query/context and pre-edit impacts completed. RefreshTokenService CRITICAL: 2 direct, 134 processes; Identity JWT filter CRITICAL: 2 direct, 115 processes; Gateway JWT filter CRITICAL: 1 direct, 134 processes; BiometricSessionStore CRITICAL: 3 direct, 76 processes. Warnings reported before edits. Native AuthService MEDIUM (3 direct), ApiService MEDIUM (12 direct), login/settings state LOW (1 direct each). Tests/framework symbols without resolved callers UNKNOWN, not evidence of no impact. Generic build/setup graph edges are broad; source review and real runtime tests establish actual scope.

Backend remains isolated in identity-email-recovery with its existing uncommitted security/email prerequisites; native in patient-mobile-ios-login. No staging, commits, pushes, resets of queues or demo account passwords. Sensitive registration records are user-created and never seeded as demo data. Canonical user actions are maintained only under huong-dan-su-dung.

References: Apple Keychain biometric access control; OWASP MASVS-AUTH-2 and MASWE-0020 (OS-gated cryptographic storage, not a boolean local-auth check). Existing native vault uses biometric-only ACLs and invalidation on enrollment changes.
