<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **careflow** (5272 symbols, 12233 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/careflow/context` | Codebase overview, check index freshness |
| `gitnexus://repo/careflow/clusters` | All functional areas |
| `gitnexus://repo/careflow/processes` | All execution flows |
| `gitnexus://repo/careflow/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->

# Project Rules - CareFlow

## Git & Work Branch Strategy
- **Prescription Feat Scope:** Only code and edit features related to Prescription on the `feature/vi/prescription-service` branch. 
- **Isolation of Features:** Keep consultation-service and prescription-service cleanly implemented. Do not implement Lab Orders or EMR logic directly on this branch. Mock external dependencies when necessary, and document them clearly.
- **Commit discipline:** Ensure focused commits so that changes can be easily cherry-picked or reverted without affecting other services.
- **Branch Scope & No Push Main**: Chỉ lập trình và push code lên nhánh cá nhân của service đó (`feature/...`), **TUYỆT ĐỐI KHÔNG PUSH LÊN NHÁNH `main`**.
- **Pull Main Before Coding**: Trước khi lập trình bất kỳ tính năng mới nào, **BẮT BUỘC** phải pull code mới nhất từ `main` về (`git pull origin main`) để tránh xung đột và code bị outdated.
- **English Commit Messages**: All git commit messages **MUST be written in English** (e.g. `feat: ...`, `fix: ...`).
- **No Unrequested Commits**: KHÔNG tự động thực hiện `git add` hay `git commit` trừ khi được người dùng chỉ định rõ ràng.

## DB Schema & Planning Rules
- **Đồng bộ DBML**: Mỗi khi lập trình xong một tính năng có bổ sung/chỉnh sửa DB Schema (JPA Entity, Flyway Migration, bảng/cột mới), **BẮT BUỘC** phải cập nhật lại sơ đồ DBML tương ứng tại thư mục `.planning/<service_name>/<service_name>-db.dbml`.
- **Thư mục Planning**: Tất cả tài liệu thiết kế, logic nghiệp vụ, và file `.dbml` phải được quản lý tập trung trong thư mục `.planning/` ở gốc dự án phân theo từng service:
  - `.planning/emr-service/`
  - `.planning/consultation-service/`
  - `.planning/prescription-service/`
  - `.planning/identity-service/`
- **Remote DB Default**: Mọi cấu hình kết nối DB phải ưu tiên chạy với Remote Server (`100.116.233.60:5432`). File `application-remote.yml` và `.env` không được commit lên Git.

## Automatic Data Seeding Strategy & Rules
- **Automatic DataInitializer Pattern**: Mọi microservice khi khởi chạy (`CommandLineRunner`) BẮT BUỘC phải tự động kiểm tra và seed dữ liệu mẫu hợp lệ (clean & ready-to-test data) cho ngày hiện tại (`LocalDate.now()`).
- **Quy tắc Xử lý Bản ghi đã tồn tại trong ngày**:
  - **Dữ liệu Hàng đợi & Lượt khám theo ngày (`QueueEntry`, `VisitTicket`)**: Thực hiện **Reset / Clear dữ liệu cũ (`deleteAll()`) và seed lại danh sách active mới cho ngày hiện tại** để sau mỗi lần khởi động lại hệ thống luôn có hàng đợi sạch sẵn sàng cho việc test.
  - **Dữ liệu Danh mục & Master Data (`Patient`, `User`, `Drug`, `Config`)**: Sử dụng **Idempotent Update**: Kiểm tra tồn tại (`existsById` / `findByCode`). Nếu chưa có thì `INSERT` mới; nếu đã có thì `UPDATE` đồng bộ lại nội dung chuẩn, không tạo trùng lặp ID và không làm crash ứng dụng.
- **Nguồn dữ liệu seed chuẩn**:
  - `careflow-patient-service`: Seed 6 bệnh nhân mẫu (`f0000001-0000-0000-0000-000000000001` đến `...0006`) đầy đủ tiền sử y tế, dị ứng thuốc và thẻ BHYT.
  - `careflow-queue-service`: Reset và seed danh sách lượt chờ khám active trong ngày cho `ROOM-01` (`NOI-001` đến `NOI-005`), lượt Cận lâm sàng (`LAB-HEMATOLOGY-01`), và lượt Quầy phát thuốc (`PHARMACY-MAIN-01`).
  - `careflow-prescription-service`: Seed danh mục thuốc mẫu (`drugs_dictionary`).
  - `careflow-identity-service`: Seed tài khoản nội bộ & bệnh nhân mẫu (`PATIENT`, `DOCTOR`, `STAFF`, `LAB_TECHNICIAN`, `ADMIN`).
- **Quy tắc khi tạo tính năng/Entity mới**: Mỗi khi phát triển tính năng hoặc entity mới có danh sách/hàng đợi, BẮT BUỘC phải bổ sung logic seed data tương ứng vào lớp `DataInitializer.java` của microservice đó để mỗi lần chạy lại hệ thống đều có dữ liệu test tức thì.

---

# Behavioral Guidelines (LLM Coding)

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
