# Project Rules - CareFlow

## Git & Work Branch Strategy
- **Prescription Feat Scope:** Only code and edit features related to Prescription on the `feature/vi/prescription-service` branch. 
- **Isolation of Features:** Keep consultation-service and prescription-service cleanly implemented. Do not implement Lab Orders or EMR logic directly on this branch. Mock external dependencies when necessary, and document them clearly.
- **Commit discipline:** Ensure focused commits so that changes can be easily cherry-picked or reverted without affecting other services.

## UI/UX & Design System Constraints
- **Absolute Rounding Rule:** All components must use `border-radius: 0px` (flat/square design system). No exceptions for buttons, cards, inputs, badges, or avatars.
- **No Decorative Icons:** Strictly follow the design system in `docs/design.md`. Do not add icons to stat cards or decorative elements.
- **No Emojis:** Do not use emojis in UI titles (e.g. no waving hands, warning icons unless strictly warning badge style).
- **Background Grid:** Apply decorative grid backgrounds (`bg-[linear-gradient(...)]`) to the main workspace and dark sidebar using SJD colors and correct grid size (`24px x 24px`).
- **Sidebar Hover State:** Use a darker purple background `#1E1322` for sidebar menu item hovers to maintain high readability of text and icons.

## GitNexus Integration
- Run `gitnexus analyze` whenever a new feature is completed or significant structural changes are made to keep repository index updated.
- Use `gitnexus context` and `gitnexus impact` to analyze blast radius and affected execution flows before writing new features.
