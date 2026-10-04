# Healthcare-HMS — API Standards

Base URL: `/api/v1`. JSON over HTTPS. Documented with OpenAPI (dev; protected or disabled in production).

## 1. Resource Naming
- Plural nouns, kebab-case, hierarchical only when ownership is real:
  `/patients`, `/patients/{id}/allergies`, `/appointments`, `/visits/{id}/notes`.
- Actions that are not CRUD use sub-resources or verbs sparingly: `POST /visits/{id}/finalize`, `POST /appointments/{id}/cancel`.
- External IDs are UUIDs; internal numeric keys are never exposed.

## 2. Methods & Status Codes
| Method | Use | Success |
|---|---|---|
| GET | Read | 200 |
| POST | Create / action | 201 (+`Location`) or 200 |
| PUT | Full replace | 200 |
| PATCH | Partial update | 200 |
| DELETE | Remove/archive | 204 |

| Code | Meaning |
|---|---|
| 400 | Malformed request |
| 401 | Not authenticated / expired token |
| 403 | Authenticated, lacks permission |
| 404 | Not found **or belongs to another tenant** |
| 409 | Conflict (duplicate, slot taken, stale version) |
| 422 | Validation failed |
| 429 | Rate limit exceeded (with `Retry-After`) |
| 500 | Unexpected server error |

## 3. Envelopes
**Success**
```json
{ "success": true, "data": { }, "timestamp": "2026-01-01T10:00:00Z", "traceId": "…" }
```
**Paginated**
```json
{ "success": true, "data": [ ], "meta": { "page": 0, "size": 20, "totalElements": 134, "totalPages": 7 }, "traceId": "…" }
```
**Error**
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "One or more fields are invalid.",
    "fields": [ { "field": "phone", "message": "Phone number is required." } ]
  },
  "traceId": "…"
}
```

### Standard Error Codes
`VALIDATION_FAILED`, `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `ACCESS_DENIED`, `NOT_FOUND`, `DUPLICATE_RESOURCE`, `RESOURCE_IN_USE`, `SLOT_UNAVAILABLE`, `VERSION_CONFLICT`, `RECORD_FINALIZED`, `RATE_LIMITED`, `FILE_TOO_LARGE`, `FILE_TYPE_NOT_ALLOWED`, `INTERNAL_ERROR`.

`RESOURCE_IN_USE` (added at P6.2, decision D5): 409 for a resource that exists and is valid to ask about but cannot be removed while it is referenced — today, a role still assigned to accounts.

## 4. Pagination, Sorting, Filtering, Search
- `?page=0&size=20` (default 20, max 100).
- `?sort=lastName,asc` — sort fields allow-listed per endpoint.
- Filters as explicit query params (`status=`, `doctorId=`, `from=`, `to=`).
- Free text via `q=`; minimum length enforced.
- No endpoint may return an unbounded collection.

## 5. Request Rules
- DTO validation with Jakarta Validation; unknown properties rejected.
- Dates in ISO-8601 UTC; date-only fields as `YYYY-MM-DD`.
- Money as decimal string plus currency code.
- Never accept `tenantId`, `createdBy`, `role`, or other server-controlled fields from the client.

## 6. Response Rules
- Include display labels alongside IDs (`patientId` + `patientName`, `doctorId` + `doctorName`).
- Field-level masking applied by permission; masked fields are omitted.
- No entities, stack traces, SQL or internal class names in responses.

## 7. Authentication & Authorization
- `Authorization: Bearer <access token>` for API calls.
- Refresh/logout use the HttpOnly cookie plus CSRF protection.
- Every endpoint has an explicit permission; the default is deny.
- Foreign-tenant resources return 404.

## 8. Idempotency & Concurrency
- `Idempotency-Key` header supported on `POST` for appointments, invoices, payments; replays return the original result.
- Mutable resources return `version`; updates send `If-Match`/`version`; mismatch → 409 `VERSION_CONFLICT`.

## 9. Rate Limiting
- Anonymous and auth endpoints limited per IP and per account; responses carry `Retry-After`.
- Authenticated endpoints limited per user/tenant with generous defaults; heavy endpoints (export, search) stricter.

## 10. Endpoint Design Checklist
For every endpoint record:
| Question | Required answer |
|---|---|
| Authentication required? | Yes unless explicitly public |
| Permission | Named permission |
| Tenant restriction | Yes for all tenant data |
| Resource policy | Which policy/bean |
| Audit event | Yes/No and which |
| Pagination | Required for lists |
| Validation | DTO constraints defined |
| Idempotency | Needed for duplicate-sensitive creates |

## 11. Initial Endpoint Map (indicative)
| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register-hospital`, `/auth/verify-email`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/forgot-password`, `/auth/reset-password` |
| Organization | `GET/PUT /hospital`, `/departments` (CRUD) |
| Staff | `/staff`, `/invitations`, `POST /invitations/accept`, `/roles`, `/permissions` |
| Patients | `/patients`, `/patients/{id}/allergies`, `/patients/{id}/assignments`, `/patients/{id}/timeline` |
| Appointments | `/appointments`, `/queue`, `/doctors/{id}/availability` |
| Clinical | `/visits`, `/visits/{id}/vitals|notes|diagnoses|recommendations|orders`, `POST /visits/{id}/finalize`, `/addenda` |
| Prescriptions | `/prescriptions`, `/prescriptions/{id}/versions`, `/medicines` |
| Lab/Imaging | `/orders`, `/lab-results`, `/imaging-studies` |
| Documents | `POST /documents`, `GET /documents/{id}/download-url` |
| Billing | `/invoices`, `/invoices/{id}/payments` |
| Notifications | `/notifications`, `PATCH /notifications/{id}/read` |
| Audit | `GET /audit-logs` |
| Search | `GET /search?q=` |
