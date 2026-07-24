<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **careflow** (1589 symbols, 3160 relationships, 89 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

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

## DB Schema & Planning Rules
- **Đồng bộ DBML**: Mỗi khi lập trình xong một tính năng có bổ sung/chỉnh sửa DB Schema (JPA Entity, Flyway Migration, bảng/cột mới), **BẮT BUỘC** phải cập nhật lại sơ đồ DBML tương ứng tại thư mục `.planning/<service_name>/<service_name>-db.dbml`.
- **Thư mục Planning**: Tất cả tài liệu thiết kế, logic nghiệp vụ, và file `.dbml` phải được quản lý tập trung trong thư mục `.planning/` ở gốc dự án phân theo từng service:
  - `.planning/emr-service/`
  - `.planning/consultation-service/`
  - `.planning/prescription-service/`
  - `.planning/identity-service/`
- **Remote DB Default**: Mọi cấu hình kết nối DB phải ưu tiên chạy với Remote Server (`100.116.233.60:5432`). File `application-remote.yml` và `.env` không được commit lên Git.
