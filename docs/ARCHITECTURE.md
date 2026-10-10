# Architecture and security

## Model and modules

Java 21 / Spring Boot 3.5 modular monolith. Each domain has separate entities and API controllers. Shared services implement authentication, membership/permission checks, visibility rules, persistence projections, file storage, notifications and append-only audit writes. JPA/Hibernate provides persistence; Spring Data JPA handles login identity queries. Flyway owns schema changes.

Controllers operate within transactions, so a business mutation, its notification and audit record commit together. UUID identities and optimistic versions reject concurrent updates instead of silently overwriting state. API projections explicitly omit password hashes, storage keys and token-revocation counters.

- Account: global login identity. SUPER_ADMIN is a platform privilege.
- Society: independent name, registration, address, contact, status and timezone.
- Membership: account + society + status + joined/left dates + multiple role codes.
- RoleDefinition: explicit permission catalog. Definitions apply platform-wide; memberships assign roles per society. Society administrators cannot grant permissions they do not themselves hold.
- ResidentProfile: distinct from a login, optionally linked to an account.
- ResidentFlatMapping: resident + flat + relationship + dated occupancy. Start is inclusive; end is exclusive, evaluated in society timezone.
- Tenant entities: an immutable society_id with per-tenant indexes and composite relationship foreign keys.

The platform owner can enter a society explicitly from Societies → Manage society. That is authorized platform administration, not ordinary cross-tenant membership access.

## Request boundary

1. Spring Security validates JWT signature, issuer and expiry.
2. The account is reread from the database to enforce current account status and token version.
3. The requested society must exist. Ordinary users need ACTIVE membership and an ACTIVE/TRIAL society.
4. Resolve permissions from the current membership's role definitions.
5. Check the operation permission and tenant-scoped entity reference.
6. Apply record-level privacy and state-transition checks.
7. Commit mutation, notification and audit together.

A changed URL/body/header cannot grant tenant access. Lists and individual actions both enforce authorization. SUPER_ADMIN never comes from a supplied society role.

## Privacy

Residents can see visitors/parcels only for their current flats. Complaints are visible to the reporter or holders of COMPLAINT_READ_ALL. Unrelated phone numbers and member accounts require resident-management permissions. Meeting visibility includes published AGM/SGM meetings and explicitly invited participants; committee read-all permission is separate. Notices check audience and publication dates. Documents distinguish SOCIETY, COMMITTEE and PRIVATE.

Notification content is deliberately generic where a notice may target only part of the society. Opening the related item always rechecks visibility.

Search and reports use the same privacy predicates. CSV cells beginning with spreadsheet formula characters are escaped.

## Authentication and sessions

BCrypt cost 12; passwords limited to 72 UTF-8 bytes. JWTs last one hour, carry only identity/session metadata, and use a required random HMAC secret. Password changes increment a token version, revoking existing sessions at the next API request. Account suspension and membership/role changes also take effect on subsequent requests.

The browser token is stored only in sessionStorage, not localStorage or a service-worker cache. Logout clears token/context. Switching society increments a context generation and clears old records so delayed responses cannot populate the new dashboard.

Login has IP-window and failed-account throttles. IP throttling uses the direct peer address rather than trusting forwarding headers; multiple users behind one ngrok agent share that conservative network quota. Limits are process-local and reset on restart.

## Files

Files are stored under a configured private directory with UUID names. Extension/MIME/magic signature, size and path are validated. Metadata stores revision and previous-version links. Download requires authentication and authorization, uses attachment disposition and nosniff, and never exposes a local path or raw storage key. Failed transactions discard the newly stored file.

Magic signatures are not antivirus scanning; do not treat user uploads as trusted executable content. A future production hardening step is an asynchronous malware scan/quarantine service.

## State transitions

- Visitor: WAITING_APPROVAL → APPROVED or DENIED; APPROVED/valid EXPECTED → CHECKED_IN → CHECKED_OUT. Duplicate transitions return 409.
- Parcel: WAITING → COLLECTED or RETURNED.
- Complaint: every comment/status/assignee change creates history. Closed/rejected records must be reopened before ordinary updates.
- MOM: DRAFT → REVIEW → APPROVED. Approval requires agenda and attendance and locks the meeting/minutes from ordinary edits. Corrections currently use a new meeting record; amendment versioning is a future extension.
- Actions: OPEN / IN_PROGRESS / BLOCKED / COMPLETED / CANCELLED, with completion timestamp.

## Deployment and storage

Static PWA → ngrok HTTPS → API 8081 → private PostgreSQL 5433 on this machine. Uploads remain local. No Redis, Kafka, cloud DB or microservices are needed.

Runtime API configuration is fetched without caching before Angular starts. Service-worker assets exclude API responses, documents and runtime config. GitHub Pages uses a repository base href and hash routing.

Environment variables provide backend port, datasource, JWT secret, initial owner identity, CORS origins and upload directory. Future migration changes these plus a FileStorageService implementation; no frontend feature rewrite is needed.
