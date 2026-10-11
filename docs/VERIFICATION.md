# Verification

Verified locally on 11 October 2026 against PostgreSQL 17.6 and Java 21.

| Check | Result |
| --- | --- |
| Backend | Maven `verify`: 23 integration tests, zero failures/errors; executable production JAR built |
| Database | Flyway V1–V4 applied; Hibernate schema validation succeeds |
| Frontend | 18 unit tests pass; Angular production build passes (389.21 kB initial / approximately 101.64 kB transfer) |
| Browser application | 17 tests covering themes, recovery/login changes, visitor lifecycle, permissions, tenant switching, signup/approval, user listing/edit/suspend, mobile screens and expired sessions |
| Production PWA | Installable manifest, offline application shell, no API/private data/runtime-config in service-worker caches |
| Dependencies | npm audit: zero vulnerabilities in the locked frontend dependency tree |
| Live data | Only the real Super Admin account remains; owner-edited society/resident configuration preserved |
| Production profile | Demo fixtures absent from production JAR; Swagger disabled; missing routes return friendly 404 |

## Security and workflow coverage

Backend tests cover valid/invalid login, duplicate signup (normalized email/mobile), weak/mismatched passwords, rejected privileged signup fields, no-membership login, join requests, approval and flat mapping, wrong-society request/flat/entity access, role permissions, user creation/list/edit/suspend/reactivate, token revocation, expired/tampered JWTs and authentication throttling. Existing tests cover resident/watchman visitor approval/check-in/out, parcel privacy, complaints, notice audiences, meeting approval locking, upload validation, private documents and CORS.

Recovery tests cover all role types, single-use/rotated/reused codes, concurrent consumption (exactly one succeeds), session revocation, owner login identity changes and restrictions on owner-assisted resets. Browser checks exercise theme controls, persistence and switching, recovery/password/login changes, public signup and pending access, approval through the society-admin UI, sole-society routing and admin-created users appearing after stale search filters.

Mobile checks cover login/signup/dashboard, visitors and forms, parcels, complaints, notices, meetings, emergency and society selection. Profile forms have unique accessible field IDs. Mutating browser tests use only the isolated `mysociety_e2e` database. The separate backend suite resets only `mysociety_test`. Browser service workers are disabled for isolated runtime overrides; production/hosted PWA checks leave them enabled.

## Commands

```powershell
. .\scripts\import-env.ps1
.\backend\mvnw.cmd -f backend\pom.xml verify dependency:build-classpath '-Dmdep.outputFile=target/test-classpath.txt' '-DincludeScope=test'
cd frontend
npm test -- --watch=false
npm run build
npm audit
cd ..
# Start production preview in a separate terminal: node scripts/preview.mjs
.\scripts\run-browser-tests.ps1
cd frontend
$env:CHECK_PWA='1'
$env:E2E_BASE_URL='http://localhost:4200'
npx playwright test e2e/pwa.spec.ts
```

After Pages deployment, the read-only `hosted.spec.ts` checks owner sign-in, protected-route reload, public signup availability, exact runtime API URL and PWA/offline behavior. Set `HOSTED_URL`, `EXPECTED_API_URL` and `HOSTED_LOGIN_CHECK=1`; credentials are loaded privately from `.env` or environment, never from source. Deployment run results are reported separately in the handover.

Backups, screenshots, credentials, test results, uploads and runtime binaries are ignored and must not be committed. These checks are a security baseline, not an independent penetration test or a guarantee of always-on hosting.
