# API guide

Prefix: /api/v1. JSON request bodies. Protected requests use:

```http
Authorization: Bearer <access-token>
X-Society-Id: <active-society-uuid>
```

The society header is omitted for global auth/platform endpoints. A successful context activation returns society details, permissions, roles and associated flat IDs. It does not grant access to arbitrary headers; every business request is checked again.

| Area | Endpoints |
| --- | --- |
| Auth | POST /auth/login; GET/PATCH /auth/me; POST /auth/change-password |
| Membership | GET /memberships; POST /memberships/{id}/activate-context; GET /context |
| Health/branding | GET /system/health; GET /application/config |
| Platform | GET/POST /platform/societies; PUT /platform/societies/{id}; GET/POST /platform/users; PATCH /platform/users/{id} |
| Platform configuration | PUT /platform/branding; GET /platform/roles; PUT /platform/roles/{id}; GET /platform/audit; GET /system/diagnostics |
| Society | GET/PUT /society |
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

## Errors

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
