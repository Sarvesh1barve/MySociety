# Verification

Verified locally on 10 October 2026.

| Check | Result |
| --- | --- |
| Java / backend package | Java 21; Maven verify succeeds; executable Spring Boot JAR produced |
| PostgreSQL / Flyway | Real PostgreSQL 17 on loopback 5433; V1 and V2 migrations applied; Hibernate schema validation passes |
| Backend integration suite | **14 passed**, 0 failures/errors |
| Frontend service/guard suite | **12 passed**, 0 failures |
| Browser application suite | **9 passed**, including two-session visitor lifecycle, complaint timeline, society switching, branding, permission guard, mobile layout and API failure handling |
| Production PWA suite | **1 passed**: valid manifest, no installability errors, offline application shell, no API/private data/runtime-config in service-worker caches |
| Angular production build | Passes; approximately 380 kB initial raw bundle / 99 kB estimated transfer, with feature routes lazy-loaded |
| Dependency audit | npm audit reports **0 vulnerabilities** in the locked frontend dependency tree |
| API health | UP on localhost:8081 and through the HTTPS ngrok tunnel |
| OpenAPI | Development /v3/api-docs returns 200; protected operations still require bearer authentication |
| CORS | Allowed-origin preflight passes; untrusted origin rejected |
| Backup | PostgreSQL custom-format backup and upload directory saved to a unique ignored local directory |
| Restore | Restored to separate mysociety_restore_verified database and separate files; 2 societies, 5 accounts and 2 applied migrations verified |
| Visual QA | Resident/committee desktop and 390-pixel mobile screenshots inspected; no mobile horizontal overflow |
| Existing services | Existing 8080 and 5432 listeners preserved; project uses 8081 and 5433 |

## Tests

Backend tests cover authentication, invalid login, unauthenticated access, permission denial, membership checks, cross-society access, role differences after switching, walk-in visitor approval/check-in/check-out, invalid duplicate transitions, expected time windows, visitor denial, parcel privacy/collection, complaint history, notice audiences and acknowledgements, approved MOM locking, file-type rejection, protected documents, platform society creation/branding/status, CORS, same-society flat privacy, ended occupancy and password-change token revocation.

Frontend unit tests cover centralized runtime API configuration, protocol validation, ngrok header scoping, dynamic branding, API headers, public requests, login success/failure, context clearing, permission/platform/context guards and logout.

Browser tests use generated local dev credentials and the real API. The PWA check uses the production build and Chromium installability diagnostics. No screenshots or authentication state containing private data are tracked.

## Commands

```powershell
. .\scripts\import-env.ps1
.\backend\mvnw.cmd -f backend\pom.xml verify
cd frontend
npm test -- --watch=false
npm run build
npx playwright test --grep-invert 'production PWA'
# Separate production preview:
cd ..
node scripts/preview.mjs
# Another terminal:
cd frontend
$env:E2E_BASE_URL='http://localhost:4201'
$env:CHECK_PWA='1'
npx playwright test e2e/pwa.spec.ts
```

Backend tests only reset the dedicated mysociety_test database. Test data and local verification backups remain ignored.
