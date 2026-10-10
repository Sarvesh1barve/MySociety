# GitHub Pages deployment

1. Start PostgreSQL. This workstation uses loopback port **5433**; Docker defaults to **5432** unless POSTGRES_PORT changes it.
2. Inspect ports with `Get-NetTCPConnection -State Listen -LocalPort 8080,8081 -ErrorAction SilentlyContinue`. Do not terminate unrelated listeners.
3. Start `scripts/start-backend.ps1` (add `-Dev` only for the separate demo environment). Record the actual API port. This installation uses **8081**.
4. Verify `http://localhost:8081/api/v1/system/health` returns UP.
5. Start `ngrok http 8081`. If the startup script selected another port, use that port.
6. Copy the HTTPS tunnel origin, without /api/v1.
7. In GitHub repository Settings → Secrets and variables → Actions → Variables, set **API_BASE_URL** to that origin.
8. Configure backend CORS to include **https://sarvesh1barve.github.io** (no /MySociety path).
9. Repository Settings → Pages → Build and deployment → Source: **GitHub Actions**.
10. Run the **Deploy GitHub Pages** workflow, or push frontend changes to main.
11. Open **https://sarvesh1barve.github.io/MySociety/**, sign in, and verify society selection and the platform Diagnostics page.
12. Verify a real visitor approval from a resident session and check-in from a watchman session.

## Workflow behavior

The workflow installs locked dependencies, runs frontend unit tests, writes runtime configuration from API_BASE_URL, and builds with `--base-href /MySociety/` inferred from the repository name. The generated artifact contains the Angular service worker and PWA manifest. Hash routing keeps refreshes valid.

API_BASE_URL is public configuration, not a secret. Database passwords, JWT secrets, owner passwords and ngrok authentication tokens must never be GitHub frontend variables.

The runtime config generator enables the ngrok warning bypass only for recognized ngrok hosts. Invalid or missing API_BASE_URL stops deployment with a useful error.

## Changing the tunnel URL

Update **API_BASE_URL**, then manually rerun **Deploy GitHub Pages**. No feature service contains an embedded tunnel URL. The checked-in runtime config remains a local-development default.

## Availability

GitHub Pages serves static assets. Authentication and society operations need the local backend/database/tunnel online. When the laptop sleeps, the UI reports server unavailability. Installability and offline shell support do not provide offline approval or write queues.
