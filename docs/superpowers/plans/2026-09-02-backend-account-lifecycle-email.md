# Backend Account Lifecycle and Email Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the six public account-lifecycle endpoints and reliable SMTP delivery through a transactional PostgreSQL outbox.

**Architecture:** `AccountLifecycleService` coordinates users, institutions, action tokens, sessions, and message enqueueing. `ActionTokenService` owns secure single-use tokens; `MailOutboxDispatcher` claims committed messages and sends them through a `MailSender` port implemented with Spring SMTP.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Spring Security, Spring Data JPA, PostgreSQL, Flyway, Spring Mail, Testcontainers, JUnit 5, Mockito, Mailpit

**Spec:** `docs/superpowers/specs/2026-09-02-backend-account-lifecycle-email-design.md`

## Global Constraints

- Preserve `controller -> service -> repository`; controllers never access repositories.
- Do not expose JPA entities in API contracts.
- Use tab indentation, explicit imports, Lombok `@RequiredArgsConstructor`, entity `@Getter`, and `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.
- Never store or log raw action tokens, passwords, message bodies, or sensitive links.
- Generate 256-bit action tokens with `SecureRandom`; persist only SHA-256 hashes.
- Verification and reset tokens are single-use, purpose-bound, expiring, and consumed under a database lock.
- Within the configured allowance, resend verification and forgot-password return the same `202 Accepted` response for known and unknown emails.
- Resend verification and forgot-password allow five requests per rolling hour by default and return `429 RATE_LIMIT_EXCEEDED` after the allowance is exhausted.
- SMTP I/O never occurs in the account-lifecycle transaction.
- Local defaults are `http://localhost:4200` and `Conta Certa <no-reply@contacerta.local>`; production values come from environment variables.
- Implement every production change test-first and run the focused test red and green.

---

### Task 1: Persist Action Tokens and Mail Outbox Records

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__create_action_tokens_and_mail_outbox.sql`
- Create: `backend/src/main/java/com/ifsc/contacerta/model/ActionTokenType.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/model/MailMessageType.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/model/MailOutboxStatus.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/ActionToken.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/MailOutboxMessage.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/ActionTokenRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/MailOutboxRepository.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/entity/AccountLifecyclePersistenceTest.java`

**Interfaces:**
- Produces: `ActionToken(User, ActionTokenType, String tokenHash, Instant expiresAt, Instant now)`.
- Produces: `MailOutboxMessage(MailMessageType, String recipient, String subject, String textBody, String htmlBody, Instant now)`.
- Produces: locked token lookup and outbox claim queries used by later tasks.

- [ ] **Step 1: Write the failing persistence test**

Persist a user, one token, and one outbox message. Assert enum values, timestamps, unique `token_hash`, and non-null status/attempt defaults. Add a test proving a duplicate hash raises `DataIntegrityViolationException`.

```java
ActionToken token = tokenRepository.saveAndFlush(new ActionToken(
		user, ActionTokenType.EMAIL_VERIFICATION, "hash-value", now.plus(Duration.ofHours(24)), now
));
assertThat(tokenRepository.findById(token.getId())).isPresent();
assertThat(message.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
assertThat(message.getAttemptCount()).isZero();
```

- [ ] **Step 2: Run the test red**

Run: `./mvnw -Dtest=AccountLifecyclePersistenceTest test`

Expected: test compilation fails because the migration, entities, enums, and repositories do not exist.

- [ ] **Step 3: Add migration and entities**

Create `action_tokens` with `id`, `user_id`, `type`, `token_hash`, `expires_at`, `consumed_at`, `invalidated_at`, and `created_at`. Create `mail_outbox` with `id`, `type`, `recipient`, `subject`, `text_body`, `html_body`, `status`, `attempt_count`, `next_attempt_at`, `claimed_at`, `sent_at`, `last_error`, `created_at`, and `version`. Add indexes for token lookup, usable tokens by user/type, and due outbox rows.

Entity mutation methods must be explicit:

```java
public void consume(Instant now) { consumedAt = now; }
public void invalidate(Instant now) { if (consumedAt == null) invalidatedAt = now; }
public void claim(Instant now) { status = MailOutboxStatus.SENDING; claimedAt = now; }
public void markSent(Instant now) { status = MailOutboxStatus.SENT; sentAt = now; claimedAt = null; }
public void scheduleRetry(Instant next, String error) { status = MailOutboxStatus.PENDING; attemptCount++; nextAttemptAt = next; claimedAt = null; lastError = error; }
public void markFailed(String error) { status = MailOutboxStatus.FAILED; attemptCount++; claimedAt = null; lastError = error; }
```

- [ ] **Step 4: Add repository contracts**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select token from ActionToken token join fetch token.user where token.tokenHash = :hash and token.type = :type")
Optional<ActionToken> findForUpdateByTokenHashAndType(String hash, ActionTokenType type);

@Modifying
@Query("update ActionToken token set token.invalidatedAt = :now where token.user.id = :userId and token.type = :type and token.consumedAt is null and token.invalidatedAt is null")
int invalidateUsableByUserIdAndType(UUID userId, ActionTokenType type, Instant now);
```

Expose focused outbox queries to find due IDs with PostgreSQL `for update skip locked`, load a claimed record by ID, and recover `SENDING` rows whose `claimedAt` is older than the lease.

- [ ] **Step 5: Run persistence tests green**

Run: `./mvnw -Dtest=AccountLifecyclePersistenceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V11__create_action_tokens_and_mail_outbox.sql src/main/java/com/ifsc/contacerta/model src/main/java/com/ifsc/contacerta/entity/ActionToken.java src/main/java/com/ifsc/contacerta/entity/MailOutboxMessage.java src/main/java/com/ifsc/contacerta/repository/ActionTokenRepository.java src/main/java/com/ifsc/contacerta/repository/MailOutboxRepository.java src/test/java/com/ifsc/contacerta/entity/AccountLifecyclePersistenceTest.java
git commit -m "feat: persiste tokens de acao e outbox de emails"
```

### Task 2: Generate, Replace, and Consume Action Tokens

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/config/AccountLifecycleProperties.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/config/RandomConfig.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/ActionTokenService.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/ActionTokenServiceTest.java`
- Modify: `backend/src/main/resources/application.properties`

**Interfaces:**
- Consumes: `ActionTokenRepository` from Task 1.
- Produces: `GeneratedActionToken create(User user, ActionTokenType type)`.
- Produces: `User consume(String plainText, ActionTokenType expectedType)`.
- Produces: `record GeneratedActionToken(String plainText, Instant expiresAt) {}`.

- [ ] **Step 1: Write failing token-service tests**

Use a fixed `Clock`, mocked repository, and deterministic 32-byte random source. Assert URL-safe token generation, SHA-256 persistence, type binding, replacement invalidation, and the three stable errors.

```java
assertThatThrownBy(() -> service.consume("token", ActionTokenType.PASSWORD_RESET))
		.isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.getStatus()).isEqualTo(HttpStatus.GONE);
			assertThat(exception.getCode()).isEqualTo("ACTION_TOKEN_EXPIRED");
		});
```

- [ ] **Step 2: Run the test red**

Run: `./mvnw -Dtest=ActionTokenServiceTest test`

Expected: compilation fails because `ActionTokenService` and properties are absent.

- [ ] **Step 3: Implement typed properties and token generation**

Bind `app.account.frontend-url`, `app.account.token.email-verification-ttl`, `teacher-invitation-ttl`, and `password-reset-ttl`. Reuse the configured `SecureRandom` bean. Encode exactly 32 random bytes using `Base64.getUrlEncoder().withoutPadding()` and hash UTF-8 bytes with SHA-256.

- [ ] **Step 4: Implement replacement and consumption**

`create` invalidates usable tokens for the user/type, saves the new hash, and returns the plaintext only to the caller. `consume` hashes the supplied value, performs the locked purpose-bound lookup, checks `consumedAt`, `invalidatedAt`, and `expiresAt` in that order, marks a valid token consumed, and returns its user.

- [ ] **Step 5: Run token tests green**

Run: `./mvnw -Dtest=ActionTokenServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ifsc/contacerta/config/AccountLifecycleProperties.java src/main/java/com/ifsc/contacerta/config/RandomConfig.java src/main/java/com/ifsc/contacerta/service/ActionTokenService.java src/test/java/com/ifsc/contacerta/service/ActionTokenServiceTest.java src/main/resources/application.properties
git commit -m "feat: implementa tokens de acao de uso unico"
```

### Task 3: Enqueue and Deliver Email Reliably

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/ifsc/contacerta/config/MailOutboxProperties.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mail/MailMessage.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mail/MailSender.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mail/SmtpMailSender.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/MailOutboxService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/MailOutboxDispatcher.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/MailOutboxServiceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/MailOutboxDispatcherTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/mail/SmtpMailSenderTest.java`
- Modify: `backend/src/main/resources/application.properties`

**Interfaces:**
- Consumes: `MailOutboxRepository` and `MailOutboxMessage` from Task 1.
- Produces: `void enqueue(MailMessageType type, String recipient, String subject, String textBody, String htmlBody)`.
- Produces: `void dispatch()` and `void send(MailMessage message)`.

- [ ] **Step 1: Write failing enqueue and SMTP adapter tests**

Assert enqueue persists `PENDING`; assert `SmtpMailSender` maps sender, recipient, subject, text, and HTML into `MimeMessageHelper` and calls `JavaMailSender.send` once.

- [ ] **Step 2: Run the tests red**

Run: `./mvnw -Dtest=MailOutboxServiceTest,SmtpMailSenderTest test`

Expected: compilation fails because mail interfaces and Spring Mail dependency are absent.

- [ ] **Step 3: Add Spring Mail and enqueue/send implementation**

Add `spring-boot-starter-mail`. Configure `spring.mail.*`, `app.mail.from`, and outbox properties. Keep the port independent of Spring Mail:

```java
public interface MailSender {
	void send(MailMessage message);
}
```

- [ ] **Step 4: Write failing dispatcher tests**

Cover claim-and-send, retry delay after an exception, `FAILED` on the fifth exception, truncated error summary, and recovery of claims older than the configured lease.

- [ ] **Step 5: Run dispatcher tests red**

Run: `./mvnw -Dtest=MailOutboxDispatcherTest test`

Expected: fails because dispatch/claim/retry behavior is absent.

- [ ] **Step 6: Implement transactional claiming and delivery**

Use one transaction to recover expired claims and claim a bounded list of IDs. Send each message outside that transaction, then update each outcome in a new transaction. Schedule retry delays from configured durations and never log bodies or links.

- [ ] **Step 7: Run mail tests green**

Run: `./mvnw -Dtest=MailOutboxServiceTest,MailOutboxDispatcherTest,SmtpMailSenderTest test`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/java/com/ifsc/contacerta/config/MailOutboxProperties.java src/main/java/com/ifsc/contacerta/mail src/main/java/com/ifsc/contacerta/service/MailOutboxService.java src/main/java/com/ifsc/contacerta/service/MailOutboxDispatcher.java src/test/java/com/ifsc/contacerta/mail src/test/java/com/ifsc/contacerta/service/MailOutboxServiceTest.java src/test/java/com/ifsc/contacerta/service/MailOutboxDispatcherTest.java src/main/resources/application.properties
git commit -m "feat: envia emails por outbox transacional"
```

### Task 4: Enforce Persistent Account-Message Rate Limits

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__create_account_rate_limits.sql`
- Create: `backend/src/main/java/com/ifsc/contacerta/model/AccountRateLimitOperation.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/AccountRateLimit.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/AccountRateLimitRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/config/AccountRateLimitProperties.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/AccountRateLimitService.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/AccountRateLimitServiceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/repository/AccountRateLimitRepositoryTest.java`
- Modify: `backend/src/main/resources/application.properties`

**Interfaces:**
- Produces: `void check(String normalizedEmail, AccountRateLimitOperation operation)`.
- Produces: `RESEND_VERIFICATION` and `FORGOT_PASSWORD` operation keys.
- Produces: configurable defaults of five requests in a rolling one-hour window.

- [ ] **Step 1: Write failing rate-limit tests**

With a fixed `Clock`, assert the first five checks succeed, the sixth throws `429 RATE_LIMIT_EXCEEDED`, a check after one hour opens a new window, and separate operations have separate allowances. Capture the saved entity and assert its key is a SHA-256 hash rather than the email.

```java
assertThatThrownBy(() -> service.check("ana@example.com", AccountRateLimitOperation.FORGOT_PASSWORD))
		.isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
			assertThat(exception.getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
		});
```

- [ ] **Step 2: Run rate-limit tests red**

Run: `./mvnw -Dtest=AccountRateLimitServiceTest,AccountRateLimitRepositoryTest test`

Expected: compilation fails because the rate-limit model, repository, and service do not exist.

- [ ] **Step 3: Implement persistence and atomic window updates**

Create `account_rate_limits` with composite uniqueness on `operation` and `subject_hash`, plus `window_started_at`, `request_count`, and `version`. Hash `operation + ':' + normalizedEmail` with SHA-256. Execute `check` in a `REQUIRES_NEW` transaction so every accepted attempt is counted even if the surrounding account operation later fails. Use a PostgreSQL upsert that atomically starts or increments the window and returns the resulting count; reset it when `windowStartedAt + window <= now`, and reject counts above the configured allowance.

- [ ] **Step 4: Run rate-limit tests green**

Run: `./mvnw -Dtest=AccountRateLimitServiceTest,AccountRateLimitRepositoryTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V12__create_account_rate_limits.sql src/main/java/com/ifsc/contacerta/model/AccountRateLimitOperation.java src/main/java/com/ifsc/contacerta/entity/AccountRateLimit.java src/main/java/com/ifsc/contacerta/repository/AccountRateLimitRepository.java src/main/java/com/ifsc/contacerta/config/AccountRateLimitProperties.java src/main/java/com/ifsc/contacerta/service/AccountRateLimitService.java src/test/java/com/ifsc/contacerta/service/AccountRateLimitServiceTest.java src/test/java/com/ifsc/contacerta/repository/AccountRateLimitRepositoryTest.java src/main/resources/application.properties
git commit -m "feat: limita mensagens de ciclo de conta"
```

### Task 5: Register Students and Verify Email

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/auth/StudentRegistrationRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/auth/ActionTokenRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/auth/ResendVerificationRequest.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/entity/User.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/InstitutionRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/AccountMailFactory.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/AccountLifecycleService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/controller/AuthController.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/config/SecurityConfig.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/AccountLifecycleServiceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/controller/AccountLifecycleControllerTest.java`

**Interfaces:**
- Consumes: token and outbox services from Tasks 2–3.
- Consumes: `AccountRateLimitService` from Task 4 for verification resend.
- Produces: `void registerStudent(StudentRegistrationRequest request)`.
- Produces: `void verifyEmail(String token)` and `void resendVerification(String email)`.

- [ ] **Step 1: Write failing registration and verification service tests**

Cover normalized email, password policy, duplicate email, active institution, pending student creation, confirmation enqueue, successful activation, token errors, and neutral resend for unknown/already-verified accounts.

- [ ] **Step 2: Run service tests red**

Run: `./mvnw -Dtest=AccountLifecycleServiceTest test`

Expected: compilation fails because lifecycle DTOs/service are absent.

- [ ] **Step 3: Add DTOs and user transitions**

Use Jakarta validation matching the Angular contracts. Add domain methods that enforce legal transitions:

```java
public void verifyEmail(Instant now) { emailVerifiedAt = now; }
public void activate() { status = AccountStatus.ACTIVE; }
```

`AccountMailFactory` builds the exact `/verificar-email?token=` link from configured `FRONTEND_URL` and produces text plus minimal escaped HTML.

- [ ] **Step 4: Implement registration, verification, and resend**

Create `STUDENT/PENDING`, initialize the encoded password, create the token, and enqueue in one transaction. Verification consumes only `EMAIL_VERIFICATION`, verifies email, and activates only the pending student. Resend always returns normally and only creates mail for an eligible pending unverified student.

- [ ] **Step 5: Write failing controller/security tests**

Assert unauthenticated calls receive `202`, `204`, and token Problem Details rather than `401`; assert invalid bodies return `400` with field errors.

- [ ] **Step 6: Expose public endpoints and run tests green**

Add controller mappings and permit the three exact POST routes in `SecurityConfig`.

Run: `./mvnw -Dtest=AccountLifecycleServiceTest,AccountLifecycleControllerTest,SecurityConfigTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/ifsc/contacerta/dto/auth src/main/java/com/ifsc/contacerta/entity/User.java src/main/java/com/ifsc/contacerta/repository/InstitutionRepository.java src/main/java/com/ifsc/contacerta/service/AccountMailFactory.java src/main/java/com/ifsc/contacerta/service/AccountLifecycleService.java src/main/java/com/ifsc/contacerta/controller/AuthController.java src/main/java/com/ifsc/contacerta/config/SecurityConfig.java src/test/java/com/ifsc/contacerta/service/AccountLifecycleServiceTest.java src/test/java/com/ifsc/contacerta/controller/AccountLifecycleControllerTest.java src/test/java/com/ifsc/contacerta/config/SecurityConfigTest.java
git commit -m "feat: cadastra alunos e confirma emails"
```

### Task 6: Recover Passwords and Revoke Sessions

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/auth/ForgotPasswordRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/auth/ResetPasswordRequest.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/AuthSessionRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/RefreshTokenRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/AccountMailFactory.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/AccountLifecycleService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/controller/AuthController.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/config/SecurityConfig.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/service/AccountLifecycleServiceTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/controller/AccountLifecycleControllerTest.java`

**Interfaces:**
- Consumes: `AccountRateLimitService` from Task 4 for forgot-password.
- Produces: `void forgotPassword(String email)`.
- Produces: `void resetPassword(String token, String newPassword)`.
- Produces: bulk session and refresh-token revocation by user ID.

- [ ] **Step 1: Write failing recovery tests**

Assert forgot-password is neutral for unknown/ineligible accounts, valid accounts receive `/redefinir-senha?token=`, reset applies password policy, consumes only `PASSWORD_RESET`, changes the hash, and revokes all sessions and refresh tokens.

- [ ] **Step 2: Run tests red**

Run: `./mvnw -Dtest=AccountLifecycleServiceTest test`

Expected: new recovery tests fail because methods and DTOs are absent.

- [ ] **Step 3: Implement recovery and revocation**

Add explicit modifying repository queries scoped by `user.id`. Perform password change, token consumption, session revocation, and refresh-token revocation in one transaction. Do not authenticate after reset.

- [ ] **Step 4: Add public mappings and run green**

Permit `/auth/forgot-password` and `/auth/reset-password`; return `202` and `204` respectively.

Run: `./mvnw -Dtest=AccountLifecycleServiceTest,AccountLifecycleControllerTest,AuthServiceRefreshTest,SecurityConfigTest test`

Expected: PASS, including rejection of all pre-reset refresh tokens.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ifsc/contacerta/dto/auth src/main/java/com/ifsc/contacerta/repository/AuthSessionRepository.java src/main/java/com/ifsc/contacerta/repository/RefreshTokenRepository.java src/main/java/com/ifsc/contacerta/service/AccountMailFactory.java src/main/java/com/ifsc/contacerta/service/AccountLifecycleService.java src/main/java/com/ifsc/contacerta/controller/AuthController.java src/main/java/com/ifsc/contacerta/config/SecurityConfig.java src/test/java/com/ifsc/contacerta/service/AccountLifecycleServiceTest.java src/test/java/com/ifsc/contacerta/controller/AccountLifecycleControllerTest.java
git commit -m "feat: recupera senha e revoga sessoes"
```

### Task 7: Accept Teacher Invitations

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/auth/AcceptTeacherInviteRequest.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/AccountMailFactory.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/AccountLifecycleService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/controller/AuthController.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/config/SecurityConfig.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/service/AccountLifecycleServiceTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/controller/AccountLifecycleControllerTest.java`

**Interfaces:**
- Produces: `GeneratedActionToken inviteTeacher(User pendingTeacher)` for the later admin service.
- Produces: `void acceptTeacherInvite(String token, String password)`.

- [ ] **Step 1: Write failing invitation tests**

Assert invitation creation rejects non-teachers and non-pending users, emits `/convite-professor?token=`, and invalidates the prior invitation. Assert acceptance validates password, consumes `TEACHER_INVITATION`, initializes the password, verifies email, activates the teacher, and rejects a token whose user is not a pending teacher.

- [ ] **Step 2: Run tests red**

Run: `./mvnw -Dtest=AccountLifecycleServiceTest test`

Expected: invitation tests fail because operations are absent.

- [ ] **Step 3: Implement invitation creation and acceptance**

Keep `inviteTeacher` transactional so token and outbox commit together. Make acceptance return `404 ACTION_TOKEN_NOT_FOUND` for a token bound to an invalid account state without revealing account details.

- [ ] **Step 4: Expose public acceptance and run green**

Permit only `POST /auth/accept-teacher-invite` and return `204`.

Run: `./mvnw -Dtest=AccountLifecycleServiceTest,AccountLifecycleControllerTest,SecurityConfigTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ifsc/contacerta/dto/auth/AcceptTeacherInviteRequest.java src/main/java/com/ifsc/contacerta/service/AccountMailFactory.java src/main/java/com/ifsc/contacerta/service/AccountLifecycleService.java src/main/java/com/ifsc/contacerta/controller/AuthController.java src/main/java/com/ifsc/contacerta/config/SecurityConfig.java src/test/java/com/ifsc/contacerta/service/AccountLifecycleServiceTest.java src/test/java/com/ifsc/contacerta/controller/AccountLifecycleControllerTest.java
git commit -m "feat: implementa convite de professores"
```

### Task 8: Add Mailpit and Verify the Complete Lifecycle

**Files:**
- Modify: `backend/compose.yaml`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/test/java/com/ifsc/contacerta/controller/AccountLifecycleControllerTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/entity/AccountLifecyclePersistenceTest.java`

**Interfaces:**
- Consumes: all public endpoints, persistence, rate limiting, and email delivery from Tasks 1–7.
- Produces: local SMTP service on port `1025` and Mailpit UI on port `8025`.

- [ ] **Step 1: Add end-to-end backend integration tests**

Using PostgreSQL Testcontainers and mocked `MailSender`, cover registration through verification, forgot through reset and session rejection, and pending teacher through invitation acceptance. Assert database token hashes never equal the raw query-string token.

- [ ] **Step 2: Run integration tests before infrastructure configuration**

Run: `./mvnw -Dtest=AccountLifecycleControllerTest,AccountLifecyclePersistenceTest test`

Expected: lifecycle integration tests pass because SMTP is mocked; failures must be corrected in the owning service before changing local infrastructure.

- [ ] **Step 3: Configure local Mailpit**

Add a `mailpit` service using `axllent/mailpit` with ports `${MAILPIT_SMTP_PORT:-1025}:1025` and `${MAILPIT_UI_PORT:-8025}:8025`. Point local `spring.mail.host` to `${MAIL_HOST:localhost}` and port `${MAIL_PORT:1025}`; keep credentials and TLS environment-controlled.

- [ ] **Step 4: Run focused and full verification**

Run:

```bash
./mvnw -Dtest=ActionTokenServiceTest,MailOutboxServiceTest,MailOutboxDispatcherTest,SmtpMailSenderTest,AccountRateLimitServiceTest,AccountRateLimitRepositoryTest,AccountLifecycleServiceTest,AccountLifecycleControllerTest,AccountLifecyclePersistenceTest test
./mvnw verify
git diff --check
git status --short
```

Expected: all focused tests pass; full build reports zero failures and errors; diff check is clean; only intentional lifecycle/email files remain.

- [ ] **Step 5: Commit**

```bash
git add compose.yaml src/main/resources/application.properties src/test/java/com/ifsc/contacerta/controller/AccountLifecycleControllerTest.java src/test/java/com/ifsc/contacerta/entity/AccountLifecyclePersistenceTest.java
git commit -m "test: valida ciclo completo de contas e emails"
```
