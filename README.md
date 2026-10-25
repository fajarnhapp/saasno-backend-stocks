# Saasno Backend

Backend API for **Saasno** — a role-based access management system covering APIs, menus, roles, and users. Built with Spring Boot 3.5 / Java 21.

Pairs with [`saasno-frontend`](../saasno-frontend), a Next.js app. The frontend never calls this service from the browser — it proxies every request through its own Next.js API routes.

## Tech Stack

- **Framework:** Spring Boot 3.5.7, Java 21
- **Database:** PostgreSQL + Hibernate/JPA (`ddl-auto=validate`)
- **Session store:** Redis (session tokens, looked up per request — not JWT)
- **Messaging:** RabbitMQ (async notification delivery)
- **Password hashing:** Argon2 (`spring-security-crypto`)
- **Mapping:** MapStruct (entity ↔ DTO)

## Prerequisites

- Java 21 (JDK)
- Maven
- PostgreSQL, Redis, and RabbitMQ reachable (defaults below assume `localhost` with services running)

## Getting Started

```bash
mvn spring-boot:run
```

The server starts on port `8000`. Every config value below has a `localhost` default, so no environment variables are required to run locally, as long as Postgres/Redis/RabbitMQ are up on their standard ports.

## Environment Variables

All are optional locally — see the defaults in `src/main/resources/application.properties`.

| Variable | Default | Description |
|---|---|---|
| `SAASNO_DATABASE_URL` | `jdbc:postgresql://localhost:5432/saasno` | PostgreSQL JDBC URL |
| `SAASNO_DATABASE_USERNAME` | `database` | PostgreSQL user |
| `SAASNO_DATABASE_PASSWORD` | `database` | PostgreSQL password |
| `SAASNO_REDIS_HOST` | `localhost` | Redis host (port fixed at `6379`) |
| `SAASNO_REDIS_PASSWORD` | `admin` | Redis password |
| `SAASNO_RABBITMQ_HOST` | `localhost` | RabbitMQ host (port fixed at `5672`) |
| `SAASNO_RABBITMQ_USERNAME` | `admin` | RabbitMQ user |
| `SAASNO_RABBITMQ_PASSWORD` | `admin123` | RabbitMQ password |
| *(Configuration page)* | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH`, `MAIL_STARTTLS`, `MAIL_FROM`, `MAIL_FROM_NAME` | The SMTP server and the sender of the forgot-password and new-sign-in emails. Not environment variables: set them on the Configuration page (no restart). Empty `MAIL_HOST` = no email is sent. The password is write-only and shown as `********`. An empty `MAIL_FROM_NAME` means the app name |
| `APP_UPLOAD_DIR` | `./uploads/` | Folder for profile pictures, logo and downloads (`app.upload.dir`) |
| `SAASNO_MANAGEMENT_PORT` | `8081` | Port of the Prometheus metrics endpoint `/actuator/prometheus` (separate from the app port; do not publish it) |
| `SAASNO_MANAGEMENT_ADDRESS` | `127.0.0.1` | Address the metrics port listens on; `0.0.0.0` in Docker so Prometheus can reach it over a Docker network |
| `SAASNO_API_DOCS_ENABLED` | `false` | Serve the OpenAPI docs: Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs` (enable in dev/UAT only) |

The forgot-password link is built from the `FRONTEND_URL` setting (Configuration menu).

## Password Policy & Recovery

- **Forgot password** — `POST /api/public/password/forgot` (`{"username": "<email>"}`) emails a one-time link (`{FRONTEND_URL}/reset-password?token=…`); `POST /api/public/password/reset` (`{"token", "password"}`) sets the new password. The username is used as the email address. The answer is the same whether or not the account exists, at most 3 emails per account per hour are sent, and only the SHA-256 of the token is stored (Redis, expires after `PASSWORD_RESET_MINUTES`). A successful reset signs the user out everywhere.
- **Forced change** — a new user gets no password at all: the account is locked and an activation link is emailed, through which they choose their own (without working email nothing is sent, and an admin uses *Reset password* once email is set up). A user whose password was reset by an admin gets a new link the same way; every such user must change the password right after login; so must a user whose password is older than `PASSWORD_EXPIRY_DAYS`. Until then the session can only call `GET /api/user/me`, `PUT /api/user/change-password` and `DELETE /api/logout` (everything else answers `403 Password change required`).
- **Rules** — `GET /api/public/password/policy` returns `minLength` and `requireComplexity`. Clients send only the SHA-256 of a password, so the *client* checks length/complexity before hashing; the backend enforces the hash format, not a retired shared default, not the current password, and not one of the latest `PASSWORD_HISTORY_COUNT` passwords.
- **Settings** (Configuration menu): `PASSWORD_MIN_LENGTH` (default 8), `PASSWORD_REQUIRE_COMPLEXITY` (default `true`), `PASSWORD_EXPIRY_DAYS` (default `0` = never), `PASSWORD_HISTORY_COUNT` (default 5, `0` = off, max 24), `PASSWORD_RESET_MINUTES` (default 30).
- **Database** — run `docs/migrations/2026-10-21-password-policy.sql` before starting this version (adds `user.password_changed_at` and `user.password_must_change`; `ddl-auto=validate`).

## Profile Pictures, Application Settings & Uploads

- **Files** live under `app.upload.dir` (default `./uploads/`): `avatars/<userId>.png`, `branding/logo.png`, `downloads/`. In Docker this folder is the `saasno-uploads` volume (see `docker-compose.yml`), so files survive a redeploy.
- **Upload rules** (`ImageProcessor`): PNG or JPEG only (SVG is refused, it can carry scripts), at most 2 MB and 25 megapixels. The type is decided from the file's own bytes, then the picture is decoded and drawn again, so metadata and anything hidden in the file are dropped. Profile pictures become 256x256 squares, the logo is shrunk to fit 512 px. File names never come from the client (`FileStorageService` also refuses any path outside the upload folder).
- **Endpoints:** `GET|POST|DELETE /api/user/avatar` (multipart field `file`); `GET|PUT /api/app-setting`, `POST|DELETE /api/app-setting/logo` (admin); public read side `GET /api/public/app-setting` and `GET /api/public/app-logo`.
- **Application settings** are the configuration keys `APP_NAME` (default "Saasno"), `BRAND_HUE` (0-360, default 293) and `BRAND_CHROMA` (0.3-1.2, default 1). The frontend turns them into the brand colour; emails use the name and colour too.

## Maintenance & Automatic Cleanup

- A nightly job removes old data: downloads older than `DOWNLOAD_RETENTION_DAYS` (default 7, 03:45), audit log entries older than `AUDIT_LOG_RETENTION_DAYS` (default 365, `0` = keep forever, 04:00) and old notifications (`NOTIFICATION_RETENTION_DAYS`, 03:30). Cron expressions: `saasno.download-cleanup.cron`, `saasno.audit-cleanup.cron`, `saasno.notification-cleanup.cron`.
- `GET /api/maintenance` (figures), `POST /api/maintenance/cleanup` (run all cleanups now, or one kind with `?type=downloads`, `?type=audit-logs` or `?type=notifications`; any other type is a `400`) and `DELETE /api/maintenance/download` (**delete every download of every user**) are admin-only: only roles linked to them in `role_api` (SUPER) can use them. Each user can clear their own list with `DELETE /api/download/all`.

## Two-Step Recovery Codes, New-Device Alerts & IP Allowlist

- **Recovery codes:** enabling two-step verification returns 10 one-time codes (`XXXXX-XXXXX`, shown once; only SHA-256 hashes are stored in `user.two_factor_recovery_hashes`). At login a recovery code can be entered instead of the 6-digit code. `POST /api/user/otp/recovery?code=<authenticator code>` makes a new set (the old ones stop working). Using one raises a notification. Disabling two-step verification deletes them.
- **New-device alerts:** each login's User-Agent is remembered per user in Redis (1 year). A login from an unseen one creates a notification and, if SMTP is set up and the username is an email, an email. The first-ever login is not reported.
- **Login IP allowlist:** `LOGIN_IP_ALLOWLIST` (Configuration) takes IPs and CIDR ranges, e.g. `203.0.113.7, 10.0.0.0/8, 2001:db8::/32`; empty = anywhere. It is checked before the account is looked up, using `X-Forwarded-For` set by the frontend, so the backend must only be reachable through the frontend. Saving a list that does not include the saving admin's own IP is refused. If you ever lock yourself out, empty the value in the database: `UPDATE configuration SET value = '' WHERE id = 'LOGIN_IP_ALLOWLIST';`
- Database: run `docs/migrations/2026-10-22-base-features.sql` (new column, settings, endpoints and menus; it also removes configuration rows this backend does not know).

## Maintenance Mode

- **On/off:** `GET|PUT /api/maintenance/mode` (SUPER only, via `role_api`). The request is `{"enabled": true, "message": "...", "until": "2026-10-23T18:00", "bypassRoles": ["MANAJER"]}`; the settings are the configuration keys `MAINTENANCE_MODE`, `MAINTENANCE_MESSAGE`, `MAINTENANCE_UNTIL`, `MAINTENANCE_BYPASS_ROLES`.
- **Who keeps working:** the roles in `MAINTENANCE_BYPASS_ROLES`. **SUPER is always one of them** (it is added even when missing from the list), so someone can always switch maintenance off. Everybody else gets `503` with the message on every request, and cannot sign in; signing out still works. It is judged from the user's roles at the moment of the request, so users already signed in are blocked too, and take no sessions down with them.
- **How it ends:** switch it off (`enabled: false`), or let `MAINTENANCE_UNTIL` pass (server time, `yyyy-MM-ddTHH:mm`): it is active only while the time is ahead, so a forgotten switch ends by itself. Emergency: `UPDATE configuration SET value = 'false' WHERE id = 'MAINTENANCE_MODE';` (takes effect within about 5 seconds; the state is cached that long).
- **Public side:** `GET /api/public/app-setting` carries `maintenance: {active, message, until}` (the message is only included while it is active), used by the login banner and the `/maintenance` page.
- Database: `docs/migrations/2026-10-23-maintenance-mode.sql`.

## Health Check

`GET /api/public/health` → `200 {"status":"UP","version":"…","components":{"postgres","redis","mongodb","rabbitmq"}}`, or `503` when any dependency is down. `docker-compose.yml` uses it as the container health check.

## Tests

`mvn test` runs the unit tests. `SaasnoBackendApplicationTests` (full context) needs the real PostgreSQL/Redis/RabbitMQ/MongoDB, so it runs only with `SAASNO_INTEGRATION_TEST=true`. The `test` job in `.github/workflows/ghcr.yml` runs `mvn verify` on every pull request and push to `main`/`UAT`, and before an image is built.

## Modules

Each module below is a matching Entity + Repository + Service + Controller, following the same generic CRUD + pagination/filter pattern (`DataFilter`/`DataTable`, see below):

| Module | Endpoint base | Notes |
|---|---|---|
| Api | `/api/api` | Registry of protected endpoints (id, name, method, path) — the permission catalog itself |
| Menu | `/api/menu` | Sidebar menu tree (parent/child via `parentId`) |
| Role | `/api/role` | On creation, automatically grants a baseline set of self-service API permissions (own profile, notifications, password change, menu-access check) and the `DASHBOARD` menu — see `RoleService.create()` |
| RoleApi | `/api/role-api` | Role ↔ API permission assignment |
| RoleMenu | `/api/role-menu` | Role ↔ Menu assignment, plus `GET /api/role-menu/access` to check the current session's access to a menu |
| User | `/api/user` | Includes `/me`, `/reset-password` (admin-initiated), `/change-password` (self-service) |
| Configuration | `/api/configuration` | Simple key/value app settings |
| Notification / NotificationUnread | `/api/notification`, `/api/notification-unread` | Per-user notification list + unread counter; delivery fanned out via RabbitMQ |
| Authentication | `/api/login`, `/api/logout` | Session-based (not JWT) — see below |

> **Note:** the frontend also has Position and Position-Role screens (`/api/position`, `/api/position-role`), but there is currently no matching entity/controller for either on this backend — those calls will 404 until implemented here.

## Authorization Model

This isn't just "logged in or not" — every non-public request is checked against the **exact** `method` + `path` pair recorded in the `api` table for one of the caller's roles (`SessionService.check`, backed by `role_api`). That's why the `Api` module exists: it's a literal registry of every protected endpoint, and a role only gets access to an endpoint once someone links it via `role_api`. Endpoints that take an id as a path variable (rather than in the query string or body) can't be matched this way — keep new endpoints consistent with the existing `?id=`/body-based pattern rather than `/resource/{id}`.

Sessions live in Redis, keyed by a bearer token issued at login; there is no JWT — every request does a Redis lookup via `SessionFilter`.

## Generic Query Layer

Most `read()` endpoints accept arbitrary query params, which `DataFilter` (`utility/model/data/DataFilter.java`) turns into `ILIKE` filters automatically — any request param that isn't `limit`/`offset`/`order`/`sorting`/`group`/`custom`/`startDate`/`endDate` becomes a filter on that entity field, including dotted paths for auto-joined relations (e.g. `role.id`). No per-field filter code is needed on the backend when the frontend adds a new filter input.

## Building

```bash
mvn clean package -DskipTests
java -jar target/saasno-backend-*.jar
```

## Deployment

`Dockerfile`/`docker-compose.yml`/`docker-entrypoint.sh` build and run the packaged jar in a two-stage image (Maven build, then a JRE). The service, container, Docker network (`saasno-backend`, shared with the frontend) and the jar inside the image are all named `saasno-backend`. The app listens on port 8000.

## License

All rights reserved — see [`LICENSE`](./LICENSE).
