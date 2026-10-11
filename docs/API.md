# API guide

Prefix: /api/v1. JSON request bodies. Protected requests use:

```http
Authorization: Bearer <access-token>
X-Society-Id: <active-society-uuid>
```

The society header is omitted for global auth/platform endpoints. A successful context activation returns society details, permissions, roles and associated flat IDs. It does not grant access to arbitrary headers; every business request is checked again.

Recovery: GET /auth/recovery-codes returns only the unused count. POST with currentPassword returns five plaintext codes once and invalidates older codes; never log/store that response in shared clients. POST /auth/forgot-password accepts login, recoveryCode and newPassword, consumes the code atomically and revokes all existing tokens. PUT /auth/login-details accepts email, mobile and currentPassword and revokes existing tokens. POST /auth/admin-reset/{userId} needs a Super Admin session, currentPassword (the owner's), and newPassword (the target user's); it cannot target platform owners. PATCH /society/theme accepts one of FOREST/OCEAN/INDIGO/ROSE/AMBER/SLATE and requires SOCIETY_UPDATE.

| Area | Endpoints |
| --- | --- |
| Auth | POST /auth/login; GET/PATCH /auth/me; POST /auth/change-password; PUT /auth/login-details |
| Recovery | GET/POST /auth/recovery-codes; POST /auth/forgot-password (public); POST /auth/admin-reset/{userId} (platform owner only) |
| Membership | GET /memberships; POST /memberships/{id}/activate-context; GET /context |
| Health/branding | GET /system/health; GET /application/config |
| Platform | GET/POST /platform/societies; PUT /platform/societies/{id}; GET/POST /platform/users; PATCH /platform/users/{id} |
| Platform configuration | PUT /platform/branding; GET /platform/roles; PUT /platform/roles/{id}; GET /platform/audit; GET /system/diagnostics |
| Society | GET/PUT /society; PATCH /society/theme |
| Structure | GET/POST /buildings, /wings, /floors, /flats; PUT /flats/{id} |
| Residents | GET/POST /residents; PUT /residents/{id}; GET/POST /resident-flat-mappings; PATCH /resident-flat-mappings/{id}/end |
| Membership administration | GET/POST /society-users; POST /society-memberships; PUT /society-memberships/{id} |
| Visitors | GET/POST /visitors; GET /visitors/{id}; POST /visitors/{id}/approve, /deny, /check-in, /check-out, /cancel |
| Parcels | GET/POST /parcels; POST /parcels/{id}/collect, /return |
| Complaints | GET/POST /complaints; PATCH /complaints/{id}; GET /complaints/{id}/history; POST /complaints/{id}/comments, /reopen |
| Notices | GET/POST /notices; PUT /notices/{id}; POST /notices/{id}/acknowledge |
| Meetings | GET/POST /meetings; GET/PUT /meetings/{id}; PUT /meetings/{id}/mom; POST /meetings/{id}/approve-mom |
| Actions | GET/POST /tasks; PATCH /tasks/{id} |
| Documents | GET /documents; POST /documents (multipart file, visibility, optional previousVersionId); GET /documents/{id}/download |
| Notifications | GET /notifications; POST /notifications/{id}/read |
| Emergency | GET/POST /emergency-contacts; PUT /emergency-contacts/{id} |
| Overview | GET /dashboard; GET /search?q=...; GET /audit |
| Reports | GET /reports/{report}; GET /reports/{report}/csv |

Report values: flats, residents, visitors-today, visitors-inside, complaints-open, actions-pending, parcels-waiting.

## Walk-in example

```json
{
  "visitorName": "Guest name",
  "mobileNumber": "9000000000",
  "visitorType": "GUEST",
  "flatId": "<authorized-flat-uuid>",
  "purpose": "Family visit",
  "expected": false
}
```

Expected visitors also set expected=true and ISO UTC expectedFrom/expectedUntil timestamps. A resident must be linked to the selected flat.

## Meeting minutes

PUT /meetings/{id}/mom takes agendaJson and attendanceJson as serialized arrays, resolutions text and momStatus DRAFT or REVIEW. This explicit serialized representation is also persisted for immutable approved meeting snapshots.

Agenda items: id, sequenceNumber, title, description, discussion, decision.
Attendance entries: userId, status PRESENT/ABSENT/LEAVE_OF_ABSENCE.

The Angular editor constructs these fields; ordinary users do not need to enter JSON. The task form provides a linked-agenda picker after selecting a meeting.

## Self-signup and society approval

All paths below use the `/api/v1` prefix. `POST /auth/signup` is public and accepts only `name`, `email`, `mobile`, `password`, `confirmPassword`. It creates an active global account without a society membership or privileged role. Email/mobile are normalized and unique; passwords use BCrypt and must be at least 12 characters and at most 72 UTF-8 bytes.

Authenticated users use `GET /my-membership-requests` and `POST /my-membership-requests` with `societyCode` and `flatHint`. A known active/trial society code is required; no public society directory is exposed. Requests alone grant no tenant access.

Administrators with `MEMBERSHIP_MANAGE` use `GET /membership-requests` and `POST /membership-requests/{id}/review`, both with `X-Society-Id`. Review accepts `approve`, optional `note`, and, for approval, a verified same-society `flatId` and `relationship` (`OWNER`, `TENANT`, `FAMILY_MEMBER`). Approval additionally requires `RESIDENT_UPDATE` and sufficient permissions to grant the resulting safe resident role. Membership, resident profile and flat mapping are created atomically. Other privileged roles use the existing authorized membership administration flow.

`PUT /platform/users/{id}` edits a non-owner account's name/email/mobile. `PATCH /platform/users/{id}` changes its status. Both revoke old sessions. Owners use their own authenticated profile/account-security endpoints.

## Error response

```json
{
  "timestamp": "2026-10-10T00:00:00Z",
  "status": 403,
  "code": "FORBIDDEN",
  "message": "You do not have access to this resource.",
  "fieldErrors": {}
}
```

400 invalid input; 401 missing/expired/revoked token; 403 no permission; 404 missing resource; 409 duplicate or invalid transition; 413 oversized upload; 429 sign-in throttled. Unexpected errors do not include stack traces. X-Correlation-Id identifies the request without exposing secrets.
