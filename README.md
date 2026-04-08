# API Monitor

A Spring Boot application that monitors the availability and latency of external API endpoints in real time. A scheduled background service checks each active endpoint on a configurable interval and persists results to a database. A dark-themed React SPA polls the backend every 10 seconds to display live status cards and a latency leaderboard.

---

## Features

- **Live health dashboard** — status cards for every active endpoint with latency, HTTP status, and last-checked timestamp
- **Latency leaderboard** — top 3 fastest active endpoints ranked with gold/silver/bronze highlighting
- **Public watchlist management** — any visitor can activate or deactivate pre-defined catalog endpoints; no login required
- **Endpoint catalog** — browsable dropdown of pre-defined endpoints; click once to add to the active watchlist
- **Custom endpoint tracking** — admins can add arbitrary HTTPS URLs to monitor alongside the built-ins
- **Public endpoint suggestions** — visitors can suggest new endpoints via a submission queue; admins review and approve or deny
- **Drag-and-drop card ordering** — reorder dashboard cards; order is persisted to `localStorage`
- **Admin login UI** — lock-icon button in the header; validates the API key server-side and issues an httpOnly session cookie
- **Email notifications** — optional SMTP integration notifies the admin when a new public submission arrives
- **Docker-first deployment** — single multi-stage `Dockerfile`; `docker-compose.yml` for local or self-hosted use
- **CI pipeline** — GitHub Actions builds and tests on every push to `main`

---

## Architecture

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Spring Boot 3.5, Spring WebFlux (WebClient), Spring Data JPA |
| Database (dev/test) | H2 (in-memory) |
| Database (prod) | PostgreSQL |
| Schema migrations | Flyway |
| Frontend | React 18, Vite 5, TypeScript, Tailwind CSS |
| Data fetching | @tanstack/react-query v5 |
| Drag-and-drop | @dnd-kit/sortable |
| HTTP client | axios |
| Containerisation | Docker (multi-stage), Docker Compose |

---

## Authentication & Permission Model

The application uses a tiered permission model — most read and watchlist actions are fully public; only administrative operations require the API key.

### Public (no login required)

| Action | How |
|--------|-----|
| View the dashboard and leaderboard | Navigate to the app |
| Activate a pre-defined catalog endpoint | Click **Add to Tracker** in the catalog dropdown |
| Deactivate a pre-defined endpoint | Click **×** on the status card |
| Clear all active endpoints | Click **Clear All** |
| Submit a new endpoint suggestion | Click **+ Suggest an API**, fill in the form |
| Check the status of your own submission | Poll `GET /api/submissions/{token}` |

### Admin only (API key required)

| Action | How |
|--------|-----|
| Add a custom HTTPS endpoint directly | Click **+ Add Custom API** (visible as **+ Suggest an API** to public users) |
| Delete a custom endpoint permanently | Click **×** on a card with a **CUSTOM** badge |
| Review the pending submission queue | **Submissions** panel in the dashboard |
| Approve or deny a submitted endpoint | Approve / Deny buttons in the **Submissions** panel |

### Admin login flow

1. Click the **🔒** icon in the top-right corner of the dashboard.
2. Enter your API key in the modal and click **Sign in**.
3. The key is validated against `POST /api/auth/ping` — a `204` confirms it is correct.
4. On success, the server creates a server-side session and sets an **httpOnly `admin_session` cookie** (8-hour sliding window). 
5. On every page load the frontend calls `GET /api/auth/status` to silently restore admin mode from the cookie — no key entry required.
6. The session expiry is extended on every authenticated request. After 8 hours of inactivity the session expires and the admin is signed out automatically.
7. To sign out early, click **Sign out** next to the **Admin ●** indicator — this calls `POST /api/auth/logout` to invalidate the server-side session immediately.

**Client-side rate limiting:** 10 failed login attempts within any 10-minute window triggers a 1-hour client-side lockout (stored in `localStorage`). Only `401` responses count; network errors are ignored. A successful login clears the counter.

---

## API Reference

All endpoints are relative to the server root (e.g. `http://localhost:8080`).

### Health metrics

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/health-metrics` | Public | List all active endpoint metrics |
| `POST` | `/api/health-metrics/activate/{id}` | Public | Activate a pre-defined endpoint |
| `POST` | `/api/health-metrics/deactivate/{id}` | Public | Deactivate a pre-defined endpoint |
| `POST` | `/api/health-metrics/deactivate/all` | Public | Deactivate all active endpoints |

### Custom endpoints (admin)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/custom-endpoints` | **Admin** | Add a custom HTTPS endpoint to monitor |
| `DELETE` | `/api/custom-endpoints/{id}` | **Admin** | Remove a custom endpoint permanently |

### Public submissions

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/submissions` | Public | Submit a new endpoint suggestion |
| `GET` | `/api/submissions/{token}` | Public | Poll the status of a submission by its UUID token |

### Submission review (admin)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/submissions` | **Admin** | List all pending submissions |
| `POST` | `/api/submissions/{id}/approve` | **Admin** | Approve a submission (adds it to the active watchlist) |
| `POST` | `/api/submissions/{id}/deny` | **Admin** | Deny a submission |

### Authentication

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/ping` | **Admin** | Validate the API key; sets session cookie on success (`204`), `401` on failure |
| `GET` | `/api/auth/status` | Public | Returns `{"admin": true}` if a valid session cookie is present |
| `POST` | `/api/auth/logout` | Public | Invalidates the session and clears the `admin_session` cookie |

**Admin requests** must include the header `X-API-Key: <your-key>` (initial login) or send the `admin_session` httpOnly cookie set after a successful ping (subsequent requests).

---

## Running Locally

### Prerequisites

- Docker & Docker Compose
- (Optional, for frontend hot-reload) Node.js 20+ and npm

### 1 — Clone and configure

```bash
git clone https://github.com/your-username/ApiMonitor.git
cd ApiMonitor/api-monitor
cp .env.example .env
```

Edit `.env` and set `SECRET_SECURITY_KEY` to a strong secret. 

### 2 — Start with Docker Compose

```bash
docker compose up --build
```

The app is available at **http://localhost:8080**.

### 3 — Sign in as admin (browser)

Click the **🔒** icon in the top-right corner of the dashboard and type `dev-api-key` into the login modal. The server validates the key, creates a server-side session, and sets an httpOnly `admin_session` cookie. The **Admin ●** indicator will appear, unlocking the submissions panel and custom-endpoint management.

## Configuration Reference

All variables are set in `api-monitor/.env` (local) or as container environment variables (production).

| Variable | Default | Description |
|----------|---------|-------------|
| `SECRET_SECURITY_KEY` | `dev-api-key` | Admin API key — **required in production**; set to a strong random secret. |
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring profile. Use `dev` to enable the H2 console and Swagger UI. Set automatically by `docker-compose.yml`. |
| `DB_USERNAME` | `apimonitor` | PostgreSQL username. |
| `DB_PASSWORD` | — | PostgreSQL password — **required in production**. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8080` | Comma-separated list of allowed CORS origins (e.g. your public domain). |
| `TUNNEL_TOKEN` | — | Cloudflare Tunnel token. Required only if routing traffic through Cloudflare Tunnel. |
| `MONITOR_CHECK_INTERVAL_MS` | `60000` | Health-check polling interval in milliseconds. |
| `MAIL_HOST` | *(disabled)* | SMTP server hostname. Leave blank to disable email notifications entirely. |
| `MAIL_PORT` | `587` | SMTP port (typically `587` for STARTTLS, `465` for SSL). |
| `MAIL_USERNAME` | — | SMTP account username. |
| `MAIL_PASSWORD` | — | SMTP account password. |
| `MAIL_TO` | — | Email address that receives new-submission alerts. |

Email notifications are entirely optional. If `MAIL_HOST` or `MAIL_TO` is blank the application starts normally and skips all email sending silently.

---

## CI / CD

GitHub Actions runs on every push and pull request targeting `main` or `master`:

1. **Run tests** — `mvn -B test` against the `test` profile (H2 in-memory)
2. **Docker build** — multi-stage build compiles the JAR and React bundle inside Docker; no secrets are baked into the image
3. **Upload Surefire reports** — test results are uploaded as a workflow artifact on every run

---

## Security Considerations

### Public exposure by design

Watchlist management (activate, deactivate, clear all) is intentionally public so that any visitor can curate what they see. This is appropriate for a personal or team dashboard. If you need watchlist operations to be private, add an `authenticated()` rule for those routes in `SecurityConfig.java`.

### Rate limiting

The backend applies a global rate limit of **120 requests per minute per IP address**. Requests exceeding this limit receive `429 Too Many Requests`. When deployed behind Cloudflare Tunnel, set `TRUST_PROXY=true` so that the real client IP is read from the `CF-Connecting-IP` header instead of the tunnel egress IP.

### SSRF protection

All URLs submitted for monitoring are validated before the first health check:
- HTTPS only (HTTP URLs are rejected)
- Hostname must not match a blocklist of private/internal patterns
- DNS resolution is performed and every returned IP is checked against private, loopback, link-local, and wildcard ranges
- A custom Netty resolver re-validates the resolved IP at connection time, closing the DNS-rebinding TOCTOU window
