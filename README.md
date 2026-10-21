# Household Expense & Investment Tracker

A web app for tracking household finances (expenses, income, investments and
account balances). Data lives in a **native SQLite database** — the single source
of truth — for fast, indexed, transactional access. You can **download the data
as an Excel workbook** at any time, and in production a periodic Excel export is
pushed to Google Drive as an offsite backup (with the Summary/Monthly tabs,
formulas and charts intact).

- **Backend:** Java 25 + Spring Boot (virtual threads), SQLite via Spring JDBC,
  Apache POI (write-only, for the Excel export), Google Drive API (backup upload).
- **Frontend:** React + Vite + TypeScript SPA, served as static assets by the same
  Spring Boot service — **one deployable**.
- **Performance:** every read is an indexed SQLite query and every write is a
  single transactional statement — no workbook parsing, in-memory cache, or
  debounce/reconcile machinery.

## Architecture

```
Browser (React SPA)  ──JSON──▶  Spring Boot REST API  ──▶  per-entity DAOs ──▶ SQLite (tracker.db)
                                        │
                                        ├── GET /api/export ──▶ POI exporter ──▶ .xlsx download
                                        └── @Scheduled backup ──▶ POI exporter ──▶ Google Drive .xlsx
```

One SQLite database (`tracker.db`) holds all financial tables **and** the audit
log. The dashboard computes its aggregates in Java over the rows it reads, so it
never depends on spreadsheet formulas. The Excel export loads a bundled blank
template and fills its five data sheets; Excel recomputes the Summary/Monthly
tabs on open (`forceFormulaRecalculation` is set on save).

## Prerequisites

- JDK 25 (a Zulu/Temurin build). The Gradle toolchain targets Java 25.
- Node.js 20+ and npm (only needed to build the frontend).
- A Google Cloud project with a service account (only for the Drive backup in
  production; local runs need no Google setup).

## Running locally (no Google setup needed)

```bash
# 1. Build the SPA and bundle it into the jar
./gradlew build -PwithFrontend

# 2. Run — the SQLite DB is created automatically (empty) on first start
DATA_DB_PATH=./data/tracker.db \
  java -jar build/libs/expense-tracker-0.1.0.jar
```

Open http://localhost:8080. The database starts **empty**; add data through the
UI. With no `app.users` configured, a dev account **`admin` / `admin`** is
created (a warning is logged) — fine for local use only. With `DRIVE_FILE_ID`
unset, the Drive backup is simply disabled.

### Frontend dev server (hot reload)

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173, proxies /api to :8080
```

Run the backend separately (step 2 above) while using the Vite dev server.

## Migrating from an existing workbook

If you already track finances in a household workbook (the same `.xlsx` shape the
app exports — sheets `Expenses`, `Investments`, `Income`, `Balances`, `Lists`,
data from row 5), you can load it into a fresh database in one shot. Pass
`--import=<path>` on startup:

```bash
DATA_DB_PATH=./data/tracker.db \
  java -jar build/libs/expense-tracker-0.1.0.jar \
  --import=./Household_Expenses_and_Investments_Tracker-3.xlsx
```

The importer reads each data sheet with Apache POI (decoding Excel date serials
and shared strings), stops at the first row with a blank date, and inserts every
row through the normal DAOs; the auto-formula columns (Month / Year / Gain-Loss /
Net-Worth) are ignored and recomputed by the app. It logs a per-sheet count when
done. The app then continues to run normally — stop it (Ctrl-C) if you only wanted
the migration.

The import is **additive** and meant for a **fresh, empty database**: to avoid
duplicating rows it refuses to run when the database already holds
expense/investment/income/balance data. Pass `--import-force` to override that
guard (rows are added, not replaced).

> On a corporate network with a TLS-intercepting proxy, add
> `-Djavax.net.ssl.trustStoreType=Windows-ROOT` to the `java`/Gradle command
> (already set in `gradle.properties` for builds).

## Google Drive backup setup (production, optional)

The app periodically exports the data to an `.xlsx` and overwrites a Drive file
with it, giving you an offsite backup you can open in Excel. To enable it:

1. **Create a service account**
   - Google Cloud Console → *IAM & Admin → Service Accounts → Create*.
   - Create a **JSON key** for it and download it. Keep it secret.
   - Enable the **Google Drive API** for the project
     (*APIs & Services → Library → Google Drive API → Enable*).
2. **Create the backup target and share it with the service account**
   - Upload any `.xlsx` to Google Drive to act as the backup file (its contents
     are overwritten on each backup).
   - Share it with the service account's email
     (`...@...iam.gserviceaccount.com`) as **Editor**.
3. **Get the file id** from the share link:
   `https://drive.google.com/file/d/`**`<FILE_ID>`**`/view`.

The backup runs on a cron schedule (`BACKUP_CRON`, default 03:00 daily) whenever
`DRIVE_FILE_ID` is set; it is best-effort and never blocks or breaks API writes.

## Login accounts (you + spouse)

Credentials are BCrypt hashes supplied via env vars. Generate a hash:

```bash
# htpasswd ships with Apache tools (apache2-utils / httpd-tools)
htpasswd -bnBC 10 "" 'your-password' | tr -d ':\n'
```

Set two accounts via the env vars below. If none are set, the dev `admin/admin`
account is used (not for production).

## Configuration (environment variables)

| Variable | Purpose |
| --- | --- |
| `PORT` | HTTP port (default 8080; most hosts set this). |
| `DATA_DB_PATH` | Path to the SQLite database file (default `data/tracker.db`; parent dir auto-created). Holds financial data **and** the audit log. |
| `DRIVE_FILE_ID` | Drive file id of the backup `.xlsx` (enables the periodic Drive backup). |
| `GOOGLE_CREDENTIALS_JSON` | Service-account key as an inline JSON string. |
| `GOOGLE_CREDENTIALS_PATH` | …or a path to the key file (use one or the other). |
| `BACKUP_CRON` | Cron for the Drive backup (default `0 0 3 * * *` = 03:00 daily). Only used when `DRIVE_FILE_ID` is set. |
| `USER1_NAME`, `USER1_HASH` | First account's username and BCrypt hash. |
| `USER2_NAME`, `USER2_HASH` | Second account's username and BCrypt hash. |
| `SECURITY_JWT_SECRET_KEY` | **Required in production.** Base64-encoded, ≥256-bit HMAC secret used to sign JWTs. Startup fails if unset in a non-dev profile. |
| `SECURITY_JWT_EXPIRATION_TIME` | Access-token lifetime in milliseconds (default `3600000` = 1 hour). |
| `SECURITY_JWT_REFRESH_EXPIRATION_TIME` | Refresh-token lifetime in milliseconds (default `2592000000` = 30 days). |
| `REFRESH_COOKIE_SECURE` | `Secure` flag on the refresh cookie (default `true`). Set `false` only to test over plain http. |

The app always uses the local SQLite database at `DATA_DB_PATH`. The Google Drive
backup is enabled only when `DRIVE_FILE_ID` (and a credential) is set; otherwise
backups are skipped and the data lives solely in the local DB.

## Build & run with Docker

```bash
docker build -t expense-tracker .
docker run -p 8080:8080 \
  -v expense-data:/data \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DRIVE_FILE_ID=... \
  -e GOOGLE_CREDENTIALS_JSON="$(cat service-account.json)" \
  -e USER1_NAME=you    -e USER1_HASH='$2a$10$...' \
  -e USER2_NAME=spouse -e USER2_HASH='$2a$10$...' \
  -e SECURITY_JWT_SECRET_KEY="$(openssl rand -base64 48)" \
  expense-tracker
```

The multi-stage build compiles the SPA, builds the Spring Boot jar, and ships a
slim JRE 25 runtime tuned for low memory (serial GC, capped heap). **Mount a
persistent volume at `/data`** (as above) — that is where `tracker.db` and its
WAL sidecars live; without it the data is lost on container restart.

## Deploying to a free tier

Any host that runs a container or a Java jar works. Using the `Dockerfile`:

- **Render / Koyeb / Fly.io:** point the service at this repo (Docker build) or
  push the image. Set the env vars from the table above (store the SA key and
  `SECURITY_JWT_SECRET_KEY` as secrets, and set `SPRING_PROFILES_ACTIVE=prod`).
  The host provides HTTPS and sets `PORT`.
- **Attach a persistent disk and point `DATA_DB_PATH` at it** (e.g. a Render disk
  mounted at `/data`, with `DATA_DB_PATH=/data/tracker.db`) so the database
  survives restarts and redeploys. Enabling the Drive backup gives an additional
  offsite copy.
- Prefer `GOOGLE_CREDENTIALS_JSON` (a secret value) over committing a key file.

Note: free tiers sleep when idle and have ~512 MB RAM; SQLite plus a small heap
fits comfortably.

## API

All endpoints require a valid bearer token except `POST /api/login`,
`POST /api/refresh`, and `POST /api/logout`. The SPA sends the access token as
`Authorization: Bearer <token>` on every request; the refresh endpoints are
authenticated by the refresh cookie instead.

| Method & path | Description |
| --- | --- |
| `POST /api/login` | `{username, password}` → `{token, username}` and sets the refresh cookie. `token` is a short-lived access JWT. |
| `POST /api/refresh` | Reads the refresh cookie, rotates it, → `{token, username}` with a fresh access token (401 if the cookie is missing/expired/revoked). |
| `POST /api/logout` | Revokes the refresh token server-side and clears the cookie (`204`). |
| `GET /api/me` | Current username (401 if the token is missing/invalid/expired). |
| `GET /api/dashboard?year=` | KPIs + chart aggregates for a year. |
| `GET/POST /api/expenses`, `PUT/DELETE /api/expenses/{id}` | Expense CRUD. |
| `GET/POST /api/investments`, `PUT/DELETE /api/investments/{id}` | Investment CRUD. |
| `GET/POST /api/income`, `PUT/DELETE /api/income/{id}` | Income CRUD. |
| `GET/POST /api/balances`, `PUT/DELETE /api/balances/{id}` | Balance-snapshot CRUD. |
| `GET /api/lists`, `PUT /api/lists/{column}` | Read / replace dropdown lists. |
| `GET /api/export` | Download all data as an `.xlsx` workbook (the same artifact used for the Drive backup). |
| `GET /api/audit` | Recent audit-log entries. |

`{column}` is one of `categories`, `payment-methods`, `people`,
`investment-types`, `accounts`, `income-types`, `account-types`.

## Tests

```bash
./gradlew test
```

The suite covers the per-entity DAOs against a real SQLite database (insert /
query / update / hard-delete, the fund current-value and delete-fund semantics,
and list replacement), the Java dashboard aggregation, the audit recording in
each service, and the Excel export (data lands in the right cells and all sheets
and charts survive the POI round-trip).

## Security notes

- **JWT auth with silent refresh.** `POST /api/login` returns a short-lived
  (default 1h) HMAC-SHA256 access token the SPA holds in memory and sends as an
  `Authorization: Bearer` header, plus a long-lived (default 30d) refresh token
  in a `HttpOnly`, `Secure`, `SameSite=Strict` cookie. On a 401 the SPA silently
  calls `POST /api/refresh` to get a new access token and retries; the same call
  restores the session after a page reload. CSRF protection stays disabled
  safely: every state-changing endpoint authenticates via the Bearer header
  (unreachable cross-site), and the one cookie is `SameSite=Strict`.
- **Refresh tokens are rotated and revocable.** Each `/api/refresh` issues a new
  refresh token and invalidates the old one; the server tracks valid token ids
  in memory, so `POST /api/logout` and a server restart both revoke outstanding
  refresh tokens (clients must log in again).
- **Set `SECURITY_JWT_SECRET_KEY`** (base64, ≥256-bit) in production. Startup
  fails fast if it is blank in a non-dev profile. A stolen *access* token is
  valid only until it expires (keep `SECURITY_JWT_EXPIRATION_TIME` modest); a
  stolen refresh token can be revoked via logout/restart.
- **Run production with `SPRING_PROFILES_ACTIVE=prod`.** Outside a dev/test
  profile the app refuses to start without configured `app.users` (no
  `admin/admin` fallback) and without a JWT secret.
- **Login is rate-limited** (per-IP lockout after repeated failures) to blunt
  brute-force attempts.
- Never commit the service-account key, real credential hashes, or the workbook
  (`.gitignore` excludes common names and `*.xlsx`).
