# My Society release report — 11 October 2026

## Data cleanup

The live `mysociety` database now contains only the existing real Super Admin account. Removed known seeded logins: `secretary@demo.local`, `watchman@demo.local`, `resident@demo.local`, `resident2@demo.local`, plus five precisely identified browser-test accounts. The repeatable `scripts/cleanup-known-demo.sql` uses an exact ID/email allowlist, refuses owner deletion and deletes dependent dummy records transactionally. Unknown users are not mass-deleted.

Removed dependent dummy memberships, three dummy resident profiles/mappings, visitors, complaints/history, notices, a meeting/task, a parcel, notifications, test recovery codes and related audit events. Before cleanup, database and uploads were backed up in the ignored `.local/backups/20261011-141742-11a9f6b8` directory. The cleanup is recoverable from that backup; it is not a startup migration.

Preserved the real owner, both existing society configurations (including owner-renamed Jay Bharti), seven flats and the owner-edited resident profile/flat relationship. That profile is detached from the old demo login and needs a real login linked when appropriate. The remaining society named My Society Demo CHS is configuration, not a remaining dummy user; the owner should review it before real onboarding.

Demo creation was removed from the production bootstrap and development profile. Fixtures now exist only in test source and isolated test databases, never in the production JAR. Normal startup initializes required role/settings metadata and an owner only when no owner exists. The initial password comes from a private environment variable, not source.

## Accounts and membership

- Sign in: `/#/login`; create account: `/#/signup`; recovery: `/#/forgot-password`.
- Signup asks for full name, mobile, email, password and confirmation. It creates no society permissions.
- The new user signs in, enters the society code and flat details and sees Pending Approval.
- A society administrator opens Join requests, verifies identity/occupancy, chooses a real flat and relationship, and approves. Approval creates membership, resident profile and flat mapping together.
- One available society opens automatically. Multiple societies present a chooser. No membership presents clear onboarding, not an empty dashboard. Suspended memberships do not grant access.
- Privileged roles can only be assigned by authorized administrators. Signup cannot choose them.

## User-list root causes and fixes

Global accounts, society memberships and resident contact profiles are distinct records. Previously a newly created global user could look ready despite no society access; editing a resident's contact information did not rename the login. Old search/status/page filters could also hide newly saved records, and membership linking changed society context without reliably refreshing the platform list.

The API now returns explicit membership/access summaries and resident/login/flat relationships. The UI identifies Active, Pending Approval, Suspended and No Society Assigned. Saves reset stale filters/page and reload server records. Linking uses an explicit target society header without changing the active context, then refreshes summaries. Global edit/suspend and society membership/role changes are tested; account changes invalidate old tokens.

## Usability

Clear Sign In/Create Account/Forgot password links; account-ready and pending-access screens; understandable errors/empty states; resident quick actions; five large watchman gate actions; searchable flat selection; role checkboxes; mobile record cards and sticky form actions; required/validation/loading/success states; unique accessible form IDs; no misleading Super Admin role on an unassigned normal account. Six per-society colour themes are permission-controlled. The fixed rights statement remains visible.

## Super Admin

The existing owner login was verified after cleanup and restart. Initial identifier: `owner@mysociety.local`. The private password and five recovery codes remain in `.local/SUPER_ADMIN_ACCESS.md`, not Git. Read [the full owner guide](SUPER_ADMIN_GUIDE.md) for setup, name/email/mobile/password changes, recovery, themes, roles, backups and service startup. Environment bootstrap values do not reset an existing account. Save new credentials privately after changing them.

## Verified hosting configuration

| Item | Actual configuration |
| --- | --- |
| Backend hosting | **LOCAL**, this Windows laptop; not a cloud-hosted backend |
| Backend host / port | `http://localhost:8081`; configurable with `SERVER_PORT` |
| Database | PostgreSQL 17.6, `mysociety`, loopback port **5433** |
| Frontend hosting | GitHub Pages: `https://sarvesh1barve.github.io/MySociety/` |
| ngrok | User-started tunnel verified running, forwarding only the API |
| Public backend / API origin | `https://3533-103-48-57-100.ngrok-free.app` |
| GitHub `API_BASE_URL` | Same HTTPS origin; deployment writes centralized `runtime-config.json` |
| Local source runtime URL | `http://localhost:8081`; all frontend API calls use central configuration |

The tunnel inspector and public health endpoint were checked, not inferred from source. Existing unrelated services were not stopped. PostgreSQL is not forwarded. The API is running without the dev profile; Swagger is disabled. The final chat handover includes the actual commit and Pages workflow outcome, followed by live deployed verification.

## Security and testing

BCrypt passwords; private signing secret; JWT expiry/signature/token-version checks; recovery code hashes and atomic one-time use; account locks; protected owner reset; no privileged signup; server-enforced permissions/tenant ownership; same-society flat checks; exact CORS origins; authentication/signup throttling; parameterized database access; input validation; private upload storage/type/size/path restrictions; generic internal errors; SQL exception logging disabled to avoid accidental personal-data details. No API data or runtime configuration is stored in PWA caches.

See [verification details](VERIFICATION.md) for 23 backend tests, 18 frontend tests, 17 passing application browser tests, production PWA checks and the production build. Live checks are read-only; mutating checks use isolated databases so test users cannot reappear in real usage.

## Remaining operational considerations

The laptop, PostgreSQL, backend and tunnel must stay running. A changed ngrok URL requires updating the repository variable and redeploying Pages. This is not always-on cloud hosting. Email/SMS verification is not configured; administrators must independently verify resident identity and occupancy. Recovery uses saved one-time codes, as requested. The single-instance in-memory rate limiter is not a distributed limiter. Backups should be protected off-device and regularly restore-tested. Existing owner-edited configuration has deliberately not been deleted blindly. No independent penetration test or malware-scanning service was performed.
