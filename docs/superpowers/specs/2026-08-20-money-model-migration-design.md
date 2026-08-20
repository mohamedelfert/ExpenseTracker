# Phase 1+2 — Money Model & Migration Safety

**Date:** 2026-08-20
**Scope:** Foundation only. Replace `Double` money with integer minor units, introduce
`TransactionType`, and replace `fallbackToDestructiveMigration()` with a real, data-preserving
`5→6` migration. Everything in later phases builds on this.

## Constraints established

- **Build environment cannot compile/run here** (no JVM, no `gradlew` script, no emulator).
  All compilation, Room schema generation, and test execution happen in Android Studio.
  Nothing in this doc is "verified" until built there.
- **Pre-v5 installs may still exist**, and there are no exported schemas or git history for
  v1–v4, so those migrations cannot be written. Resolution:
  `fallbackToDestructiveMigrationFrom(1, 2, 3, 4)` + a real `MIGRATION_5_6`. v5 data is
  preserved; v1–v4 are reset (identical to today's behavior for them).
- Single currency (EGP default). Schema is multi-currency ready (currency stored per record)
  but no FX/conversion is built. YAGNI.

## Current state (as-is)

- `Expense(id, amount: Double, merchant, bankName, timestamp, rawBody, categoryName)`
- `Budget(categoryName PK, limitAmount: Double)`
- `RecurringExpense(id, amount: Double, merchant, bankName, categoryName, dayOfMonth, lastAddedMonth)`
- Aggregations `CategoryTotal(categoryName, total: Double)`, `SourceTotal(bankName, total: Double)`
- DB version 5, SQLCipher via `SupportOpenHelperFactory`, `fallbackToDestructiveMigration()`.
- `DatabaseEncryptionMigration.ensureEncrypted()` runs before Room opens; preserves
  `user_version` (so Room still fires `5→6` for an upgrading unencrypted v5 install — **verify**).
- Manual DI, KSP, Room 2.6.1, `exportSchema=false`.
- `NumberUtils.parseAmount(String): Double?`; `SmsParser` produces `Expense` with `Double`.
- `CsvExporter` columns: `ID,Date,Amount,Merchant,Bank,Category,RawText`.

## Target design

### Money value type
`data class Money(val amountMinor: Long, val currency: String = "EGP")`
- `plus`/`minus`: require equal `currency`, else `throw IllegalArgumentException`.
- No `Double` in arithmetic.

`object MoneyFormat`
- `format(amountMinor: Long, currency: String = "EGP"): String` → e.g. `"125.50 EGP"`.
- Integer math: `major = minor / 100`, `frac = abs(minor % 100)`, thousands-grouped major,
  `".%02d"` fraction. Negative amounts render with leading `-`.

### Storage (two columns per money field, not a TypeConverter)
Keeps `SUM()` and currency queryable.

- `Expense`: `amountMinor: Long`, `currency: String @ColumnInfo(defaultValue="EGP")`,
  `type: TransactionType @ColumnInfo(defaultValue="EXPENSE")`, + existing non-money fields.
- `Budget`: `limitMinor: Long` (single-currency; no per-budget currency column — YAGNI).
- `RecurringExpense`: `amountMinor: Long`.
- `CategoryTotal.total: Long`, `SourceTotal.total: Long`.

### TransactionType
`enum class TransactionType { EXPENSE, INCOME, TRANSFER, REFUND }` (all four defined now to
avoid a later migration just to add enum values). One Room `TypeConverter`
(`String` ↔ enum), registered via `@TypeConverters` on `AppDatabase`.

### Migration 5→6 (recreate-table, one migration, all three money tables)
`DROP COLUMN` is unreliable at minSdk 26, so each table is recreated:

```sql
-- expenses
CREATE TABLE expenses_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  amountMinor INTEGER NOT NULL,
  currency TEXT NOT NULL DEFAULT 'EGP',
  type TEXT NOT NULL DEFAULT 'EXPENSE',
  merchant TEXT NOT NULL,
  bankName TEXT NOT NULL,
  timestamp INTEGER NOT NULL,
  rawBody TEXT NOT NULL,
  categoryName TEXT NOT NULL DEFAULT 'عام'
);
INSERT INTO expenses_new (id, amountMinor, currency, type, merchant, bankName, timestamp, rawBody, categoryName)
  SELECT id, CAST(ROUND(amount * 100) AS INTEGER), 'EGP', 'EXPENSE',
         merchant, bankName, timestamp, rawBody, categoryName
  FROM expenses;
DROP TABLE expenses;
ALTER TABLE expenses_new RENAME TO expenses;
-- repeat analogously for budgets (limitAmount → limitMinor) and recurring_expenses (amount → amountMinor)
```

- `exportSchema=true` (configure Room schema dir). **Build once → Room generates the v6 JSON →
  reconcile the CREATE TABLE DDL above against the generated schema byte-for-byte.** The exact
  DDL (PK clause, `NOT NULL`, defaults) must match the entities or Room throws an identity-hash
  mismatch on first open. This is the single highest-risk step.
- `AppDatabase`: add `.addMigrations(MIGRATION_5_6)`, replace `fallbackToDestructiveMigration()`
  with `fallbackToDestructiveMigrationFrom(1, 2, 3, 4)`, bump `version = 6`.

### Parsing
- `NumberUtils.parseAmountMinor(input: String): Long?` — normalize digits (reuse
  `normalizeDigits`), split on decimal separator, `major*100 + fracPadded/truncated to 2`, no
  `Double`. `>2` fractional digits: truncate (documented). Remove/replace `parseAmount(Double)`
  and update callers.
- `SmsParser` amount extraction returns minor units; builds `Expense` with `amountMinor`,
  `currency="EGP"`, `type=EXPENSE`.

### Downstream updates
- DAOs: `SUM(amount)` → `SUM(amountMinor)`; return `Long`; aggregation rows `Long`.
- `MainViewModel.monthTotal: StateFlow<Long>`; all money display via `MoneyFormat`.
- UI screens (`HomeScreen`, `DashboardScreen`, `AddExpenseScreen`, `RecurringExpensesScreen`):
  read/format minor units; input parsed via `parseAmountMinor`.
- `BudgetAlertChecker`: compare `Long` spent vs `Long` limit; ratio `spent.toDouble()/limit`
  used for the **percentage display only** (not money).
- `CsvExporter`: `Amount` formatted as decimal string via `MoneyFormat`; add `Currency` and
  `Type` columns. `RawText` behavior unchanged (redaction is Phase 18).

### Deferred on purpose (not in this phase)
- Accounts, transfers, refunds, income — logic and UI.
- `WHERE type='EXPENSE'` aggregation semantics (all rows are EXPENSE today; forward-looking
  filter semantics belong to Phase 5/6 with their own tests).
- CSV raw-text redaction (Phase 18).

## Testing (JVM/unit — runs in Android Studio, not here)
- `MoneyTest`: `12550 + 10025 == 22575`; `minus`; currency-mismatch throws; `format` cases
  (incl. `<1.00`, negative, thousands grouping).
- `NumberUtilsTest`: `"125.50"→12550`, `"125.5"→12550`, `"125"→12500`, `"1,250.75"→125075`,
  `>2 decimals` truncation, Arabic/Persian digits, garbage→`null`.
- `Migration5to6Test` (Robolectric, plain in-memory SQLite, no SQLCipher — the conversion SQL
  is encryption-agnostic): seed a v5 row `amount=125.5`, run `MIGRATION_5_6`, assert
  `amountMinor==12550`, `type=='EXPENSE'`, `currency=='EGP'`, row count preserved. Best-effort:
  depends on adding Robolectric to the test config.

## Verification checklist (Android Studio — required before "done")
1. Gradle sync + build succeeds (`exportSchema=true` produces `app/schemas/...6.json`).
2. Reconcile `MIGRATION_5_6` DDL against generated v6 JSON; fix any DDL mismatch.
3. Confirm `DatabaseEncryptionMigration` leaves `user_version=5` so Room fires `5→6` on an
   upgrading unencrypted install.
4. Manual upgrade test: install prior v5 build with data → install this build → data intact,
   amounts correct (spot-check a known amount → minor units).
5. Run unit tests (`Money`, `NumberUtils`, migration) — all green.
6. Smoke test: add manual expense, SMS/notification capture, CSV export open in a spreadsheet.

## Notes
- Repo is not a git repository, so this spec is written but not committed.
