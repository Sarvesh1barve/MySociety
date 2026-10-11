# Delivery guide

1. **Architecture:** Angular 21 PWA → HTTPS ngrok → Java 21 / Spring Boot 3.5 modular monolith → PostgreSQL + local protected uploads.
2. **Structure:** frontend/, backend/, docs/, scripts/, .github/workflows/, docker-compose.yml and root README.
3. **Backend port:** 8081.
4. **Reason:** 8080 was occupied; its existing process was preserved.
5. **PostgreSQL:** this project runs a loopback-only instance on 5433; another service on 5432 was preserved. Docker Compose and existing PostgreSQL are also supported.
6. **Backend startup:** from root, scripts/start-backend.ps1. Use -Dev only for development API documentation. The wrapper imports .env and selects a free port.
7. **Frontend startup:** cd frontend; npm ci; npm start. Local URL: http://localhost:4200.
8. **Owner creation:** first startup reads INITIAL_SUPER_ADMIN_NAME, INITIAL_SUPER_ADMIN_EMAIL/MOBILE and INITIAL_SUPER_ADMIN_PASSWORD only if no owner exists.
9. **ngrok:** ngrok http 8081 --inspect=false, or scripts/start-ngrok.ps1. Use the actual selected API port.
10. **Runtime config:** frontend/public/runtime-config.json. All API clients use the centralized AppConfigService.
11. **Pages:** source GitHub Actions; run Deploy GitHub Pages; site path is https://sarvesh1barve.github.io/MySociety/.
12. **GitHub variable:** API_BASE_URL is the public HTTPS tunnel origin. No backend secrets belong in frontend variables.
13. **Local credentials:** owner login owner@mysociety.local; private password and recovery-code handover in ignored .local/SUPER_ADMIN_ACCESS.md. All nine known demo/test accounts were removed. New residents self-register and request approval; see SUPER_ADMIN_GUIDE.md.
14. **Routes:** the complete hash-route list is in README. Core pages include dashboard, societies, visitors, parcels, complaints, notices, meetings, tasks, documents, residents, reports, profile and diagnostics.
15. **Multi-society:** global identities + per-society memberships and roles; every business entity is scoped and every request validates tenant access.
16. **Switching:** one active membership auto-selects; multiple memberships show a selection screen. The header/profile switcher clears old context, validates the new membership and reloads permissions/data.
17. **RBAC:** role definitions contain permission sets; memberships can contain multiple roles. Record privacy adds resident-flat, complaint, notice, meeting and document restrictions. SUPER_ADMIN is separate.
18. **Tests:** 23 backend integration tests, 18 frontend service/guard tests and 17 application browser tests pass. PWA and deployment verification details are in VERIFICATION.md. Mutating browser tests use only a dedicated database/API.
19. **Builds:** Maven verify and Angular production build succeed. GitHub workflows reproduce backend/frontend checks and Pages deployment.
20. **Limits:** see IMPLEMENTATION.md for availability, pagination, translations, recovery, upload scanning and deferred modules. This MVP has not received an independent security/load audit.
21. **Next steps:** review the preserved owner-edited society/flat configuration, invite real residents, pilot the gate flows on phones/tablets, and regularly verify backups. Scale hosting/pagination as adoption requires.

The UI displays **All rights reserved by Sarvesh Barve** on login, the application footer and printable minutes.

The follow-up release adds six per-society themes, login email/mobile changes, one-time password recovery codes for every account role and Super Admin-assisted recovery for non-owner accounts. Full owner setup and operating steps: [SUPER_ADMIN_GUIDE.md](SUPER_ADMIN_GUIDE.md). Exact owner credentials and initial recovery codes are in the ignored local handover file, never in GitHub.
