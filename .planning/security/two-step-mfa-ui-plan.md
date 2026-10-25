# Two-step MFA sign-in UI — 2026-10-16

User asks for a separate MFA screen after username/password, removing factor input from the initial sign-in screen. Applies to Mobile Emulator and Hospital Web. Existing authenticated MFA enrollment screen remains available in Account → Bảo mật đăng nhập.

## Contract and design

Identity already verifies password first and returns 401 `MFA_REQUIRED:` only for enabled MFA with no factor. No token/session is issued until OTP or one-use recovery code is verified. Keep this backend contract: client first submits credentials alone, then presents /login/mfa only after that explicit result. Resubmit credentials with factor through the existing proof-bound login endpoint. Credentials stay only in transient memory, expire after five minutes, clear on completion/cancel/logout/reload; no URL/history/storage persistence. Wrong password never opens MFA. Wrong factor remains on the second screen. Non-MFA accounts retain one-step login.

## GitNexus Impact Assessment

Index checked before plan; queried login/MFA flows and inspected local auth contract and source. AuthNotifier MEDIUM, 13 direct / 34 affected symbols; AuthService MEDIUM, 1 direct / 34 affected symbols. Existing Dart login/logout method queries were unresolved; class impacts plus manual reads cover those methods. Login screen state LOW, 1 direct. AuthStatus/appRouter UNKNOWN, framework/constant dispatch manually reviewed. Web AuthProvider LOW, 1 direct; AuthContextType MEDIUM, 14 direct; provider login LOW, 1 direct; logout LOW, 2 affected (graph lower bound); LoginPage UNKNOWN framework entry. Manual blast radius is both clients' sign-in and session handoff; session verification/DPoP/backend MFA validators unchanged.

## Steps and verification

1. Typed MFA-required handling in Mobile AuthService; transient MFA state in notifier; remove initial factor field; add dedicated challenge route/screen with authenticator and recovery modes, errors, cancel and loading. Preserve authenticated enrollment.
2. Add equivalent transient challenge state and /login/mfa screen to Hospital Web, removing initial factor field. Grant authenticated navigation only after completed login.
3. Focused Flutter state tests: no-MFA success, explicit challenge, wrong password, wrong factor, recovery, cancel and expiry. Real browser tests on fresh audit-only accounts with real enrollment: initial screens contain no MFA input; correct password opens second step with no token; wrong factor stays unauthenticated; correct OTP/recovery completes login; cancel/reload clears pending state. Existing users' MFA settings untouched.
4. Build/deploy both clients, run ordinary login regression, update graph and document tested scope. No DB schema change, reset, mock runtime, stage/commit/push.

Additional test maintenance: legacy security-browser-smoke login helper impact LOW, 1 direct test caller (lower bound), updated to submit recovery only after the explicit MFA screen. Auth enrollment and ordinary Hospital Web login/reload/logout regressions passed. New Flutter transport fixture was corrected to accept both Dio map/string request representations; runtime does not use the fixture.

During real browser verification, individual device Reload was proven to be same-document hash navigation: performance.timeOrigin unchanged and outer card stayed loading, blocking subsequent logout controls. Additional impact before edit: loadDevice CRITICAL, 4 direct callers/7 processes, 2 modules; warning communicated, proceeding under the user's existing authorization for security/flow repairs. Add a non-secret per-navigation query nonce to force a document reload while keeping device/storage/DPoP scope unchanged. Verify actual new timeOrigin, ready handshake and normal session operations; do not reset storage/DB.

## Completed

Both separate MFA screens built and deployed. 14 Flutter state/refresh tests, 28 Node unit tests, 20 real MFA browser checks, 8 final enrollment/security checks and 4 ordinary Hospital Web auth checks passed. Actual reload bug repaired and verified with new timeOrigin/ready handshake; visual QA confirmed both screens. Full results: [two-step-mfa-ui-results.md](two-step-mfa-ui-results.md). No backend schema/auth protocol changes, existing-user MFA changes or Git mutations.
