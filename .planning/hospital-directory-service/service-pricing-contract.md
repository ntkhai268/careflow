# MOB-02 — Service pricing contract, 19/10/2026

## Ownership and model

Hospital Directory owns immutable price revisions; Appointment owns the quote accepted on a booking. Integer VND only, 0–1,000,000,000,000; duration 1–1440 minutes. A quote is not a paid invoice or transaction. Prices are global service offerings in this release, not department-specific tariffs.

`ServicePriceResponse`: `code`, `name`, `description`, `amount`, `currency`, `version`, `effectiveAt`, `active`, `estimatedMinutes`.

## Endpoints

| Endpoint | Principal | Result |
|---|---|---|
| GET `/api/directory/services` | Authenticated user or scoped service caller | Latest effective revision per code, then active filter |
| GET `/api/directory/services/{code}` | Same | Current effective active quote; 404 if absent, 409 if inactive |
| GET `/api/directory/services/admin` | ADMIN | Most recently published revision per code, including scheduled/inactive |
| POST `/api/directory/services` | ADMIN with verified actor ID | Append revision, HTTP 201; never rewrite historical price rows |

Publish body: `code`, `name`, optional `description`, integer `amount`, `estimatedMinutes`, `active`, optional `effectiveAt`, `expectedVersion`. Initial expectedVersion is 0. The server serializes writes by code with a transaction-scoped advisory lock; stale expectedVersion returns 409. effectiveAt defaults to server current time; deliberately backdated publication is refused. A newer effective revision supersedes earlier revisions of the same code; versions are not independent scheduled jobs. Server/API fail closed on missing/unavailable current quotes.

## Appointment integration

Create accepts optional `serviceCode`, `priceVersion`. Omitted serviceCode selects the server default `GENERAL_CONSULTATION` for compatibility. The server always obtains a valid current quote from Directory and stores the snapshot; no local amount fallback. If provided, priceVersion must equal the fetched version. Client amounts/names do not define the stored quote. HTTP 409 requires reloading/reconfirming the offer. Idempotent retries retain the original stored quote; changing code/version under an already-used key is rejected.

Response adds nullable `serviceCode`, `serviceName`, `priceVersion`, `quotedAmount`, `quotedCurrency`, `priceEffectiveAt`. NULL on old records means no captured quote, not zero charge. Status updates/cancellation do not mutate snapshots. Existing capacity/owner/assignment rules remain in effect.

Directory Flyway V1 creates `medical_service_prices`, baseline version 0 for existing Directory master tables. Appointment V6 adds nullable snapshot columns. Keep additive schema during rollback; do not drop historical quotes. Production baseline/migration approval and backup remain part of the later deployment gate.

## Demo and verification

Demo/test DataInitializer seeds `GENERAL_CONSULTATION` once at revision 1, 150000 VND. No catalog is automatically manufactured on production startup. Local current runtime initialization uses the authorized ADMIN API with idempotent presence checks.

PostgreSQL tests cover immutable history, effective time, inactive filtering, stale writes and concurrent publish. Appointment tests cover unavailable/inactive/stale quotes, immutable snapshots and retry payload checks. API/UI evidence is in [phase 1 verification](../frontend/phase1-verification-2026-10-19.md); usage instructions are in `huong-dan-su-dung/14-tep-thiet-bi-va-bang-gia.md`.
