# Auth UI review — 17/10/2026

Source: `.worktrees/security-integration`, branch `feature/dangkhoii/security-integration`, HEAD `5a8c6e5` with uncommitted integration changes. This report covers the UI refinement following the real E2E audit. User actions are documented only in [the canonical login guide](../../huong-dan-su-dung/02-dang-nhap-mfa.md).

## References and design decisions

Reviewed Google's [passkey interface examples](https://developers.google.com/identity/passkeys/ux/user-interface-design) and [user journeys](https://developers.google.com/identity/passkeys/ux/user-journeys), including the published Google Accounts introduction and Trailblazer account settings screenshots. Also consulted [FIDO design guidance](https://fidoalliance.org/design-guidelines/).

Applied the reference principles to CareFlow: recognizable person/key icons, short explanations before the device prompt, method cards, real enrollment status/date metadata and an explicit completion state. The layouts and icons are implemented as Flutter widgets/SVG/HTML/CSS; reference screenshots are not shipped as product assets. Mobile retains the existing teal design tokens; Hospital Web uses purple with restrained borders, spacing and a responsive two-column auth shell.

Choosing a method precedes the form. Secret-question selection uses a dismissible bottom sheet on mobile and a custom keyboard-accessible combobox on web. Back/change-method clears secret inputs. API failures remain visible; mobile scrolls to the error notice. Password recovery has its own success page and login action. The passkey removal confirmation requests the current password inside the dialog; no browser window.confirm UI.

## Verification

| Check | Observed result |
|---|---|
| Web question login | Real API 200 and ADMIN landing page |
| Mobile question login | Real API 200 and account page |
| Enrollment reauthentication | Wrong current password 401 on Web, visible error; correct password saves 200 |
| Save question on both clients | Real API 200; settings refresh and success notice |
| Password confirmation | Web mismatch blocked locally; mobile required fields validated inline |
| Password recovery on both clients | Real API 200, dedicated success page; public demo passwords restored through the real recovery API |
| Web combobox | Three existing choices; outside mousedown dismisses; ArrowDown opens/focuses options, Escape closes |
| Responsive Web | 360px viewport: document width equals viewport, no horizontal overflow; passkey/question screenshots reviewed |
| Mobile accessibility layout | Widget tests at 360px, text scale 100%/200%, including the scrollable question sheet, required fields and back navigation |
| Fresh build | New Flutter document loaded after per-phone session-preserving reload; passkey form and real rejection path checked |
| API/DB | Existing Gateway/Identity/PostgreSQL, no response interception or authenticator emulator |

Flutter full suite: **186 passed, one skipped** on the final UI source. Analyze has no errors/warnings; one pre-existing info remains in queue_ticket_eventual_test.dart:14. Web production build/TypeScript and Flutter Web build pass. Runtime health: 14/14 OK. Browser auth trace records only method/path/status in [browser-http.json](auth-ui-2026-10-17/browser-http.json); request bodies, credentials and tokens are excluded.

The UI change touches LoginMethodsForm, shared AuthShell, Web LoginPage and the two emulator auth screens, plus the layout tests and canonical guide/changelog. GitNexus upstream checks: mobile auth actions/fields LOW with two direct UI callers; entry functions with unresolved UI dispatch remain UNKNOWN rather than being treated as unused. Their three Web routes and mobile router entries were inspected manually. Full-worktree detect-changes also includes the pre-existing security/E2E changes and reports CRITICAL; no staging, commit or push was performed.

Positive Touch ID/Face ID hardware enrollment/assertion still requires actual user verification and has not been claimed by this UI review. Screen-reader device testing and every browser/OS combination are not covered by the narrow-screen/widget checks.

## Reviewed screenshots

- [Mobile login](auth-ui-2026-10-17/mobile-login.png), [method chooser](auth-ui-2026-10-17/mobile-methods.png), [passkey](auth-ui-2026-10-17/mobile-passkey.png).
- [Mobile saved settings](auth-ui-2026-10-17/mobile-settings-saved.png), [recovery success](auth-ui-2026-10-17/mobile-recovery-success.png).
- [Web login](auth-ui-2026-10-17/web-login.png), [method chooser](auth-ui-2026-10-17/web-methods.png), [saved settings](auth-ui-2026-10-17/web-settings-saved.png), [recovery success](auth-ui-2026-10-17/web-recovery-success.png).
- [Web at 360px: passkey](auth-ui-2026-10-17/web-passkey-360.png), [question](auth-ui-2026-10-17/web-question-360.png).

## User correction — preserve Web login

The user requested the original Hospital Web login design be preserved. `/login` now restores its original purple hospital banner, modular logo, two-column layout, input styling and primary button. Only two subtle support links are added below the form. The previous Web login screenshot above is historical; current evidence: [restored Web login](auth-ui-2026-10-17/web-login-restored.png). Mobile and the dedicated method/recovery/settings pages retain the previous refinement.

Correction verification: production build passes; original banner/form visible, both support links reach their routes, 390px has no horizontal overflow and real STAFF password sign-in returns 200. [Checks](auth-ui-2026-10-17/web-login-restored-checks.json).

## User correction — share the original Web frame

`/login`, `/login/methods`, `/forgot-password` and `/account/security` now share `HospitalAuthLayout` and the original logo. The original hospital banner content, purple gradient, typography and artwork remain intact. Child screens use the same background, form width, input tokens and primary-button colors as the login page. The banner stays at half the viewport width and sticks to the viewport during long-form scrolling; the scrollbar no longer changes its width. Mobile Emulator is unchanged by this correction.

Production build and TypeScript pass. Browser checks exercise seven views (login, method chooser, passkey form, question form, recovery chooser, recovery question form, authenticated settings), navigation/back, question-picker outside dismissal, desktop scrolling at 800px height and 390px viewport layouts without horizontal overflow. Real STAFF password authentication returns HTTP 200 and opens the check-in page before authenticated security settings are checked. No API mocking or interception is used. The original login banner matches its prior screenshot exactly; banner comparisons across routes differ by at most 1/255 per color channel from browser raster rounding, with identical dimensions. No browser JavaScript errors were observed.

Current screenshots: [Login](auth-ui-2026-10-17/web-auth-shared-login.png), [methods](auth-ui-2026-10-17/web-auth-shared-methods.png), [recovery](auth-ui-2026-10-17/web-auth-shared-recovery.png), [recovery form](auth-ui-2026-10-17/web-auth-shared-recovery-question.png), [settings](auth-ui-2026-10-17/web-auth-shared-settings.png). [Checks](auth-ui-2026-10-17/shared-frame-checks.json).

GitNexus upstream checks for the edited entry components, shared layout and styling constants resolve no JSX callers and report UNKNOWN. Text inspection confirms two direct layout consumers and the three routes using LoginMethodsForm; no backend symbols change in this correction. Source/runtime remain in the security-integration worktree and are uncommitted. The prior child-page screenshots are historical; the shared-frame screenshots above describe the current runtime.
