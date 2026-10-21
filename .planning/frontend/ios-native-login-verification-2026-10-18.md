# Native iOS login verification — 2026-10-18

## Scope

- Source: `feature/dangkhoii/patient-mobile-ios-login`, based on `develop@e5fc9c1`; `git pull --ff-only origin main` completed before coding, already up to date.
- Native build copy: `~/Library/Developer/CareFlow/iphone11pm-20261018/app`, version `1.0.0+2`.
- Flutter 3.41.6 / Dart 3.11.4, Xcode 27.1, iPhone 11 Pro Max / iOS 18.1.1.
- Local audit environment only: HTTPS bridge on the Mac forwards to the existing HTTPS Gateway; no backend code, schema, database reset or credential changes.

## Failure and correction

The installed native app had no DPoP request signing, whereas the integrated Identity runtime binds sessions to an ES256 client key. A direct login without proof returns HTTP 401 with a DPoP validation message. The iPhone also had no configuration profile for the private local CA.

`ApiService` now signs normal and refresh requests using a per-Gateway key persisted in secure storage. Proofs contain the public P-256 JWK, unique request ID, method, canonical URL without query/fragment, issuance time and access-token hash for authenticated requests. Requests to other API origins are rejected before signing. The canonical proof origin can be set with `DPOP_ORIGIN` for the existing reverse proxy deployment.

An optional `LOCAL_TEST_CA_ASSET` configures the native Dio adapters with a bundled public CA while retaining certificate verification and normal trusted roots. A DER asset was rejected by this device's Dart TLS implementation with `BAD_PKCS12_DATA`; the build uses a PEM certificate asset. The CA and machine-specific build/test configuration are present only in the external build copy. Private CA keys and session credentials are not part of the source changes. Transport initialization is lazy so configuration errors reach the request error handler instead of an unhandled startup future.

GitNexus upstream impact on `ApiService`: MEDIUM, 10 direct importers, 52 dependencies through three levels. No Dart processes were enumerated, so graph output alone was not treated as complete runtime coverage.

## Checks

| Check | Result |
|---|---|
| DPoP signature/public-only JWK, token binding, URL normalization and origin rejection | 3 tests passed |
| Existing authentication response/error mapping regression tests | 5 tests passed |
| Static analysis of changed Dart files and tests | No issues |
| Real HTTPS login, `/auth/me`, patient profiles, refresh, second `/auth/me`, logout using the new Dart signer | All six requests HTTP 200 |
| Native iPhone HTTPS/Keychain/session/refresh/profile test | Not accepted: Profile runner reached an HTTP connection error; local network permission/Wi-Fi reachability still need confirmation. A visible health probe was added for the next run. |
| Final signed Release installation and launch | Release 1.0.0 (2) installed and launched successfully through devicectl; bundled public CA checksum and deep code signature verified |

The live checks obtain demo credentials from the canonical account guide through a private test config outside Git, print only safe outcomes and revoke their diagnostic session. The physical test intentionally replaces an access token with an invalid test value to exercise automatic refresh against the same stored signing key.

The first physical runner attempt stopped before executing the test because Flutter's cached `iproxy` was Intel-only and Rosetta was unavailable. An ARM64 `iproxy` was installed from Homebrew `libusbmuxd`; the original cached binary was backed up outside Git and Flutter's cache entry points to the native tool. Debug runner discovery was also unreliable with this Xcode, so a prebuilt Profile bundle was used with `flutter drive`.

The driver uninstalls its application on cleanup by default, which can remove the device's developer-trust state. Further physical runs should use `--keep-app-running`, followed by a direct devicectl update to the final Release bundle. The Release bundle was restored after testing. Native login is not reported as passing until the device's networking step is confirmed and the test completes.

Xcode 27 places the app at `build/ios/Release-iphoneos/Runner.app` while this Flutter version sometimes expects `build/ios/iphoneos/Runner.app`. The final bundle was built through config-only Flutter preparation plus xcodebuild, then installed/launched using devicectl. No backend setting was relaxed to make this work.

## Limits

This verifies native authentication and HTTP profile access against the current audit runtime. It does not establish full appointment/consultation/check-in/GPS/FCM coverage or synchronize the browser emulator's independent UI/business fixes. Native STOMP DPoP integration is outside this login change.

User steps and retry instructions belong only in [the iPhone usage guide](../../huong-dan-su-dung/11-cai-app-iphone.md); demo credentials belong only in [the canonical account guide](../../huong-dan-su-dung/01-tai-khoan-va-du-lieu.md).


## Release 3 — restart after Wi-Fi IP change

- Found Mac Wi-Fi IP had changed from 192.168.1.9 to 192.168.1.10 while Release 2 and bridge retained the old IP.
- Runtime config now uses https://MacBook-Pro-cua-DangKhoii.local:18443/api and matching WSS endpoint; existing certificate already includes this DNS SAN. Bridge restarted with dual-stack listener. Source symbols were not changed.
- Hostname health check: HTTP 200 UP with CA validation. Dart DPoP login/me/profiles/refresh/me/logout: all six HTTP 200. Diagnostic session revoked.
- Signed Release 1.0.0 (3), codesign deep/strict validation passed; devicectl installation and launch on the connected iPhone 11 Pro Max succeeded.
- Physical-device login remains pending user confirmation; Mac checks do not establish native E2E acceptance.
