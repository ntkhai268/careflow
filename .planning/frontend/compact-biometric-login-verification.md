# Compact biometric login — verification, 18 October 2026

Scope: native patient app, `feature/dangkhoii/patient-mobile-ios-login`, Release 1.0.0 (8). No new backend, database, API, or credential-storage changes. Existing uncommitted authentication/recovery work was preserved. `git pull --ff-only origin main` returned already up to date; no staging, commit, or push was performed.

## GitNexus Impact Assessment

- `_LoginScreenState`: LOW; 1 direct importer (`router.dart`), 5 upstream symbols in the initial class analysis, no indexed affected processes.
- Ran upstream impact for `_loadBiometrics`, `dispose`, and `build` before editing. Initial parser cache incorrectly assigned Dart method names to Java Identity UIDs and produced CRITICAL results. Paused source edits, warned the user, and rebuilt with `analyze --force --no-parse-cache --index-only`.
- Rebuilt `_loadBiometrics` result: correct Dart UID, LOW, 2 direct callers (`initState`, `build`), no indexed processes, Auth module.
- Rebuilt `build`/`dispose`: correct Dart UIDs, UNKNOWN because Flutter framework lifecycle callers are not resolved. Confirmed the containing widget is used by the native login route; zero indexed callers is not treated as proof of no impact.
- Test `main` impact: UNKNOWN, no callers; test runner invokes it. Nested `open` helper was not indexed. Changes remain confined to this test file and login state; new handlers are local UI methods.
- Re-indexed after completing the feature. No commit was requested.

## Result

Remembered biometric username is prefilled once; asynchronous loading and error retries do not overwrite user edits. Only a matching normalized username with available hardware shows the compact password field and 56-point face/fingerprint button. Icon activation uses the existing OS-gated server login and bypasses password-form validation. Account edits clear the entered password, expand its field, and hide the remembered-account icon. Explicit account switching clears both fields and focuses the username without deleting enrollment. Inputs and icon are disabled during authentication. The registration protocol and existing keychain data remain unchanged from Release 7.

Canonical user instructions: [mobile login](../../huong-dan-su-dung/12-dang-nhap-mobile.md).

## Validation

- 52 mobile regression tests passed across session storage, login methods, biometric failures, app lock/lifecycle, email recovery, DPoP, auth response parsing, and API error handling.
- Includes 9 login widget tests: prefill/compact width/icon login; cancellation; editing another username; explicit switching with ordinary password submission; delayed account loading; no enrollment; unavailable sensor; Touch ID icon/label; 360×640 viewport with 1.5× text and keyboard. The last case identified a registration-link row overflow; changed it to a wrapping layout and verified the full suite passes.
- Dart analysis of changed production/test files: no issues. Diff whitespace check passed.
- An exploratory widget bitmap used substitute fonts and lacked complete test font assets; it is not accepted as typography, icon rendering, or device visual evidence. The temporary capture test was removed.
- iPhone 11 Pro Max remains connected, iOS 18.1.1. Runtime HTTPS bridge health is UP. Backend was not restarted.
- Release 8 build succeeded; `codesign --verify --deep --strict` passed, bundle build number is 8. Installed over the existing app successfully, preserving its data/enrollment. Private build/install logs are outside Git.
- The first launch attempt was denied while the phone was locked; a subsequent launch succeeded. Inspecting the new layout and approving real Face ID remain user/device acceptance. No claim of physical sensor verification is made.
- No automatic reenrollment is needed when updating from Release 7. Legacy Releases 4–6 still require the previously documented one-time migration.

The 173 backend tests and real-device-credential API checks reported for Release 7 are prior evidence, not rerun counts for this UI-only change.
