# Mac local demonstration

User selected running the real system on the current Mac for a thesis presentation. Keep the integrated security branch and root develop/user edits intact; no stage, commit, push or remote DB writes. Reuse existing PostgreSQL/RabbitMQ containers and reviewed JARs. Do not introduce simulated clinical API responses or claim real biometrics/eKYC.

## GitNexus Impact Assessment

2026-10-15: integration index up-to-date; CLI query for launch/health/mTLS and impact run before planning. createEmulator: LOW, one direct caller in server.mjs, zero mapped process groups. Existing emulator code will be reused without edits. MFA _action inspected (LOW, one direct build caller, one affected process group); no defect confirmed, so no changes needed. New local orchestration scripts have no existing callers; manually review process ownership, loopback-only configuration, TLS verification and private file paths.

## Execution and acceptance

1. Add Mac launch/status/backup commands; save only current runtime prerequisites outside Git so /tmp cleanup can be recovered. Verify idempotent start preserves healthy process PIDs and original dataset. Do not restart all backends just to test the launcher.
2. Verify actual API login for demo roles, cryptographic binding, replay denial, ownership, logout and private mTLS. Exercise real browser screens and check browser platform-authenticator availability without prompting for biometrics. Verify recovery of one owned frontend process.
3. Supply a short Vietnamese demonstration script, exact accounts/URLs and no-phone TOTP instructions using an authenticator on the Mac. Document that an existing-machine launcher is not a clean-machine installer, that Docker volumes must be preserved, and that eKYC/payment adapters are not real identity/payment services. Capture a real local DB backup; do not restore/reset live data automatically.

Launcher helper impacts before file-I/O corrections: private_json LOW (2 direct callers), spawn LOW (1), backup LOW (1), zero mapped flows. Endpoints/Java/Dart business functions unchanged.

Final evidence: 35 actual API checks, 14 health entries, 7 three-phone browser cases and 4 production Hospital Web cases passed. Private runtime snapshot and compressed PostgreSQL dump saved outside Git. Owned emulator recovery plus one missing TLS CSR restore verified without restarting healthy backend PIDs; full Mac reboot/DB restore not tested. Final aggregate GitNexus detect-changes remains CRITICAL due to the previously authorized security integration; Mac additions do not alter Java/Dart business code. Index FTS/BM25 remains unavailable; graph/impact/detect-changes and manual source review were used.
