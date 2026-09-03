# Backend Admin Institutions and Teachers Design

## Objective

Implement the administrative backend required by the existing Angular application for managing institutions and teachers, including the admin dashboard, teacher invitations, account activation and deactivation, and administrative password-reset delivery.

Financial tips, student and teacher dashboards, and other remaining backend endpoints are outside this delivery.

## Scope

The implementation covers:

- `GET /admin/dashboard`;
- paginated institution CRUD through `/admin/institutions`;
- institution activation and deactivation;
- paginated teacher management through `/admin/teachers`;
- teacher activation and deactivation;
- administrative teacher password-reset delivery;
- PostgreSQL-backed filtering, counting, ownership/history checks, and optimistic locking;
- authorization and API contract tests for the complete administrative flow.

## Architecture

The subsystem follows the existing `controller -> service -> repository` layering. Controllers validate transport concerns and map HTTP responses. Services own account transitions, authorization-sensitive business rules, transaction boundaries, and collaboration with the account-lifecycle subsystem. Repositories own filtered queries, aggregate counts, and history checks.

Responsibilities remain separated by aggregate:

- `AdminInstitutionController` delegates to `AdminInstitutionService`;
- `AdminTeacherController` delegates to `AdminTeacherService`;
- `AdminDashboardController` delegates to `AdminDashboardService`;
- institution and teacher specifications build allow-listed filters;
- `AccountLifecycleService` remains the single entry point for invitation and recovery-token creation;
- existing session and refresh-token repositories perform bulk revocation.

No controller accesses a repository and no JPA entity is exposed by the API.

## Security

Every `/admin/**` endpoint requires an authenticated `ADMIN`. Spring Security rejects anonymous callers with `401` and authenticated teachers or students with `403`.

Services independently validate targeted resource types and legal state transitions. A UUID that does not identify an institution or a teacher returns `404`, including when the UUID belongs to a user with a different role. This avoids exposing unrelated account details.

## Admin Dashboard

`GET /admin/dashboard` returns only aggregate counts:

```json
{
  "institutions": { "total": 10, "active": 8, "inactive": 2 },
  "teachers": { "total": 25, "pending": 3, "active": 20, "inactive": 2 }
}
```

Counts are computed in PostgreSQL with focused aggregate queries. The admin dashboard never includes rooms, content, attempts, reports, or ranking data.

## Institution Management

### Listing and detail

`GET /admin/institutions` is paginated and accepts:

- `search`: case-insensitive name match or CNPJ digit match;
- `active`: optional boolean;
- `page`, `size`, and allow-listed `sort` fields.

The response uses the shared page envelope. Detail and list items expose `id`, `name`, normalized `cnpj`, `contactEmail`, `contactPhone`, `active`, `version`, timestamps, and optional teacher/student counts.

### Creation and editing

`POST /admin/institutions` accepts `name`, `cnpj`, `contactEmail`, and `contactPhone`, returns `201 Created`, and supplies a `Location` header. CNPJ is normalized to exactly 14 digits, contact email is trimmed and lowercased, and phone must be valid E.164. Duplicate CNPJ returns `409 CNPJ_ALREADY_EXISTS`.

`PATCH /admin/institutions/{institutionId}` accepts partial mutable fields plus mandatory `version`. A stale version returns `409 VERSION_CONFLICT`; the server never silently overwrites a concurrent update.

### State and deletion

Activation and deactivation are idempotent. Deactivation prevents new teachers and rooms while preserving existing users, rooms, and history.

Deletion is allowed only when the institution has no users, rooms, memberships, or other historical references. Any history returns `409 INSTITUTION_HAS_HISTORY`, directing the client toward deactivation. Successful deletion returns `204 No Content`.

## Teacher Management

### Listing and detail

`GET /admin/teachers` is paginated and accepts:

- `search`: case-insensitive full name, email, or registration-number match;
- `status`: optional `PENDING`, `ACTIVE`, or `INACTIVE`;
- `institutionId`: optional institution filter;
- `page`, `size`, and allow-listed `sort` fields.

Responses expose `id`, `fullName`, immutable `email`, `registrationNumber`, institution summary, status, email-verification state, `version`, timestamps, and `lastLoginAt` when available.

### Creation and invitation

`POST /admin/teachers` accepts full name, email, registration number, and institution ID. It never accepts a password. The institution must be active and the email must be unique case-insensitively.

Creation persists a `TEACHER/PENDING` without a password and calls `AccountLifecycleService.inviteTeacher` in the same transaction. The invitation token and outbox message therefore commit atomically with the user. The endpoint returns `201 Created` with `Location`.

### Editing

`PATCH /admin/teachers/{teacherId}` accepts partial full name, registration number, and institution ID plus mandatory `version`. Email, role, password, and status are not editable through this operation.

An institution change requires an active destination institution and is allowed only while the teacher owns no rooms and has no related history. Otherwise it returns `409 TEACHER_INSTITUTION_CHANGE_BLOCKED`. This preserves the invariant that teacher, rooms, and enrolled students belong to the same institution.

### Account transitions

Teacher activation accepts only `INACTIVE`. Activating `PENDING` returns `409 TEACHER_INVITATION_REQUIRED`, because a pending teacher must accept the invitation and define a password first. Re-activating an already active teacher is idempotent.

Deactivation changes an active or pending teacher to `INACTIVE` and revokes every auth session and refresh token for that teacher in the same transaction. Repeating deactivation is idempotent.

`POST /admin/teachers/{teacherId}/password-reset` returns `202 Accepted`. It creates a password-reset token and enqueues the existing recovery email without exposing or changing the password. The endpoint is valid for active teachers; other states return a stable conflict response.

## Validation and Errors

The subsystem uses the existing RFC 9457 Problem Details format with `code`, `timestamp`, `traceId`, and validation field errors.

Stable business errors include:

- `CNPJ_ALREADY_EXISTS`;
- `EMAIL_ALREADY_EXISTS`;
- `INSTITUTION_NOT_FOUND`;
- `INSTITUTION_INACTIVE`;
- `INSTITUTION_HAS_HISTORY`;
- `TEACHER_NOT_FOUND`;
- `TEACHER_INVITATION_REQUIRED`;
- `TEACHER_INSTITUTION_CHANGE_BLOCKED`;
- `TEACHER_PASSWORD_RESET_UNAVAILABLE`;
- `VERSION_CONFLICT`.

Resource mutations use the entity `@Version` value and explicitly compare the request version before applying changes, producing the stable version conflict rather than leaking persistence exceptions.

## Persistence and Queries

No new tables are required. Existing `institutions`, `users`, `rooms`, auth-session, refresh-token, action-token, and outbox tables contain the required state.

Entities receive explicit domain methods for updating allowed fields and changing legal states. Repositories add:

- JPA Specifications for paginated institution and teacher filtering;
- aggregate count queries for dashboard cards;
- role-scoped teacher lookup;
- existence queries for institution and teacher history;
- last-login projection from authentication sessions where practical.

Queries avoid loading complete JPA graphs. Pagination is zero-based, defaults to 20, and caps size at 100 under the existing API convention.

## Testing Strategy

Implementation is test-driven and divided into atomic, independently reviewable commits:

1. institution queries and administrative lifecycle;
2. teacher queries, creation, invitation, and editing;
3. teacher activation, deactivation, session revocation, and password reset;
4. dashboard aggregates and end-to-end authorization matrix;
5. full integration verification and contract hardening.

Tests include:

- repository integration tests against PostgreSQL Testcontainers for filtering, pagination, counts, and history checks;
- service tests for normalization, active-institution requirements, legal transitions, version conflicts, invitation enqueueing, and session revocation;
- controller tests for request validation, pagination, `Location`, status codes, and Problem Details;
- security tests for the `401`/`403`/success matrix;
- integration tests covering teacher creation through outbox invitation, deactivation through token revocation, and blocked institution changes;
- full `./mvnw verify` and `git diff --check` before completion.

## Acceptance Criteria

- The existing Angular institution, teacher, and admin-dashboard services can use the backend without mock-specific behavior.
- Admin is the only role that can access these endpoints.
- Admin never chooses, reads, or resets a teacher password directly.
- A created teacher remains pending until accepting a valid invitation.
- Pending teachers cannot be administratively activated.
- Deactivation immediately invalidates every active session and refresh token.
- Institution deactivation preserves existing access but blocks new relationships.
- Institutions with history cannot be deleted.
- Teachers with rooms or history cannot move between institutions.
- Every editable resource enforces optimistic locking with a stable `409` response.
- List endpoints are filtered and paginated in PostgreSQL.
- The complete Maven verification suite passes against PostgreSQL.
