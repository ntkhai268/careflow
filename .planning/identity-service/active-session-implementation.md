> Historical baseline before security v2. Current schema, binding and migration rules: [security v2 results](../security/member-a-hardening-results.md). The legacy refresh compatibility described below is superseded; current rollout requires re-login.

# Identity — active session authority

Branch: feature/dangkhoii/identity-active-session. Baseline origin/develop 8bf77e2; git pull origin main already up to date. No staging/commit/push/merge. This implements Identity session authority; distributed consumers must still be upgraded before claiming direct-service revocation.

## Implemented policy

Access JWT jti references the existing refresh_tokens UUID row. Identity verifies mandatory signed claims, then row ownership, revocation/expiry, ACTIVE user status and matching current role. Effective expiry is the earlier JWT/session expiration. Login and refresh JSON fields are unchanged; clients treat tokens as opaque.

Logout follows the supplied refresh's replacement chain to the current row, preserving an independently logged-in device. Logout-all uses the authenticated account. Refresh rotation invalidates the prior access token. Existing replay detection revokes active rows of all devices. Lock/disable/change-role/reactivation and password-attempt lock revoke rows; reactivation or changing back to the old role cannot resurrect a token. ADMIN manual LOCKED is not silently timed/unlocked.

All token writers take account lock before token lock, consistent with existing login lock and new role/status update lock. Existing hashed refresh storage and PESSIMISTIC_WRITE token query are retained. No entity/table/column changes or new migrations; DBML only clarifies jti linkage.

GET /api/auth/session returns only active, userId, role, sessionId and expiresAt after validation. Database validation failure returns 503; invalid/inactive tokens return 401. New POST /api/auth/logout-all is protected. CF-SVC-02 contract is updated to v1.1; existing API response/TTL settings remain compatible. Old unrelated access jti is rejected at cutover; a valid legacy refresh can rotate to a bound pair.

## GitNexus Impact Assessment

Fresh worktree index and query/context/impact done before edits. Jwt issue/parse and Auth login/refresh/status/role/tokenResponse: LOW, one or two direct callers and affected auth/user-controller entrypoints. Refresh issue/rotate/revoke/create: LOW, auth/token dependencies. HTTP filter/controller/tests: UNKNOWN; manual radius is every protected Identity HTTP request. User approval to continue reported HIGH/CRITICAL security repairs remains in force; final aggregate scope is checked independently.

## Evidence

Baseline live logout followed by access-token /auth/me returned 200 (expected 401), recorded in /tmp/careflow-identity-session-baseline.json.

Final unit suite: 40 tests passed, zero failures/errors/skips. Covers mandatory claims before DB lookup, revoked/expired/unknown/misbound sessions, role/status flags, effective expiry, authority 503, role/status revocation and original login/refresh/eKYC regressions.

Run python3 scripts/identity-active-session-smoke.py --audit-db with the dedicated audit runtime. 50 HTTP cases pass, using Gateway/mobile register/login/refresh/logout and two Identity JVMs sharing the audit DB. Logout current/all, replacement-chain logout, role change then restore, disable then reactivate, five-failure lock, refresh reuse and concurrent refresh are exercised. Exactly one concurrent refresh wins; reuse invalidates its replacement and other active devices as designed. Two JVMs simulate multiple instances, not separate physical hosts.

Primary Identity is loopback 18081; secondary is loopback 18092 with Eureka disabled to isolate the comparison. Both use the same copied verified jar, private audit JWT key and audit DB. State/secrets remain in /tmp/careflow-dangkhoii-audit/state.json, mode 600. Only disposable audit users are modified; seeded admin is used as an authorized operator, not mutated. Reports contain only case/status; /tmp/careflow-identity-active-session-live-results.json.

## Remaining boundary

Business services and Gateway still validate signatures alone until the shared authority consumer is integrated. No distributed-session guarantee is claimed at this checkpoint. Asymmetric signing/JWKS, secret fallback removal, MFA and caller credentials remain separate features. No absolute security guarantee is inferred from these tests. Requests authorized before a revocation commits may finish; each later authority validation observes committed state.


## Consumer deployment continuation

The original standalone checkpoint above is superseded for deployed HTTP consumers: Gateway, Patient, Consultation, Prescription, EMR, Appointment, Directory and Lab now use the shared online authority in their owned audit processes. The Common extended smoke passes 41 HTTP cases, including revoked tokens on all protected direct ports, spoofed identity, authority unavailable returning 503 at every consumer/Gateway, and recovery. Report `/tmp/careflow-common-session-all-consumer-results.json` in the Common worktree's reproduction documentation. Requests already authorized while shutdown/revocation is in progress may finish; the outage test now waits for the authority process to exit before asserting unavailable.

Final Identity full-rebuild detect-changes reported CRITICAL, 58 symbols and 55 indexed flows, within the user's continued authorization/security scope; no source changes/staging/commits in this continuation. Asymmetric signing/JWKS, caller credentials, audience and MFA remain separate work. Existing production HMAC/key configuration is not represented as a complete key architecture.
