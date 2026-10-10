# ngrok setup

Install ngrok from [ngrok's official download page](https://ngrok.com/download), sign in to your ngrok account, and configure its authentication token locally following the account setup instructions. Never commit the token or its configuration file.

## Choose the actual API port

Check listeners first. This project found 8080 occupied and selected **8081**. The existing service was not changed.

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8080,8081 -ErrorAction SilentlyContinue
.\scripts\start-backend.ps1
```

The startup script prints the chosen port and writes `.local/backend-port.txt`. Tunnel exactly that port:

```powershell
ngrok http 8081
# Or:
.\scripts\start-ngrok.ps1
```

**Backend 8081 → ngrok http 8081.** If the backend is 8082, use `ngrok http 8082`.

Never tunnel PostgreSQL 5432/5433, and do not expose the upload directory as a static website.

## Frontend configuration

Copy the HTTPS origin to the GitHub repository variable API_BASE_URL and redeploy Pages. The backend CORS allowlist must include the Pages origin, not the ngrok origin:

```text
APP_CORS_ALLOWED_ORIGINS=http://localhost:4200,https://sarvesh1barve.github.io
```

The frontend interceptor adds `ngrok-skip-browser-warning: true` only when runtime config enables it and the API host is a recognized ngrok domain. Backend OPTIONS preflight allows that header together with Authorization, Content-Type and X-Society-Id.

## Troubleshooting

- 502/connection refused: the backend is stopped, the tunnel points at the wrong port, or the laptop slept.
- ngrok authentication error: authenticate the local agent; do not paste the token into source code or chat logs.
- Tunnel limit/domain conflict: preserve other active tunnels. Use your account dashboard to choose an available endpoint; do not terminate unrelated work.
- Browser interstitial/HTML instead of JSON: confirm bypass-header configuration and the tunnel hostname.
- CORS error: confirm the exact frontend scheme/host/port in APP_CORS_ALLOWED_ORIGINS, then restart Spring Boot.
- URL changed: update API_BASE_URL and rerun Pages deployment.
- Check the local ngrok inspector only on loopback. Another agent may already use 4040; this project's ngrok may choose a different inspector port.
