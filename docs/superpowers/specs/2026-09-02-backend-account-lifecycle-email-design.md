# Backend Account Lifecycle and Email Design

## Objective

Complete the public account lifecycle required by the frontend: student registration, email verification and resend, password recovery and reset, and teacher invitation acceptance. Deliver confirmation, recovery, and invitation links reliably through configurable SMTP without storing raw action tokens or coupling database transactions to network I/O.

## Scope

The implementation covers:

- `POST /auth/student-registration`;
- `POST /auth/verify-email`;
- `POST /auth/resend-verification`;
- `POST /auth/forgot-password`;
- `POST /auth/reset-password`;
- `POST /auth/accept-teacher-invite`;
- persistence and lifecycle of single-use action tokens;
- a transactional email outbox with scheduled delivery and retries;
- an SMTP adapter configured for Mailpit locally and environment-provided SMTP in production;
- secure frontend links for the existing Angular public routes.

The administration endpoint that creates a pending teacher and requests the initial invitation belongs to the subsequent administration subsystem. This design provides the invitation-token creation operation that subsystem will call, as well as the public endpoint that consumes the invitation.

## Out of Scope

- administration controllers and screens;
- HTML template engines or user-editable email templates;
- inbound email processing;
- an external queue or message broker;
- delivery analytics beyond operational outbox state;
- changing the existing access-token or refresh-token design;
- automatically authenticating a user after confirmation, reset, or invitation acceptance.

## Architecture

`AuthController` exposes the six public endpoints and delegates to `AccountLifecycleService`. The lifecycle service coordinates users, institutions, password policy, action tokens, session revocation, and email enqueueing inside Spring transactions.

`ActionTokenService` owns generation, hashing, invalidation, expiry checks, and atomic consumption. Callers work with a raw token only while constructing a frontend link; repositories and entities receive only its SHA-256 hash.

`MailOutboxService` persists an immutable delivery request in the same transaction as the user and action-token changes. A scheduled `MailOutboxDispatcher` claims due rows in bounded batches after commit and invokes the `MailSender` port. `SmtpMailSender` implements the port with Spring's mail support. SMTP I/O never occurs inside the account-lifecycle transaction.

Dependencies remain layered:

```text
AuthController
    -> AccountLifecycleService
        -> UserRepository / InstitutionRepository / AuthSessionRepository
        -> ActionTokenService -> ActionTokenRepository
        -> MailOutboxService -> MailOutboxRepository

MailOutboxDispatcher -> MailOutboxRepository -> MailSender -> SmtpMailSender
```

No controller accesses a repository, and no JPA entity is exposed as an API contract.

## Persistence Model

### Action tokens

Add an `action_tokens` table with:

- UUID primary key;
- token type: `EMAIL_VERIFICATION`, `TEACHER_INVITATION`, or `PASSWORD_RESET`;
- optional user foreign key, required for the supported flows;
- unique SHA-256 token hash;
- creation and expiration instants;
- optional consumed instant;
- optional invalidated instant.

Only one usable token may remain for a user and token type. Creating a token invalidates every unconsumed, non-invalidated token of the same type for that user before persisting the replacement. Consumption uses a locking repository query so concurrent requests cannot consume the same token twice.

Raw tokens contain 256 random bits produced by `SecureRandom` and encoded with URL-safe Base64 without padding. Raw values are never persisted or logged.

Default lifetimes follow the backend specification and are configurable:

- email verification: 24 hours;
- teacher invitation: 72 hours;
- password reset: 30 minutes.

### Email outbox

Add a `mail_outbox` table with:

- UUID primary key;
- message type;
- recipient address;
- subject and rendered text/HTML bodies;
- creation and next-attempt instants;
- status: `PENDING`, `SENDING`, `SENT`, or `FAILED`;
- attempt count;
- optional claim instant;
- optional sent instant;
- optional last error summary.

Messages are retained after delivery for operational diagnosis. Sensitive action links are part of the stored message body because delivery must survive process restarts; database access therefore has the same confidentiality requirement as password-reset capability. The application never writes the body or link to logs.

The dispatcher claims a bounded batch with row locking and skip-locked semantics so multiple application instances do not send the same row concurrently. A claim changes the status to `SENDING` and records `claimedAt`. Claims older than a configurable lease are returned to `PENDING`, allowing recovery after a process crash. A successful delivery marks the row `SENT`. A temporary or permanent SMTP exception schedules a later attempt. After five failed attempts, the row becomes `FAILED` and remains available for inspection. Delays increase between attempts and are configurable.

## Account Flows

### Student registration

`POST /auth/student-registration` validates the exact frontend contract: full name, normalized email, password, registration number, and institution ID. It applies the existing password policy, requires an active institution, and rejects a duplicate email with the stable existing conflict contract.

The service creates a `STUDENT` in `PENDING`, hashes the password, creates an email-verification token, and enqueues the confirmation email in one transaction. It returns `202 Accepted` without authenticating the student.

### Email verification

`POST /auth/verify-email` consumes an email-verification token. A valid token marks `emailVerifiedAt`, changes a pending student to `ACTIVE`, and returns `204 No Content`.

Invalid token state maps to:

- unknown or invalidated token: `404 ACTION_TOKEN_NOT_FOUND`;
- expired token: `410 ACTION_TOKEN_EXPIRED`;
- previously consumed token: `409 ACTION_TOKEN_USED`.

The operation is transactional so token consumption and account activation cannot diverge.

### Verification resend

`POST /auth/resend-verification` always returns `202 Accepted`, regardless of whether the email is unknown, already verified, inactive, or otherwise ineligible. For an eligible pending student, it invalidates earlier verification tokens, creates a replacement, and enqueues a new message.

The endpoint must not expose account existence through response status, body, or materially different synchronous work. Rate limiting is applied using the existing attempt/rate-limit configuration pattern.

### Forgot and reset password

`POST /auth/forgot-password` always returns `202 Accepted`. For an eligible teacher or student, it invalidates earlier reset tokens, creates a replacement, and enqueues the recovery message. It does not reveal whether the email exists.

`POST /auth/reset-password` validates the new password, consumes the token, replaces the password hash, and revokes all persisted sessions for the user in one transaction. Success returns `204 No Content` and does not create a new authenticated session. Token errors use the same `404`, `409`, and `410` contracts as verification.

### Teacher invitation acceptance

`POST /auth/accept-teacher-invite` accepts a token and password. The token must belong to a pending teacher. The service validates and hashes the password, marks the email verified, activates the teacher, clears the forced-password-change flag when applicable, consumes the token, and returns `204 No Content`.

The future administration service will create the pending teacher and invoke the invitation-creation operation exposed by `AccountLifecycleService`. Reissuing an invitation invalidates the previous invitation token.

## Email Delivery and Links

The mail port sends three fixed application templates:

- student email confirmation;
- teacher invitation;
- password recovery.

Links use a configurable frontend base URL. The local default is `http://localhost:4200`; production must provide `FRONTEND_URL`. Paths match the Angular router:

- `${FRONTEND_URL}/verificar-email?token=...`;
- `${FRONTEND_URL}/redefinir-senha?token=...`;
- `${FRONTEND_URL}/convite-professor?token=...`.

The local sender is `Conta Certa <no-reply@contacerta.local>`. Production must provide `MAIL_FROM`.

SMTP settings are supplied through environment-backed Spring properties. Local development adds Mailpit to `compose.yaml`, with SMTP exposed to the backend and the Mailpit web interface exposed to the developer. Production startup fails configuration validation when required frontend URL, sender, or SMTP settings are absent.

## Security Configuration

The six new lifecycle endpoints are public at the Spring Security transport layer. Their services still perform all token, account, institution, and state validation.

Security requirements:

- password policy is checked before hashing;
- tokens have 256 bits of entropy and only SHA-256 hashes are stored;
- responses for resend and forgot-password resist email enumeration;
- password reset revokes every active session;
- token consumption is single-use and concurrency-safe;
- no raw token, password, message body, or sensitive link is logged;
- rate limits cover resend and forgot-password, in addition to existing protected flows;
- token-bearing endpoints do not return user data or tokens after success.

## Error Semantics

Existing Problem Details formatting remains authoritative. Validation failures return field errors. Account and institution business conflicts preserve stable codes already used by the backend where available.

Action-token errors are deliberately state-specific because the Angular screens distinguish expired and used links:

- `404 ACTION_TOKEN_NOT_FOUND` for unknown, malformed, wrong-purpose, or invalidated tokens;
- `409 ACTION_TOKEN_USED` for a consumed token;
- `410 ACTION_TOKEN_EXPIRED` for an expired token.

The resend and forgot-password endpoints suppress these distinctions and always return the same accepted response.

## Configuration

Introduce typed configuration properties for:

- `FRONTEND_URL`, locally defaulting to `http://localhost:4200`;
- `MAIL_FROM`, locally defaulting to `Conta Certa <no-reply@contacerta.local>`;
- SMTP host, port, username, password, authentication, and TLS;
- the three action-token lifetimes;
- outbox batch size, polling interval, claim lease, maximum attempts, and retry delays.

Tests override scheduling and SMTP where required. Production-required values are validated without making local tests depend on Mailpit.

## Testing Strategy

Implementation is test-driven and adds regression coverage at the narrowest effective layer:

- unit tests for token generation, URL-safe encoding, hashing, expiration, invalidation, and state-to-error mapping;
- service tests for active/inactive institutions, duplicate email, confirmation, neutral resend, neutral recovery, password reset, session revocation, and invitation acceptance;
- persistence tests proving constraints, scoped queries, and single consumption under competing transactions;
- controller tests for request validation, public access, response statuses, and Problem Details codes;
- outbox tests for enqueueing in the caller transaction, successful delivery, abandoned-claim recovery, retry scheduling, five-attempt exhaustion, and retained failed messages;
- SMTP adapter tests using a mocked `JavaMailSender`;
- migration validation and the complete PostgreSQL-backed Maven verification suite.

Mailpit is a local integration aid and is not a dependency of `./mvnw verify`.

## Acceptance Criteria

- Every public account screen in the Angular frontend has its required backend endpoint.
- A student can register, receive a link, confirm the email, and then authenticate.
- Verification resend and password recovery do not reveal account existence.
- A pending teacher can accept a valid invitation and define a password.
- Password reset consumes a single-use token and immediately revokes all existing sessions.
- Replaced, expired, consumed, and unknown tokens follow the specified stable contracts.
- Token and outbox records commit atomically with the account change that created them.
- SMTP failures do not roll back account operations and are retried up to five times.
- Local Mailpit receives messages through configurable SMTP.
- Secrets and raw tokens do not appear in persistence fields intended for tokens or in application logs.
- The full `./mvnw verify` suite passes with PostgreSQL Testcontainers and without requiring Mailpit.
