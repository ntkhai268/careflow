# Native login methods — verification 2026-10-18

## Scope and source

- Branch: `feature/dangkhoii/patient-mobile-ios-login`, based on `develop@e5fc9c1`; `git pull --ff-only origin main` returned already up to date before implementation. Source changes remain uncommitted.
- Flutter native `frontend/patient-mobile`; no emulator or backend/schema edits. Existing integration runtime provides login-question APIs and DPoP-bound rotating refresh sessions.
- Emulator method picker, question bottom sheet, settings and notice layout reused. Browser bridge/WebAuthn interaction replaced by OS biometric vault operations. Native biometrics unlock a device session; no claim of registering a server passkey under the localhost RP.
- Source plan: `native-login-methods-plan.md`. Canonical user instructions: `huong-dan-su-dung/12-dang-nhap-mobile.md`.

## Impact before edits

GitNexus indexes were refreshed with `analyze --index-only`. Root keyword search remains degraded by missing Method FTS; the feature index completed graph analysis.

| Symbol | Reported risk | Direct/total dependencies |
|---|---|---|
| ApiService | MEDIUM | 10 direct imports / 52 dependencies |
| AuthService | MEDIUM | 3 direct / 34 dependencies; graph includes Java namesakes, so native scope confirmed from source |
| AuthNotifier | MEDIUM | 10 direct / 34 dependencies |
| Login screen state | LOW | Router import, 4 dependencies |
| AccountScreen | LOW | MainShell import, 4 dependencies |
| BiometricSessionStore | MEDIUM | 3 direct dependencies |
| LoginMethods screen state | LOW | 1 direct dependency |
| MainActivity, app root state | UNKNOWN | Framework lifecycle/manifest entry points, no caller edges resolved |

No Dart execution processes were enumerated by impact output. This is an index limitation, not evidence that authentication or downstream requests are unaffected. Authentication changes were treated as sensitive; API consumers and UI session behavior were checked below.

## Automated checks — 36 passed

- 6 vault/session tests: enrollment requires OS read approval; ordinary persisted tokens removed; new process locked; cancellation cannot expose tokens; unsupported hardware; protected rotation, lock, clear and disable migration.
- 8 service tests: question API bodies, no persisted answer, recovery clearing, canceled biometric means zero HTTP, server refresh and identity required, network failure stays locked, revoked session removes enrollment, full logout revokes/clears.
- 5 failure tests: wrong returned account revoked/cleared; canceled enrollment cleans newly written vault while retaining original session; offline logout removes local credentials; UI returns to unauthenticated despite a network logout failure; canceled sensors map to a retryable message without platform details.
- 4 screen tests: device label/unconfigured login disabled, question picker at 360x640, unavailable hardware fallback, native recovery and confirmation without browser UI.
- 2 lifecycle tests: inactive OS biometric prompt hides content without locking; background/resume clears in-memory session while preserving OS-gated vault.
- 3 real local HTTP adapter tests: parallel 401 resource requests share one rotation; wrong question and wrong settings password submitted once with no automatic retry. Run with `--dart-define=API_BASE_URL=http://127.0.0.1:19843/api` for the loopback fixture only.
- 8 existing regressions: 3 DPoP signature/binding/origin tests, 2 Identity contract tests, 3 API message tests.
- Targeted `flutter analyze` of 16 source/test files: no issues. `git diff --check` passed in root and feature worktrees.

## Live API audit

Used a new synthetic audit account and random private answer in memory; no existing demo question/password changed and no queue/database reset. Requests used Dart-generated ES256 DPoP, HTTPS via the mDNS bridge, and verified CA.

1. Register synthetic account: 201; password login: 200.
2. Read methods/catalog: 200; password-confirmed question save: 200.
3. Logout: 200; incorrect answer: 401; correct answer: 200.
4. Matching PATIENT identity: 200; refresh: 200.
5. Question password reset: 200; previous access rejected: 401.
6. New password login and identity: 200; final logout: 200.

The synthetic user row remains as audit data; its random password/answer were not printed or saved. Issued usable session was revoked. This validates server integration from Mac, not physical biometric acceptance.

## iPhone artifact

- CareFlow 1.0.0 (4), Release arm64, iOS minimum 15, signed with existing Personal Team. Runtime/signing files and public CA asset remain outside Git.
- Both preparatory and final xcodebuild exited 0. Strict/deep codesign verification passed; bundle version 4 and NSFaceIDUsageDescription verified. Generated native plugin registrant includes BiometricStoragePlugin and LocalAuthPlugin.
- `devicectl` installed and launched on connected iPhone 11 Pro Max iOS 18.1.1 successfully. HTTPS bridge health 200 UP.
- The user confirmed previous Release 3 password login/connectivity works. Release 4 Face ID enrollment/unlock, question UI and sensor behavior remain pending direct user confirmation; do not claim native E2E from Mac/widget tests.

## Remaining device coverage

Android debug APK compilation passed with Java 17 after configuring FragmentActivity, AppCompat themes, USE_BIOMETRIC and INTERNET permission and the Gradle compatibility adjustments below. APK: `frontend/patient-mobile/build/app/outputs/flutter-apk/app-debug.apk` (compile fixture, not a deployed Android release). Android fingerprint/face hardware and iPhone Touch ID have not been tested on physical devices.

## Primary implementation references

- Flutter local_auth: https://pub.dev/packages/local_auth — capability and enrolled modality discovery.
- biometric_storage: https://pub.dev/packages/biometric_storage and installed 5.0.1 plugin source — authenticated Keychain/Keystore read/write, biometric-only flags, enrollment invalidation.
- Apple Keychain biometryCurrentSet: https://developer.apple.com/documentation/security/secaccesscontrolcreateflags/biometrycurrentset — current enrollment binding.

- Apple native passkey association requirements: https://developer.apple.com/documentation/authenticationservices/connecting-to-a-service-with-passkeys — used to distinguish the shipped device-session unlock from native WebAuthn registration.

## Android build compatibility work

- Initial build required Gradle 9.1.0 and NDK 28.2.13676358 downloads, then failed because biometric_storage's JVM toolchain requires Java 17; only Java 25 was initially installed.
- Installed Homebrew openjdk@17 without linking it as system default; the build process receives JAVA_HOME explicitly. Installed SDK platforms 31/34/35 as requested by existing plugins.
- With Java 17, manifest validation found duplicate Espresso 3.2.0 namespaces pulled by Flutter integration_test. Added narrow app dependency constraints for espresso-core and espresso-idling-resource 3.6.1; no blanket manifest-validation suppression or plugin cache patch.
- Primary AndroidX version reference: https://developer.android.com/jetpack/androidx/releases/test#espresso-3.6.1
- The plugin itself pins compileSdk 31, below its AndroidX AAR requirements. Added a narrowly scoped root Gradle callback to compile only biometric_storage against SDK 36, matching the app; no minimum/target SDK change or metadata validation bypass.
- Final Java 17 build: exit 0 in 8.6 seconds; `app-debug.apk` produced. Native Android Kotlin/Java compilation and manifest/AAR checks passed. These Android-only changes do not require rebuilding the already installed iPhone artifact.
