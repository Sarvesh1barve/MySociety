# MVP implementation notes

## Delivered scope

The repository implements the first MVP across authentication, global identity, platform administration, dynamic branding, societies, memberships, role/permission management, building hierarchy, residents/flat relationships, role-sensitive dashboards, visitors/expected visitors, approvals/entry/exit, parcels, complaints/history, notices/acknowledgements, meetings/agenda/attendance/decisions/minutes/actions, document versions, notifications, audits, emergency contacts, search, reports, profile and diagnostics.

Angular uses standalone routed components, reactive record forms, a central runtime configuration service, HttpClient/interceptors, guards, SCSS, hash routing and a real service worker. Reusable forms/tables avoid repeated CRUD implementations; meeting and complaint workflows have specialized editors.

The footer ownership statement is fixed independently of editable platform branding: **All rights reserved by Sarvesh Barve**.

## Security/operational choices

- No real credentials, uploads, PostgreSQL data, dumps or ngrok tokens in source control.
- Local API port 8081 chosen because 8080 was occupied. Local project PostgreSQL on 5433 because 5432 was occupied.
- Real PostgreSQL integration tests validate migrations and Hibernate schema compatibility.
- Dev credentials are random environment values; demo seeding is conditional on the dev profile and an empty societies table.
- No public static file server for uploads; protected downloads only.
- Approved minutes and published notices are immutable. Corrections use a new record.
- No hard deletion APIs for historical business records.
- Native browser printing provides a practical PDF export for approved minutes without a server PDF dependency.
- Notification and gate list polling is 45 seconds, not per-second polling.

## Current limits

This is an MVP, not an externally audited or load-tested commercial service.

- A personal laptop and free tunnel do not provide continuous availability, high availability or backups by themselves.
- Lists use client-side pagination over tenant-filtered API results. Add database-level pagination/query search before onboarding large societies or years of history.
- Role definitions are platform-wide, with per-society assignment. Per-society custom role definitions are a later enhancement.
- English is complete; Marathi/Hindi cover common navigation and actions with English fallback. Full translation review remains future work.
- In-app notifications only; no push/SMS/email dispatch. Generic notice notifications may reach members outside a targeted audience, but content and records remain protected.
- Files receive type/size/signature/path validation, but no antivirus engine or content-disarm pipeline.
- Password reset/account recovery, invitation delivery, MFA, refresh tokens and persistent cross-tab login are not included. An administrator creates accounts and securely communicates initial passwords.
- Published notice corrections and approved MOM amendments use new records; a formal amendment/version comparison workflow is not implemented.
- Complaint attachments use already uploaded authorized document IDs. The initial default resident role does not include DOCUMENT_UPLOAD; a platform administrator can grant that permission if resident uploads are desired.
- Building/wing/floor creation is supported; flat/resident/contact/society edits and occupancy-ending operations are provided. Renaming/deactivating hierarchy nodes is not exposed in this first UI.
- Search is basic case-insensitive matching, and CSV reports are operational snapshots, not financial reporting.
- Rate limits are in-memory and direct-peer based; a tunnel can aggregate multiple users behind one address.
- Finance, staff attendance, vehicle registers, incidents, payments, vendor management, amenities and the other future modules are not placeholder screens.

## Recommended next steps

Pilot with one society using a clean real-data database and individual staff accounts. Test the full gate workflow with real phones/tablets, restore a backup, and review role permissions with the committee. After feedback, prioritize server pagination, account recovery, full translations, invitation delivery and safer public upload processing. Move the backend/database to managed infrastructure when availability requirements justify it.

## Verification evidence

Commands and outcomes are recorded in [VERIFICATION.md](VERIFICATION.md). Test fixtures use generated temporary credentials and an isolated database. Screenshots remain in the ignored local workspace rather than publishing resident data.
