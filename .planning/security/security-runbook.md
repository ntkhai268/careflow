# CareFlow security v2 — runbook

Source: `.worktrees/security-integration`, branch `feature/dangkhoii/security-integration`, based on develop `5a8c6e574c1249ce2a6dc791e7415a2bbc33b006`. Security changes remain unstaged/uncommitted. Root develop and the user's Doctor Web edits are preserved.

## Request acceptance rules

1. Login is an endpoint reached **through Gateway**, then Identity. Identity verifies password, account status, the chosen enrolled login method (MFA/TOTP retired 2026-10-17) and a signed P-256 client proof. A login session is stored in Identity PostgreSQL; it is not memory owned by Gateway.
2. Identity alone signs RS256 access JWTs. Each includes issuer/audience/kid, user/role, expiry, a DB session `jti` and `cnf.jkt` client public-key thumbprint. Refresh tokens are opaque, stored hashed, rotated once and bound to the same key.
3. Each client request includes `Authorization: Bearer <access>` and fresh `DPoP: <ES256 proof>`. Proof carries public JWK, `jti`, `iat`, `htm`, `htu`, and access-token hash `ath`. `htu` uses a configured public origin and path without query/fragment. Identity checks allowed origins and the path; proxy must not rewrite the API path. This is CareFlow's Bearer + DPoP profile, not a full OAuth2 authorization-server implementation of RFC 9449.
4. Gateway strips all client-supplied identity/private caller/context headers, verifies the active user with Identity over mTLS, then routes using private HTTPS. Resource services independently validate the same JWT/session and proof, then apply role, ownership, assignment and state transition rules. Gateway preflight does not consume the proof; the resource consumes it once in the shared replay DB.
5. Delegated service calls forward the original user token and attach that caller's independent credential. Receiving service checks its actual mTLS certificate CN, and Identity checks caller credential plus method/path scopes. A DPoP proof from the original public route is not reused for a different internal path. Service identity never replaces end-user permissions.
6. Missing/invalid/replayed proof, wrong key, stale/expired/revoked session: 401. Valid identity with disallowed role/ownership/assignment or caller scope: 403. Dependency failure while verifying session/clinical authority: 503 and no mutation. Login account LOCKED/DISABLED follows the existing 403 contract.
7. Logout/refresh rotation/status/role changes revoke old access; logout-all revokes every session. WebSocket CONNECT authenticates proof and session; outbound MESSAGE rechecks and an idle sweep runs every second. Revocation or unavailable Identity closes the connection.

A service runs as a network process on a host/container, not merely as a diagram element. It accepts no arbitrary command to "activate" itself: it must be running, reachable on an authorized private route, and authenticate/authorize each request. Across hosts, use private DNS, trusted TLS names, time synchronization and the same authoritative session/replay database. Replicate Identity only with shared state and the same supported signing configuration; this audit tested two JVMs on one host, not physical multi-host failover.

## Local audit currently running

| Entry | Address |
|---|---|
| Mobile emulator (integrated source) | http://localhost:3000 |
| Hospital Web production build | http://localhost:5173/login |
| Public audit Gateway | https://localhost:18080 |
| Private Identity / secondary | HTTPS 18081 / 18092 |
| Other private services | HTTPS 18082–18090 |

All backend listeners bind loopback. Private ports require client certificates; Gateway does not require a client certificate from browsers. The emulator and Hospital Web proxy trust the audit CA while retaining certificate/hostname verification. Local browser HTTP is permitted only on localhost for WebCrypto; external ingress must use HTTPS/WSS.

Current demo accounts, passwords and user actions are managed only in [the canonical guide](../../huong-dan-su-dung/01-tai-khoan-va-du-lieu.md). The 17/10 login methods replace the historical MFA procedure. Positive passkey hardware verification is still pending. API/DB E2E results: [17/10 report](end-to-end-2026-10-17.md).

The legacy eKYC endpoint remains explicitly `mock:true` and must not be used to grant login/role bypass. Actual acceptance evidence excludes eKYC as a real identity-verification claim. No clinical API response is mocked by the audit/browser harness.

Private audit state and keys are outside Git in `/tmp/careflow-dangkhoii-audit/`; never print/share `state.json`, `private-config.json`, JWT private key, client keys. Leaf certificates expire after 90 days; renewal requires owned service restarts and proxy restarts to reload the CA. Audit services share the same OS account, so this is not a hostile-workload isolation test.

## Repeat validation

Backend from integration root (JDK 25 on this machine requires Byte Buddy's experimental flag; dummy caller config is for tests only):

```sh
mvn -q -DargLine=-Dnet.bytebuddy.experimental=true \
  -Dsecurity.caller.name=test-context \
  -Dsecurity.caller.key=test-only-credential-01234567890123456789012345 verify
cd careflow-mobile-emulator-service
npm test
npm run test:integration
npm run test:security -- --audit-db
npm run test:member-a -- --audit-db
npm run test:dataset
npm run test:security:browser
```

`npm run test:security -- --audit-db --identity-outage` additionally stops and recovers **only the owned audit Identity**; expect a brief interruption. This flag is required for the outage check. All mutation scripts are restricted to the existing loopback audit environment. They create real audit users/rows/events, so counts can vary with persistent idempotent seed state. Do not point them at the remote DB or production. Expiry/concurrency probe JVMs are stopped by their runners after the checks. Playwright and Chrome paths may be provided via `PLAYWRIGHT_MODULE` / `CHROME_PATH`.

`node scripts/security/hospital-browser-smoke.mjs` tests production Hospital Web login/reload/logout. Frontend validation: `npx tsc --noEmit` then production `npm run build`; the started build uses `NEXT_PUBLIC_API_URL=http://localhost:5173`, `CAREFLOW_GATEWAY_URL=https://localhost:18080`, and `NODE_EXTRA_CA_CERTS` pointing to the audit CA. The emulator uses ignored `.runtime/launch.json` to select the same HTTPS Gateway/CA.

Audit provisioning/rollout helpers are deliberately specific to the existing private audit state, not production installers. `scripts/security/rollout-audit.py --audit-db` deploys the current built JARs and restarts owned services; do not run it merely to inspect health. `provision-audit-tls.py --audit-db --renew-certificates` renews audit certs and requires subsequent backend/proxy restarts. It does not rotate the client/signing keys.

## Deployment template and remaining gates

`docker-compose.security.yml` is a standalone template, not an override of the insecure demo compose. It activates profile `secure`, publishes only Gateway (loopback 8443 by default), mounts a unique service PKCS12 and shared truststore, keeps the JWT private key mounted only to Identity, and requires independent service credentials and database/RabbitMQ credentials. Known-password demo seed initializers are disabled in `secure`; operator provisioning of the first administrator is required. Default demo profile still seeds today's sample queues/master data for local testing.

Supply `CAREFLOW_SECURITY_DIR`, public API/web origins, per-service TLS passwords/caller keys, `SECURITY_CALLERS_JSON` and Identity signing settings. Caller registry JSON shape:

```json
{"appointment-service":{"secret":"<independent-random-secret-at-least-32-chars>","scopes":["GET:/api/patients/**","GET:/api/directory/**"]}}
```

Provision every actual caller with its own random secret; certificate CN must match the logical caller name (e.g. `appointment-service`). Never use a wildcard method or an unrestricted ADMIN identity as delegation. Certificates need SANs matching private DNS, clientAuth/serverAuth as appropriate and an operator-managed CA. Example registry scope lists in the local audit state are environment-specific, not credentials to copy into Git.

The compose configuration validates; the container stack itself has **not** been deployed in this task. Before production, configure verified TLS for PostgreSQL URLs (including remote host `100.116.233.60`), the actual DB CA and least-privilege DB users, RabbitMQ TLS/ACLs, firewall/private networks, certificate revocation/rotation, managed signing keys and backups, and rate/abuse controls for public registration/login. The template's DB URL defaults retain repository remote-host conventions and must be replaced with verified TLS URLs; they do not prove encrypted remote DB transport. No connection/write to the remote DB was performed.

Further required operational validation: real multi-host HA/network partitions, load/availability targets for online introspection, backup/restore and key rotation. Online session checks deliberately trade availability for immediate revocation: Identity unavailable means deny/503. Browser DPoP reduces stolen-token reuse but does not defeat same-origin malicious JavaScript; XSS hardening and external penetration testing remain necessary. Do not state “100% safe” or full production completion from these local tests.

References: [RFC 9449](https://www.rfc-editor.org/rfc/rfc9449.html), [RFC 8705](https://www.rfc-editor.org/rfc/rfc8705.html), [NIST SP 800-63B](https://pages.nist.gov/800-63-4/sp800-63b.html).
