# Backend Foundation, Authentication, and Institutions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the first independently usable backend slice: shared API conventions, persisted identities and institutions, JWT access/refresh sessions, student registration and email verification, teacher invitations, password recovery, and global-admin management.

**Architecture:** Extend the existing Spring Boot monolith with feature packages under `com.ifsc.contacerta`, while preserving the repository rule `controller -> service -> repository`. Use PostgreSQL/Flyway as the source of truth, Spring Security with RSA-signed access JWTs, opaque rotating refresh tokens stored only as hashes, and an outbox-backed mail port. Controllers expose DTOs and Problem Details; entities never cross the HTTP boundary.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Spring Security, Spring Data JPA, Bean Validation, PostgreSQL, Flyway, Maven Wrapper, JUnit 5, Mockito, Testcontainers PostgreSQL, Spring Mail.

## Global Constraints

- Follow `backend/AGENTS.md`: technical layers, no controller-to-repository access, no JPA entities as API contracts, Flyway-only schema changes, Java 21, Maven Wrapper.
- API base is `/api/v1`; JSON fields and resource names are English `camelCase`.
- IDs are UUID; timestamps are ISO 8601 UTC; dates without time are `YYYY-MM-DD`.
- Access JWT lifetime is 15 minutes; rotating refresh lifetime is 7 days.
- Passwords accept 8–72 characters and require at least one letter and one number.
- Action-token defaults: email verification 24 hours, teacher invitation 72 hours, password reset 30 minutes.
- Errors use `application/problem+json` with `code`, `timestamp`, `traceId`, and optional `fieldErrors`.
- List defaults: `page=0`, `size=20`, maximum `size=100`.
- Every production behavior begins with a failing test and ends with the focused test plus `./mvnw.cmd test` passing.
- Do not implement rooms, lessons, attempts, media, gamification, or reports in this plan.

## File Structure

```text
backend/src/main/java/com/ifsc/contacerta/
├── auth/
│   ├── controller/AuthController.java
│   ├── dto/AuthDtos.java
│   ├── entity/AuthSession.java
│   ├── entity/ActionToken.java
│   ├── model/ActionTokenType.java
│   ├── repository/AuthSessionRepository.java
│   ├── repository/ActionTokenRepository.java
│   ├── security/CurrentUser.java
│   ├── security/JwtService.java
│   ├── security/SecurityConfig.java
│   └── service/AuthService.java
├── institution/
│   ├── controller/AdminInstitutionController.java
│   ├── controller/InstitutionOptionController.java
│   ├── dto/InstitutionDtos.java
│   ├── entity/Institution.java
│   ├── repository/InstitutionRepository.java
│   └── service/InstitutionService.java
├── mail/
│   ├── entity/MailOutbox.java
│   ├── model/MailMessage.java
│   ├── repository/MailOutboxRepository.java
│   ├── service/MailOutboxService.java
│   └── spi/MailSender.java
├── shared/
│   ├── config/ClockConfig.java
│   ├── error/ApiException.java
│   ├── error/GlobalExceptionHandler.java
│   ├── model/AccountStatus.java
│   ├── model/Role.java
│   └── validation/ValidationPatterns.java
└── user/
    ├── controller/AdminTeacherController.java
    ├── controller/MeController.java
    ├── dto/UserDtos.java
    ├── entity/User.java
    ├── repository/UserRepository.java
    └── service/UserService.java

backend/src/main/resources/db/migration/
├── V1__create_identity_and_institution_tables.sql
└── V2__create_auth_and_mail_tables.sql
```

Tests mirror these packages under `backend/src/test/java/com/ifsc/contacerta/`. Keep each public class in its own file even where the tree groups DTO records into one `*Dtos` utility class.

---

### Task 1: Shared API foundation and test infrastructure

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.properties`
- Create: `backend/src/main/java/com/ifsc/contacerta/shared/model/Role.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/shared/model/AccountStatus.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/shared/error/ApiException.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/shared/error/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/shared/config/ClockConfig.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/shared/error/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `ApiException(HttpStatus status, String code, String detail)` and `ApiException.validation(List<FieldViolation>)` used by later services.
- Produces: injectable UTC `java.time.Clock` bean.
- Produces: enums `Role.ADMIN|TEACHER|STUDENT` and `AccountStatus.PENDING|ACTIVE|INACTIVE`.

- [ ] **Step 1: Add a failing MVC test for Problem Details**

Create a test controller inside `GlobalExceptionHandlerTest` that throws `new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already registered.")`. Assert status `409`, content type `application/problem+json`, and JSON paths `$.code`, `$.detail`, `$.timestamp`, and `$.traceId`.

- [ ] **Step 2: Run the focused test and confirm the missing classes fail compilation**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=GlobalExceptionHandlerTest test
```

Expected: compilation failure naming `ApiException` or `GlobalExceptionHandler`.

- [ ] **Step 3: Add dependencies and minimal shared implementation**

Add Spring Security, OAuth2 resource-server/JOSE, Spring Mail, Testcontainers PostgreSQL/JUnit, and `spring-security-test`. Keep versions managed by the Spring Boot parent/Testcontainers BOM. Implement `ApiException`, a `@RestControllerAdvice` that builds `ProblemDetail`, and validation mapping for `MethodArgumentNotValidException` using field objects shaped as `{ field, message }`.

Configure:

```properties
server.servlet.context-path=/api/v1
app.security.access-token-ttl=PT15M
app.security.refresh-token-ttl=P7D
app.security.email-verification-ttl=PT24H
app.security.teacher-invite-ttl=PT72H
app.security.password-reset-ttl=PT30M
app.frontend-base-url=${FRONTEND_BASE_URL:http://localhost:5173}
```

Provide `Clock.systemUTC()` through `ClockConfig`.

- [ ] **Step 4: Run the focused test and full suite**

Run:

```powershell
.\mvnw.cmd -Dtest=GlobalExceptionHandlerTest test
.\mvnw.cmd test
```

Expected: all tests pass; the error response has the required fields.

- [ ] **Step 5: Commit**

```powershell
git add backend/pom.xml backend/src/main/resources/application.properties backend/src/main/java/com/ifsc/contacerta/shared backend/src/test/java/com/ifsc/contacerta/shared
git commit -m "feat: adiciona fundacao compartilhada da api"
```

---

### Task 2: Institution and user persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__create_identity_and_institution_tables.sql`
- Create: `backend/src/main/java/com/ifsc/contacerta/institution/entity/Institution.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/institution/repository/InstitutionRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/user/entity/User.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/user/repository/UserRepository.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/institution/repository/InstitutionRepositoryTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/user/repository/UserRepositoryTest.java`

**Interfaces:**
- Produces: `InstitutionRepository.findByCnpj(String)` and `findByActiveTrueOrderByNameAsc()`.
- Produces: `UserRepository.findByEmailIgnoreCase(String)`, `existsByEmailIgnoreCase(String)`, and teacher pagination predicates.
- `User` references nullable `Institution`; service rules later require it for teachers/students and forbid it for admin.

- [ ] **Step 1: Write failing repository tests with PostgreSQL Testcontainers**

Test that CNPJ is unique, email uniqueness is case-insensitive, optimistic version increments, inactive institutions are excluded from options, and a `TEACHER` persists with registration number and institution.

- [ ] **Step 2: Run repository tests and observe missing migration/entities**

Run:

```powershell
.\mvnw.cmd -Dtest=InstitutionRepositoryTest,UserRepositoryTest test
```

Expected: failure because the tables and repositories do not exist.

- [ ] **Step 3: Create schema and mappings**

Migration requirements:

```sql
create extension if not exists citext;
create table institutions (
  id uuid primary key,
  name varchar(160) not null,
  cnpj char(14) not null unique,
  contact_email citext not null,
  contact_phone varchar(24) not null,
  active boolean not null default true,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  version bigint not null default 0
);
create table users (
  id uuid primary key,
  role varchar(16) not null,
  status varchar(16) not null,
  full_name varchar(160) not null,
  email citext not null unique,
  password_hash varchar(255),
  registration_number varchar(80),
  institution_id uuid references institutions(id),
  email_verified_at timestamptz,
  must_change_password boolean not null default false,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  version bigint not null default 0,
  constraint ck_user_institution check (
    (role = 'ADMIN' and institution_id is null and registration_number is null)
    or (role in ('TEACHER','STUDENT') and institution_id is not null and registration_number is not null)
  )
);
```

Map UUIDs assigned in constructors, `Instant` timestamps, enums as strings, and `@Version long version`. Normalize CNPJ to digits in the service layer, not entity callbacks.

- [ ] **Step 4: Run repository tests and full suite**

Run:

```powershell
.\mvnw.cmd -Dtest=InstitutionRepositoryTest,UserRepositoryTest test
.\mvnw.cmd test
```

Expected: all tests pass against PostgreSQL; Flyway validates the schema.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/resources/db/migration/V1__create_identity_and_institution_tables.sql backend/src/main/java/com/ifsc/contacerta/institution backend/src/main/java/com/ifsc/contacerta/user/entity backend/src/main/java/com/ifsc/contacerta/user/repository backend/src/test
git commit -m "feat: persiste usuarios e instituicoes"
```

---

### Task 3: Security filter chain, access JWT, sessions, and initial admin

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__create_auth_and_mail_tables.sql`
- Create: `backend/src/main/java/com/ifsc/contacerta/auth/entity/AuthSession.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/auth/entity/ActionToken.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/auth/model/ActionTokenType.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/auth/repository/AuthSessionRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/auth/repository/ActionTokenRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/auth/security/CurrentUser.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/auth/security/JwtService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/auth/security/SecurityConfig.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/auth/service/InitialAdminSeeder.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/auth/security/JwtServiceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/auth/service/InitialAdminSeederTest.java`

**Interfaces:**
- Produces: `JwtService.issueAccessToken(User user, UUID sessionId): IssuedAccessToken`.
- Produces: `CurrentUser(UUID id, Role role, UUID sessionId)` as authenticated principal.
- Produces tables/repositories consumed by `AuthService` in Task 5.

- [ ] **Step 1: Write failing JWT and seeder tests**

JWT test asserts claims `sub`, `role`, `sessionId`, `jti`, `iat`, `exp`, RSA signature validity, and exactly 15 minutes between issued/expiry using a fixed `Clock`. Seeder test asserts an admin is created once from configured email/password and `mustChangePassword=true`, then a second run creates nothing.

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\mvnw.cmd -Dtest=JwtServiceTest,InitialAdminSeederTest test
```

Expected: missing security/session classes.

- [ ] **Step 3: Implement schema and security**

Create `auth_sessions`, `action_tokens`, and `mail_outbox`. Store refresh/action tokens as SHA-256 hashes with unique indexes; include expiry, used/revoked timestamps and session rotation linkage.

Configure routes:

```java
requestMatchers("/auth/**", "/institutions/options").permitAll();
requestMatchers("/admin/**").hasRole("ADMIN");
requestMatchers("/teacher/**").hasRole("TEACHER");
requestMatchers("/student/**").hasRole("STUDENT");
anyRequest().authenticated();
```

Use stateless security, disable CSRF for bearer-token API, configure environment-driven RSA PEM keys, map JWT claims into `CurrentUser`, and return Problem Details for authentication/authorization failures.

Seeder environment keys: `INITIAL_ADMIN_EMAIL`, `INITIAL_ADMIN_PASSWORD`, and `INITIAL_ADMIN_NAME`. Reject startup seeding when one is supplied without the others; skip seeding when all are absent.

- [ ] **Step 4: Run tests and full suite**

Run:

```powershell
.\mvnw.cmd -Dtest=JwtServiceTest,InitialAdminSeederTest test
.\mvnw.cmd test
```

Expected: all pass and repeated seed execution remains idempotent.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/resources/db/migration/V2__create_auth_and_mail_tables.sql backend/src/main/java/com/ifsc/contacerta/auth backend/src/test/java/com/ifsc/contacerta/auth
git commit -m "feat: configura seguranca jwt e sessoes"
```

---

### Task 4: Transactional mail outbox

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/mail/entity/MailOutbox.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mail/model/MailMessage.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mail/repository/MailOutboxRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mail/spi/MailSender.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mail/service/MailOutboxService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mail/service/SmtpMailSender.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/mail/service/MailOutboxServiceTest.java`

**Interfaces:**
- Produces: `MailOutboxService.enqueue(MailMessage message)` for account services.
- Produces: `MailSender.send(MailMessage message)` adapter boundary.
- `MailMessage` fields: recipient, template (`VERIFY_EMAIL`, `TEACHER_INVITE`, `RESET_PASSWORD`), subject, link, and non-secret template variables.

- [ ] **Step 1: Write failing outbox service tests**

Cover enqueue, successful delivery marking `sentAt`, failed delivery incrementing attempts and scheduling exponential retry, and permanent failure after the configured maximum. Assert raw tokens are present only in the outbound link payload and never logged by the service.

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
.\mvnw.cmd -Dtest=MailOutboxServiceTest test
```

Expected: missing mail classes.

- [ ] **Step 3: Implement port, persistence, worker, and SMTP adapter**

Use `@Scheduled` worker with `FOR UPDATE SKIP LOCKED` repository query so multiple instances do not send the same row. Enqueue inside the caller transaction. Construct links using `app.frontend-base-url`; do not include token values in log messages. Configure SMTP exclusively from environment/Spring Mail properties.

- [ ] **Step 4: Run focused and full tests**

```powershell
.\mvnw.cmd -Dtest=MailOutboxServiceTest test
.\mvnw.cmd test
```

Expected: all pass; retry timestamps are deterministic with the injected clock.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/ifsc/contacerta/mail backend/src/test/java/com/ifsc/contacerta/mail
git commit -m "feat: adiciona outbox transacional de emails"
```

---

### Task 5: Login, refresh rotation, logout, and current profile

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/auth/dto/AuthDtos.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/auth/controller/AuthController.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/auth/service/AuthService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/user/dto/UserDtos.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/user/controller/MeController.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/user/service/UserService.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/auth/controller/AuthControllerTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/auth/service/AuthServiceTest.java`

**Interfaces:**
- Produces: `AuthService.login(LoginRequest): TokenResponse`, `refresh(RefreshRequest): TokenResponse`, and `logout(CurrentUser): void`.
- Produces: `UserService.currentUser(UUID): UserResponse`, `updateName(UUID, UpdateMeRequest)`, and `changePassword(UUID, ChangePasswordRequest)`.
- `TokenResponse` exactly matches the frontend spec: access/refresh tokens, `Bearer`, lifetimes in seconds, and user on login.

- [ ] **Step 1: Write failing service and MVC tests**

Cover valid login, invalid credentials with generic `401 INVALID_CREDENTIALS`, pending/inactive account rejection, refresh rotation, refresh reuse revoking the session chain, logout revoking only current session, `/me`, name update with version, and password change revoking all sessions.

- [ ] **Step 2: Run focused tests and observe failure**

```powershell
.\mvnw.cmd -Dtest=AuthServiceTest,AuthControllerTest test
```

Expected: missing services/controllers.

- [ ] **Step 3: Implement authentication services and endpoints**

Use `PasswordEncoder.matches`, generate 256-bit opaque refresh tokens with `SecureRandom`, persist only SHA-256 hash, and rotate within one transaction. A repeated previous token yields `401 REFRESH_TOKEN_REUSED`. Implement:

```text
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET /me
PATCH /me
POST /me/change-password
```

Accept and return the exact payloads in `docs/frontend-integration-spec.md` section 4. Apply `@Valid`; never reveal whether password or email caused login failure.

- [ ] **Step 4: Run focused and full tests**

```powershell
.\mvnw.cmd -Dtest=AuthServiceTest,AuthControllerTest test
.\mvnw.cmd test
```

Expected: all pass, including refresh replay and multi-session behavior.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/ifsc/contacerta/auth backend/src/main/java/com/ifsc/contacerta/user backend/src/test/java/com/ifsc/contacerta/auth
git commit -m "feat: implementa login e rotacao de tokens"
```

---

### Task 6: Student registration, verification, invitations, and password reset

**Files:**
- Modify: `backend/src/main/java/com/ifsc/contacerta/auth/dto/AuthDtos.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/auth/controller/AuthController.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/auth/service/AuthService.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/auth/service/AccountLifecycleServiceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/auth/controller/AccountLifecycleControllerTest.java`

**Interfaces:**
- Adds: `registerStudent`, `verifyEmail`, `resendVerification`, `acceptTeacherInvite`, `forgotPassword`, and `resetPassword` to the auth service/controller.
- Consumes: `MailOutboxService.enqueue`, `ActionTokenRepository`, `InstitutionRepository`, and session revocation from Task 5.

- [ ] **Step 1: Write failing lifecycle tests**

Cover student registration against active institution, duplicate email, inactive institution, email confirmation, expired/used token, resend invalidating prior verification token, teacher invitation acceptance, forgot-password enumeration resistance, reset expiry/use-once, password policy, and session revocation after reset.

- [ ] **Step 2: Run lifecycle tests and verify failure**

```powershell
.\mvnw.cmd -Dtest=AccountLifecycleServiceTest,AccountLifecycleControllerTest test
```

Expected: endpoints/methods are absent.

- [ ] **Step 3: Implement token lifecycle and endpoints**

Implement:

```text
POST /auth/student-registration     -> 202
POST /auth/verify-email             -> 204
POST /auth/resend-verification      -> 202
POST /auth/accept-teacher-invite    -> 204
POST /auth/forgot-password          -> 202 always
POST /auth/reset-password           -> 204
```

Generate 256-bit random action tokens, store SHA-256 only, compare in constant-time-capable library operations, invalidate earlier active tokens of the same type, and mark use atomically. Enqueue e-mail after persisting the token in the same transaction.

- [ ] **Step 4: Run focused and full tests**

```powershell
.\mvnw.cmd -Dtest=AccountLifecycleServiceTest,AccountLifecycleControllerTest test
.\mvnw.cmd test
```

Expected: all pass; public endpoints do not leak account existence.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/ifsc/contacerta/auth backend/src/test/java/com/ifsc/contacerta/auth
git commit -m "feat: implementa ciclo de vida das contas"
```

---

### Task 7: Admin institution management and public options

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/institution/dto/InstitutionDtos.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/institution/service/InstitutionService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/institution/controller/AdminInstitutionController.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/institution/controller/InstitutionOptionController.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/institution/service/InstitutionServiceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/institution/controller/AdminInstitutionControllerTest.java`

**Interfaces:**
- Produces institution create/detail/update/page/option DTOs from frontend spec section 8.2.
- Produces `InstitutionService.requireActive(UUID)` already consumed by registration; move any temporary implementation from Task 6 here without changing its signature.

- [ ] **Step 1: Write failing service/MVC tests**

Cover CNPJ normalization/check digits, unique CNPJ, create, paginated filters, public active options sorted by name, update with version, activate/deactivate, delete unused, and `409 INSTITUTION_IN_USE` when referenced. Verify non-admin gets `403` and anonymous options expose only id/name/CNPJ.

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
.\mvnw.cmd -Dtest=InstitutionServiceTest,AdminInstitutionControllerTest test
```

Expected: missing service/controllers.

- [ ] **Step 3: Implement contracts**

Implement:

```text
GET    /institutions/options?active=true
GET    /admin/institutions
POST   /admin/institutions
GET    /admin/institutions/{institutionId}
PATCH  /admin/institutions/{institutionId}
POST   /admin/institutions/{institutionId}/activate
POST   /admin/institutions/{institutionId}/deactivate
DELETE /admin/institutions/{institutionId}
```

Normalize CNPJ and phone to digits for persistence, return formatted display values consistently, validate contact email, and map optimistic-lock failure to `409 VERSION_CONFLICT`.

- [ ] **Step 4: Run focused and full tests**

```powershell
.\mvnw.cmd -Dtest=InstitutionServiceTest,AdminInstitutionControllerTest test
.\mvnw.cmd test
```

Expected: all pass, including authorization and conflicts.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/ifsc/contacerta/institution backend/src/test/java/com/ifsc/contacerta/institution
git commit -m "feat: adiciona gestao de instituicoes"
```

---

### Task 8: Admin teacher management and dashboard

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/user/controller/AdminTeacherController.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/user/dto/UserDtos.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/user/service/UserService.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/user/service/AdminTeacherServiceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/user/controller/AdminTeacherControllerTest.java`

**Interfaces:**
- Produces teacher page/detail/create/update and admin-dashboard DTOs from frontend spec section 8.1.
- Consumes action-token creation, mail outbox, institution lookup, password reset, and session revocation.

- [ ] **Step 1: Write failing teacher-admin tests**

Cover creating `PENDING` teacher and invitation, duplicate email, inactive institution, filters by name/email/status/institution, detail, versioned edit, activate, deactivate with all-session revocation, password-reset e-mail, and dashboard counts by teacher/institution status. Verify admins cannot create other admins through this endpoint.

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
.\mvnw.cmd -Dtest=AdminTeacherServiceTest,AdminTeacherControllerTest test
```

Expected: missing admin teacher methods/controller.

- [ ] **Step 3: Implement endpoints and service rules**

Implement:

```text
GET    /admin/dashboard
GET    /admin/teachers
POST   /admin/teachers
GET    /admin/teachers/{teacherId}
PATCH  /admin/teachers/{teacherId}
POST   /admin/teachers/{teacherId}/activate
POST   /admin/teachers/{teacherId}/deactivate
POST   /admin/teachers/{teacherId}/password-reset
```

Creation receives full name, email, registration number, and institution ID; it never accepts a password. Editing permits name, registration number, and institution only. Deactivation revokes sessions immediately. Password-reset returns `202` without exposing token values.

- [ ] **Step 4: Run focused tests and the full verification gate**

```powershell
.\mvnw.cmd -Dtest=AdminTeacherServiceTest,AdminTeacherControllerTest test
.\mvnw.cmd verify
```

Expected: Maven exits `0`; all migrations, unit, integration, security, and MVC tests pass.

- [ ] **Step 5: Exercise the slice through HTTP**

With PostgreSQL and Mailpit configured, start the app and verify this sequence: seed admin; login; create institution; create teacher; consume invitation link; login as teacher; register student; consume verification link; login as student; rotate refresh token; logout. Record sanitized example requests/responses in the PR description, never real tokens.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/ifsc/contacerta/user backend/src/test/java/com/ifsc/contacerta/user
git commit -m "feat: adiciona gestao administrativa de professores"
```

## Completion Gate

- [ ] Run `cd backend; .\mvnw.cmd verify` and confirm exit code `0`.
- [ ] Start from an empty PostgreSQL database and confirm Flyway applies V1 and V2 successfully.
- [ ] Confirm `git status --short` contains no generated `target/`, secrets, `.env`, PEM private keys, or unrelated changes.
- [ ] Compare implemented routes against backend spec sections 5.1 and the institution/teacher portions of 5.4.
- [ ] Confirm room/content/assessment endpoints remain absent; they belong to later plans.

## Subsequent Plans

After this plan passes its completion gate, create and execute, in order:

1. `backend-rooms-memberships`: rooms, join codes, multi-room memberships, removal/reactivation, archive and duplication.
2. `backend-content-authoring`: lessons, Markdown/images, four question types, ordering, publication and room assignments.
3. `backend-assessments-gamification`: snapshots, timers, answers, grading, attempts, XP, stars, progression, achievements and ranking.
4. `backend-media-reports`: private storage, video/material assignments, views, financial tips, dashboards, report queries and CSV.
