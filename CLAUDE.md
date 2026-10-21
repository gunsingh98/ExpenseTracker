# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build, run, test

**Java 25 is required at runtime.** The Gradle toolchain compiles against Java 25
(class file version 69), so the jar will not run under an older JVM — the common
trap is a system `java` on the PATH being Java 17, which fails with
`UnsupportedClassVersionError`. Point `JAVA_HOME` at a JDK 25 build before running
the jar directly:

```bash
export JAVA_HOME="/c/GunS/Setup/zulu25.28.85-ca-jdk25.0.0-win_x64"   # a JDK 25
```

Gradle itself finds a 25 toolchain automatically, so `./gradlew` tasks need no setup.

```bash
./gradlew test                                              # full test suite
./gradlew test --tests "com.expensetracker.data.ExpenseDaoTest"          # one class
./gradlew test --tests "com.expensetracker.*.ExpenseDaoTest.insertAssignsIdAndRoundTrips"  # one method
./gradlew bootJar                                           # jar WITHOUT the SPA (fast; API only)
./gradlew build -PwithFrontend                              # jar WITH the SPA bundled in
```

The SPA is only compiled and bundled into the jar when `-PwithFrontend` is set
(it drives `npm run build` and copies `frontend/dist` into the jar's static
resources) — plain `test`/`bootJar` skip it to stay fast. There is no linter task.

Run locally (DB auto-created empty on first start; dev account `admin`/`admin`):

```bash
DATA_DB_PATH=./data/tracker.db "$JAVA_HOME/bin/java" -jar build/libs/expense-tracker-0.1.0.jar
```

Frontend hot-reload dev loop: `cd frontend && npm install && npm run dev`
(Vite on :5173, proxies `/api` to :8080) with the backend running separately.

## Architecture

A single Spring Boot service (Java 25, virtual threads) serves **both** the REST
API and the React/Vite SPA as static assets — one deployable. See `README.md` for
the full env-var table and deployment notes.

**One SQLite database is the single source of truth** (`app.data.db-path`,
default `data/tracker.db`), holding the five financial tables
(`expense`, `investment`, `income`, `balance`, `list_value`) **and** the
`audit_log`. There is no migration tool and no `schema.sql`: each DAO owns its
table and creates it idempotently in a `@PostConstruct initSchema()`
(`CREATE TABLE IF NOT EXISTS`). `DatabaseConfig` defines the one `DataSource`
(WAL mode) so Spring Boot's datasource auto-config backs off.

**Request layering:** `web/*Controller` → `service/*Service` → `data/*Dao`
(Spring `JdbcTemplate`, no ORM). Every mutating service method records to the
`AuditService` capturing the before/after rows — so write paths should go through
a service, not a DAO directly. The dashboard (`DashboardService`) computes all
KPIs/aggregates in Java over rows it reads; it does **not** rely on spreadsheet
formulas.

**Excel is primarily an export format, now with a one-time import.**
`WorkbookExportService` renders the DB into a bundled blank template
(`resources/templates/tracker-template.xlsx`, which keeps the Summary/Monthly
tabs, formulas and charts) for `GET /api/export` and the scheduled Google Drive
backup; it reads the DAOs directly, bypassing the service/audit layer.
`WorkbookImportService` is its read-side mirror (same sheet names, same column
layout, data from Excel row 5) for a one-off migration, triggered by the
`--import=<path>` CLI flag via `WorkbookImportRunner`. **The two must stay in
sync**: column indices are defined in `WorkbookExportService` and mirrored in
`WorkbookImportService`; the auto-formula columns (Month / Year / Gain-Loss /
Net-Worth) are written/read by neither — Excel and the Java dashboard recompute
them.

### Domain rules that aren't obvious from a single file

- **Fund value is stored once per fund.** An investment "fund" is the set of rows
  sharing a (trimmed) `name`. A fund's current market value lives on exactly one
  row — its most recent contribution — and is cleared on the others, so the
  net-worth total (which sums `current_value` across rows) never double-counts a
  fund contributed to monthly but valued once. Set/clear and whole-fund deletes
  go through `InvestmentService.setFundCurrentValue` / `deleteFund`, never
  per-row DAO calls.
- **Balances are signed:** liabilities (loans, credit-card dues) are stored as
  negative amounts, so `BalanceInput.balance` has no `@PositiveOrZero`.
- **Dates are stored as ISO `TEXT`** (`LocalDate.toString()`), parsed back in the
  DAO row mappers.
- **The data is a shared household dataset by design** — all users see and edit
  the same rows; there is no per-user ownership, so cross-user access is not an
  IDOR.

### Auth

JWT with silent refresh. `POST /api/login` returns a short-lived HMAC access
token (held in memory by the SPA, sent as `Authorization: Bearer`) plus a
long-lived refresh token in an `HttpOnly`/`SameSite=Strict` cookie, rotated on
each `POST /api/refresh`. Valid refresh-token ids are tracked **in memory**
(`RefreshTokenStore`), so a server restart or `POST /api/logout` revokes
outstanding refresh tokens (everyone must log in again). In a dev/test profile an
ephemeral JWT key is generated and the `admin`/`admin` account is created; any
non-dev profile fails fast without `SECURITY_JWT_SECRET_KEY` and configured
`app.users`.
