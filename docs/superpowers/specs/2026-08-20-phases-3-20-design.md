# Phases 3–20 — Personal Finance Assistant

**Date:** 2026-08-20
**Baseline:** Phase 1+2 (money in integer minor units, `TransactionType`, real `MIGRATION_5_6`, DB v6).
**Source of truth:** the original 20-phase brief (recovered from the previous session transcript).

## Ground rules kept from Phase 1+2

- No build/test environment here (no JVM, no emulator). Everything below is **written, not verified**.
  Build + Room schema generation + tests happen in Android Studio.
- No `Double` for money. Everything is `Long` minor units, formatted only via `money/MoneyFormat`.
- Local-first: no network code anywhere. The "AI assistant" (Phase 19) is a **local deterministic
  answerer** over the analytics engine — see below.
- Existing features must keep working: SMS receiver, notification listener, SQLCipher, budgets,
  recurring, CSV export.

## One migration, not fifteen

All remaining phases need schema. Fifteen hand-written, unbuildable migrations stacked on a v6
baseline whose Room identity hash was never reconciled is the biggest available foot-gun, so the
schema for *every* remaining phase is designed up front and lands as a **single `MIGRATION_6_7`**
(DB version 7). It is additive only — `ALTER TABLE ADD COLUMN` plus `CREATE TABLE`/`CREATE INDEX`,
no table rebuilds, so v6 data is preserved by construction.

### `expenses` (the transaction table)

The entity keeps the name `Expense` — it is the transaction table for all four
`TransactionType`s. Renaming it to `Transaction` would churn every file for zero user value.

Added columns (all with SQL defaults so old rows stay valid):

| column | type | default | why |
|---|---|---|---|
| `merchantId` | INTEGER? | null | link to `merchants` (Phase 4) |
| `accountId` | INTEGER? | null | link to `accounts` (Phase 5); optional by design |
| `toAccountId` | INTEGER? | null | destination of a TRANSFER (Phase 5) |
| `source` | TEXT | `'MANUAL'` | MANUAL/SMS/NOTIFICATION/IMPORT/RECURRING |
| `note` | TEXT | `''` | user note (Phase 3) |
| `referenceId` | TEXT | `''` | bank reference extracted from the message (Phase 17 dedup) |
| `isVerified` | INTEGER | `0` | "mark verified" action (Phase 3) |
| `rawHash` | TEXT | `''` | stable hash of the raw message — dedup key (Phase 17) |
| `installmentId` | INTEGER? | null | this row is one installment payment (Phase 14) |
| `createdAt` | INTEGER | `0` | audit |
| `updatedAt` | INTEGER | `0` | audit |

Backfill in the migration: `createdAt`/`updatedAt` = `timestamp`, and `source` is inferred
(`bankName = 'يدوي'` → MANUAL, `rawBody LIKE 'Recurring:%'` → RECURRING, else SMS).

Indices (search, filters and month aggregation are the hot paths): `timestamp`, `type`,
`categoryName`, `merchant`, `accountId`, `merchantId`, `referenceId`, `rawHash`.

`description` from the brief is **not** added — the existing `merchant` field already carries the
human-readable label, and `note` covers free text. Adding a third string would just be a third
place to look.

### New tables

- `accounts` — id, name, type (BANK/CASH/WALLET/CREDIT_CARD/OTHER), currency, isActive, timestamps.
- `merchants` — id, name, normalizedName (unique), categoryName, icon, timestamps.
- `merchant_rules` — id, pattern, categoryName, priority, isEnabled, createdAt. This is the
  merchant-learning store *and* the Phase 5 rule screen's backing table (one table, two features).
- `installments` — id, title, totalMinor, installmentMinor, count, paidCount, startDate,
  nextDueDate, merchant, categoryName, accountId, isActive, createdAt.

### Extended tables

- `recurring_expenses` gains `name`, `frequency`, `intervalDays`, `nextDueDate`, `isActive`,
  `accountId`, `isSubscription`, `reminderDaysBefore`. **Subscriptions are recurring payments with
  `isSubscription = 1`** — same table, same scheduler, one flag. `dayOfMonth`/`lastAddedMonth` are
  kept so the existing monthly logic keeps working.
- `budgets` is unchanged. The overall monthly budget (Phase 7) is a row with the sentinel key
  `__OVERALL__`, so no schema change and every budget query keeps working.

### Not tables

- Dismissed insights → SharedPreferences (a set of insight keys). A table for dismissals is
  bookkeeping without a query.
- App-lock PIN → SharedPreferences, PBKDF2-SHA256 hash + random salt, never the raw PIN.

## Layering (Phase 20)

The brief asks for UI → ViewModel → UseCase → Repository → DAO → Room. The project already has
UI → ViewModel → Repository → DAO → Room; the missing "use case" layer is added as
**pure-Kotlin functions in `domain/`** with no Android imports, which is what makes financial logic
testable on the JVM. That is the equivalent pattern the brief explicitly allows.

`domain/` (all deterministic, all unit-testable without a device):

| file | phase | what |
|---|---|---|
| `MoneyMath.kt` | 6, 7 | net cash flow: income − expenses + refunds, transfers excluded |
| `BudgetStatus.kt` | 7 | SAFE / WARNING(≥80%) / EXCEEDED(≥100%) + percent used |
| `Forecast.kt` | 10 | daily average, projected month spend, projected budget overrun |
| `MonthComparison.kt` | 9 | per-category change, biggest increase/decrease, no-previous-month case |
| `Insights.kt` | 11 | deterministic insight list from aggregates only |
| `Anomaly.kt` | 11 | "much higher than usual" with configurable threshold + minimum sample count |
| `Categorizer.kt` | 5 | merchant rule → SMS rule → keyword → default, never overwrites a manual pick |
| `MerchantNormalizer.kt` | 4 | normalized merchant key (case, punctuation, Arabic diacritics, digits) |
| `Recurrence.kt` | 12, 13 | next due date per frequency |
| `Assistant.kt` | 19 | maps a question to an already-computed analytics answer |

## Phase-by-phase decisions

- **Phase 3 (search/filters/sorting):** one `@RawQuery` (`ExpenseDao.searchRaw`) built by
  `TransactionQuery.build()` from a filter object. Room can't parameterise `ORDER BY`, and eight
  near-identical `@Query`s is worse than one query builder with bound arguments and a whitelisted
  sort enum. Results are capped (`LIMIT`) so 100k transactions don't land in memory.
- **Phase 4/5 (merchants + rules):** learning is explicit — changing a transaction's category
  offers "apply to all from this merchant", which writes a `merchant_rules` row. Nothing
  auto-recategorises silently, and a manually set category is never overwritten.
- **Phase 5 (income) / transfers / refunds:** all expense aggregation now filters
  `type = 'EXPENSE'`; refunds subtract; transfers are excluded from both spending and net flow.
- **Phase 14 (installments):** the purchase total is **not** inserted as a transaction. Only the
  monthly payment rows are, each tagged with `installmentId`. That is the no-double-counting rule.
- **Phase 16 (security):** `androidx.biometric` is the one new dependency — biometric auth cannot
  be hand-rolled. PIN fallback uses `javax.crypto` PBKDF2 from the platform.
- **Phase 18 (export):** CSV gains Account/Source/Reference/Note columns and **omits raw message
  text unless the user ticks "include raw text"**. PDF uses the platform's
  `android.graphics.pdf.PdfDocument` — no library.
- **Phase 19 (AI):** no network, no API key, no model. `Assistant.kt` matches a question against
  intents (spend by category / month, top merchants, comparison, affordability, forecast) and
  renders the answer from numbers the analytics engine already computed. The brief's hard rule —
  "the AI must not calculate financial numbers" — is satisfied structurally: the answerer can only
  read a precomputed `FinancialContext`. Labelled in the UI as a local assistant, not AI magic.

## Verification checklist (Android Studio — required before "done")

1. Gradle sync + build; `app/schemas/7.json` generated.
2. Reconcile `MIGRATION_6_7` against the generated v7 schema (column order is irrelevant, but
   types, NOT NULL, defaults and **index names** must match, or Room throws on first open).
3. Upgrade test: install a v6 build with data → install this build → all rows intact, `source`
   backfilled, aggregates unchanged.
4. `./gradlew test` — money, forecast, comparison, budget status, categorizer, anomaly, backup
   round-trip, dedup.
5. Smoke: add expense/income/transfer/refund, capture an SMS, backup → wipe → restore, lock with
   PIN and biometric, export CSV + PDF.

## Second pass — gaps closed after the first sweep

The first pass left five phases partial. All five are now finished:

- **Phase 7** — budget notifications gained a third level: *forecast*. If the current daily average
  projects past the limit and at least 3 days remain, a proactive alert fires once per month per
  budget, alongside the existing 80% / 100% levels.
- **Phase 11** — the anomaly multiplier (2× / 3× / 5× / 8×) is now user-settable in Settings, next
  to a plain-language explanation of the rule.
- **Phase 13** — reminders no longer need the app open. `PaymentReminderScheduler` sets a daily
  inexact `AlarmManager` alarm; `PaymentReminderReceiver` checks due payments against each item's
  `reminderDaysBefore`, notifies once per due date, and re-arms itself after boot
  (`RECEIVE_BOOT_COMPLETED`). AlarmManager, not WorkManager — one daily local query does not
  justify a dependency.
- **Phase 15** — the calendar now shows income per day (green) and marks days that carry a due
  recurring/subscription/installment payment.
- **Phase 17** — the parser source abstraction exists: `sources/TransactionSources.kt` defines each
  bank/wallet once (bank name, app packages, sender patterns, optional per-source amount/merchant
  patterns, default type). `SmsParser` resolves the bank from it, and the notification listener's
  package allow-list is derived from it, so adding a bank is one row in one file.
- **Transaction editing** — amount and date/time are now editable from the detail screen (a capture
  with a misread amount was previously only deletable).
- **Phase 20 / performance** — the home ledger tree no longer loads transactions to sum them. Month
  and month+bank totals come from SQL aggregations, and a group's transactions are queried only
  while that group is expanded.

## Design system

Rebuilt as a real system rather than per-screen colors:

- `theme/Color.kt` — emerald brand, warm sand light surfaces, slate dark surfaces, plus **semantic**
  colors (income / expense / transfer / warning) and two chart palettes (light + dark).
- `theme/Theme.kt` — full Material 3 light and dark schemes, no dynamic color (income green must
  stay green regardless of wallpaper), status-bar icons flip with the theme, and a
  `LocalFinanceColors` composition local so no screen hardcodes a hex value again. Every
  `Color(0xFF…)` outside the theme package is gone.
- `theme/Type.kt` — one type scale on the system font (so Arabic renders natively, no font files);
  amounts use the heaviest display styles because the amount is the point of every financial screen.
- `theme/Shape.kt` — 8/12/16/22/28dp corners in one place.
- Window themes for day and night with a matching `windowBackground` (no white flash on launch) and
  transparent system bars.

### Logo and icons

New mark: three ascending bars with a rising trend line and an end dot — "tracking and analysis",
not just "a wallet". Adaptive icon with a gradient vector background, plus a **monochrome layer** so
Android 13+ themed icons work. The notification glyph is the same mark simplified to 24dp as a white
mask. All vectors — no raster assets, no icon library.

## Known gaps (deliberate)

- Not built, not run — see above. No claim of verification.
- Multi-currency stays schema-only; no FX conversion.
- Room Paging is not used; list queries are capped and all analytics are SQL aggregates.
- Cloud backup is out of scope (backup writes to a user-chosen file via the system picker).
- v1–v4 databases still fall back to a destructive migration — their schemas do not exist in any
  form, so a data-preserving path cannot be written. v5+ is preserved.
- `categoryId` and a separate `description` field were not added; categories stay name-keyed and
  `merchant` + `note` cover the text.
- Reminder alarms are inexact (battery-friendly); a reminder can arrive later in the day than 9am.
