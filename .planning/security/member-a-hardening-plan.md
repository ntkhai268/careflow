# Member A security hardening — 2026-10-15

Authorization: user requested testing and repairs for the preceding security assessment. Existing per-service worktrees remain isolated; no implicit add/commit/push. Audit writes only to the existing private loopback PostgreSQL/RabbitMQ environment. Preserve demo accounts and user changes.

## GitNexus Impact Assessment

Index/query/context/impact performed before modifying existing symbols. Raw reports: /tmp/careflow-security-hardening-impact.
- RefreshTokenService.revoke: CRITICAL, 3 direct dependants, 6 affected flows. Warned user before edits. Scope: logout and refresh lineage.
- JwtService.issue/parse: LOW by graph, 1 direct caller each; login/refresh and request authentication.
- AuthService.login/refresh/logout/tokenResponse: LOW, 1–2 direct callers, authentication lifecycle.
- ActiveSessionClient.verify: LOW, 1 direct caller in its index; actual consumers include Gateway and all protected servlet services.
- JwtAuthenticator.authenticate: LOW, 2 direct callers; WebSocket CONNECT and existing-session guard.
- Spring filter entry points: UNKNOWN/zero graph callers; framework entry points require HTTP integration evidence.
Graph counts are lower bounds, not security guarantees.

## Execution and acceptance

1. Reproduce live audit authorization/session behavior; preserve baseline evidence.
2. Add persistent TOTP MFA, encrypted secrets, one-use recovery and reauthentication. Verify real enrollment, missing/wrong/reused OTP, cross-instance concurrency, recovery, enable/disable revocation and lockout.
3. Replace shared HMAC signing with Identity-only asymmetric signing and explicit audience/key configuration. Add cryptographic client-key-bound access/refresh tokens and replay-resistant request validation. Verify stolen token, wrong/missing proof, wrong method/URI, refresh and revoked tokens through Gateway and direct services.
4. Provide private caller authentication for delegated service calls; preserve end-user authorization. Configure safe external ingress/private ports and TLS. Verify public headers cannot supply caller identity and unavailable validation never executes commands.
5. Update browser client/emulator to exercise real MFA and bound tokens. Rebuild source, run focused regression and real API/browser acceptance. Document exact deployed sources, unintegrated branches, limitations and evidence; never describe local multi-JVM checks as multi-host production certification.

## Policy and compatibility

MFA uses RFC 6238 TOTP; no security questions as authentication/recovery factor. Biometric/passkey is a separate authenticator requiring actual enrollment and user verification; not claimed implemented by TOTP. eKYC mock must never grant authentication or privileges.
Bound request validation follows DPoP cryptographic principles; document supported profile and HTTPS requirements without claiming full OAuth-server conformance. Any legacy unbound-token compatibility must be explicit and disabled in hardened deployment. Internal calls carry user delegation plus independently authenticated service identity.

Additional assessed scope: Hospital Web request helper CRITICAL (5 direct dependants, 31 flows, 9 modules, 116 symbols); Mobile API MEDIUM (12 direct callers); Node createEmulator LOW (1 caller). Final integrated detect-changes after documentation and fixture updates: 101 changed tracked files, 381 mapped symbols, 455 processes, CRITICAL; graph counts are lower bounds. Untracked source reviewed separately. See results and runbook.
