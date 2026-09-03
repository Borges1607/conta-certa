# Backend Admin Institutions and Teachers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the admin dashboard and complete institution and teacher administration contracts consumed by the Angular frontend.

**Architecture:** Three focused controller/service pairs own institutions, teachers, and dashboard aggregates. JPA Specifications and focused repository queries perform filtering, pagination, counts, and history checks in PostgreSQL; the existing account-lifecycle service remains the only producer of invitation and password-reset messages.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Spring Security, Spring Data JPA Specifications, PostgreSQL, Flyway, Testcontainers, JUnit 5, AssertJ, MockMvc

**Spec:** `docs/superpowers/specs/2026-09-03-backend-admin-institutions-teachers-design.md`

## Global Constraints

- Preserve `controller -> service -> repository`; controllers never access repositories.
- Never expose JPA entities from the API.
- Restrict every `/admin/**` route to `ADMIN`; anonymous callers receive `401` and other authenticated roles receive `403`.
- Use zero-based pagination, default size 20, maximum size 100, and allow-listed sort fields.
- Require `version` for PATCH operations and return `409 VERSION_CONFLICT` for stale writes.
- Admin never supplies, reads, or directly replaces a teacher password.
- A teacher is created as `PENDING` without a password and receives an invitation through the existing transactional outbox.
- `PENDING` teachers cannot be administratively activated; only `INACTIVE` teachers can transition to `ACTIVE`.
- Teacher deactivation revokes all auth sessions and refresh tokens in the same transaction.
- Teachers with rooms or history cannot change institutions.
- Institutions with history cannot be deleted; deactivation preserves existing relationships.
- Implement production behavior test-first and end every task with one atomic commit.
- Use tab indentation, explicit imports, Lombok `@RequiredArgsConstructor`, entity `@Getter`, and `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.

---

### Task 1: Add Shared Admin Pagination and Institution Query Contracts

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/AdminPageableFactory.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/specification/InstitutionSpecification.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/AdminHistoryQueryRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/InstitutionRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/UserRepository.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/controller/AdminPageableFactoryTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/repository/AdminInstitutionRepositoryTest.java`

**Interfaces:**
- Produces: `Pageable AdminPageableFactory.create(int page, int size, String sort, Set<String> allowedFields, String errorCode)`.
- Produces: `Specification<Institution> InstitutionSpecification.filtered(String search, Boolean active)`.
- Produces: `long UserRepository.countByInstitutionIdAndRole(UUID institutionId, Role role)`.
- Produces: `boolean AdminHistoryQueryRepository.hasInstitutionHistory(UUID institutionId)`.
- Produces: `boolean AdminHistoryQueryRepository.hasTeacherHistory(UUID teacherId)` covering rooms, lessons, videos, materials, and audited extra-attempt grants.

- [ ] **Step 1: Write failing pageable tests**

Add tests proving valid `name,asc` creates the expected `Pageable`, size 101 and page -1 return `INVALID_PAGE`, and unknown fields or directions return the supplied sort error.

```java
Pageable pageable = factory.create(2, 50, "name,desc", Set.of("name"), "INVALID_INSTITUTION_SORT");
assertThat(pageable.getPageNumber()).isEqualTo(2);
assertThat(pageable.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.DESC);
```

- [ ] **Step 2: Run pageable tests red**

Run: `./mvnw -Dtest=AdminPageableFactoryTest test`

Expected: compilation fails because `AdminPageableFactory` does not exist.

- [ ] **Step 3: Implement the shared factory**

Implement strict parsing of `field,asc|desc`; accept a missing direction as ascending. Throw `ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_PAGE", ...)` for pagination and the caller-provided error code for sort failures.

- [ ] **Step 4: Run pageable tests green**

Run: `./mvnw -Dtest=AdminPageableFactoryTest test`

Expected: PASS.

- [ ] **Step 5: Write failing PostgreSQL query tests**

Persist active/inactive institutions, teachers, students, rooms, lessons, videos, materials, and an extra-attempt grant fixture. Assert case-insensitive name search, punctuation-tolerant CNPJ search, active filtering, paginated ordering, role counts, institution history, and teacher history for every supported relationship.

```java
Page<Institution> result = institutionRepository.findAll(
		InstitutionSpecification.filtered("12.345", true), PageRequest.of(0, 20)
);
assertThat(result.getContent()).extracting(Institution::getName).containsExactly("Instituição A");
```

- [ ] **Step 6: Run repository tests red**

Run: `./mvnw -Dtest=AdminInstitutionRepositoryTest test`

Expected: compilation fails because the specification and repository methods are absent.

- [ ] **Step 7: Implement specifications and focused queries**

Make `InstitutionRepository` extend `JpaSpecificationExecutor<Institution>`. Normalize the CNPJ search branch to digits without weakening the name branch. Implement `AdminHistoryQueryRepository` with focused PostgreSQL `exists` queries; teacher history checks `rooms.teacher_id`, `lessons.teacher_id`, `videos.teacher_id`, `materials.teacher_id`, and `extra_attempt_grants.granted_by`, while institution history checks users and rooms because all other institutional history is anchored through those foreign keys.

- [ ] **Step 8: Run Task 1 tests green**

Run: `./mvnw -Dtest=AdminPageableFactoryTest,AdminInstitutionRepositoryTest test`

Expected: PASS.

- [ ] **Step 9: Commit atomically**

```bash
git add backend/src/main/java/com/ifsc/contacerta/controller/AdminPageableFactory.java backend/src/main/java/com/ifsc/contacerta/specification/InstitutionSpecification.java backend/src/main/java/com/ifsc/contacerta/repository/AdminHistoryQueryRepository.java backend/src/main/java/com/ifsc/contacerta/repository/InstitutionRepository.java backend/src/main/java/com/ifsc/contacerta/repository/UserRepository.java backend/src/test/java/com/ifsc/contacerta/controller/AdminPageableFactoryTest.java backend/src/test/java/com/ifsc/contacerta/repository/AdminInstitutionRepositoryTest.java
git commit -m "feat: adiciona consultas administrativas de instituicoes"
```

### Task 2: Implement Institution Administration

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/admin/AdminInstitutionResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/admin/PatchInstitutionRequest.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/dto/institution/CreateInstitutionRequest.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/entity/Institution.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mapper/AdminInstitutionMapper.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/AdminInstitutionService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/AdminInstitutionController.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/AdminInstitutionServiceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/controller/AdminInstitutionControllerTest.java`

**Interfaces:**
- Consumes: Task 1 specifications, pagination, and history queries.
- Produces: `PageResponse<AdminInstitutionResponse> list(String search, Boolean active, Pageable pageable)`.
- Produces: `AdminInstitutionResponse create(CreateInstitutionRequest request)`, `get(UUID id)`, `update(UUID id, PatchInstitutionRequest request)`, `activate(UUID id)`, and `deactivate(UUID id)`.
- Produces: `void delete(UUID id)`.
- Produces: entity methods `update(String name, String cnpj, String contactEmail, String contactPhone)`, `activate()`, and `deactivate()`.

- [ ] **Step 1: Write failing institution service tests**

Cover normalized creation, duplicate CNPJ, listing counts, detail, partial patch, stale version, idempotent state changes, deletion without history, and `INSTITUTION_HAS_HISTORY` when the dedicated history query finds users or rooms.

```java
assertThatThrownBy(() -> service.update(id, new PatchInstitutionRequest(
		"Novo nome", null, null, null, currentVersion + 1
))).isInstanceOfSatisfying(ApiException.class,
		error -> assertThat(error.getCode()).isEqualTo("VERSION_CONFLICT"));
```

- [ ] **Step 2: Run service tests red**

Run: `./mvnw -Dtest=AdminInstitutionServiceTest test`

Expected: compilation fails because admin DTOs and service are absent.

- [ ] **Step 3: Add DTOs, entity transitions, mapper, and service**

Validate name length 1–160, exactly 14 CNPJ digits after normalization, valid email, and E.164 phone (`^\\+[1-9]\\d{7,14}$`). Explicitly compare request and entity versions. Return teacher/student counts from the mapper without loading user collections.

- [ ] **Step 4: Run service tests green**

Run: `./mvnw -Dtest=AdminInstitutionServiceTest test`

Expected: PASS.

- [ ] **Step 5: Write failing controller and security tests**

Cover all eight endpoints, `201` plus `Location`, page envelope, field validation, `204` delete, stable conflicts, anonymous `401`, teacher/student `403`, and admin success.

```java
mockMvc.perform(post("/admin/institutions")
		.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
		.contentType(MediaType.APPLICATION_JSON)
		.content(validJson))
		.andExpect(status().isCreated())
		.andExpect(header().string("Location", matchesPattern("/admin/institutions/.+")));
```

- [ ] **Step 6: Expose routes and update security**

Use `@RequestMapping("/admin/institutions")`, delegate pageable construction to `AdminPageableFactory`, and add `/admin/**` as `hasRole("ADMIN")` before the generic authenticated rule.

- [ ] **Step 7: Run Task 2 tests green**

Run: `./mvnw -Dtest=AdminInstitutionServiceTest,AdminInstitutionControllerTest,SecurityConfigTest test`

Expected: PASS.

- [ ] **Step 8: Commit atomically**

```bash
git add backend/src/main/java/com/ifsc/contacerta/dto/admin backend/src/main/java/com/ifsc/contacerta/dto/institution/CreateInstitutionRequest.java backend/src/main/java/com/ifsc/contacerta/entity/Institution.java backend/src/main/java/com/ifsc/contacerta/mapper/AdminInstitutionMapper.java backend/src/main/java/com/ifsc/contacerta/service/AdminInstitutionService.java backend/src/main/java/com/ifsc/contacerta/controller/AdminInstitutionController.java backend/src/main/java/com/ifsc/contacerta/config/SecurityConfig.java backend/src/test/java/com/ifsc/contacerta/service/AdminInstitutionServiceTest.java backend/src/test/java/com/ifsc/contacerta/controller/AdminInstitutionControllerTest.java backend/src/test/java/com/ifsc/contacerta/config/SecurityConfigTest.java
git commit -m "feat: implementa administracao de instituicoes"
```

### Task 3: Add Teacher Filtering and Administrative Read Models

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/specification/TeacherSpecification.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/UserRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/AuthSessionRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/admin/AdminTeacherResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mapper/AdminTeacherMapper.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/repository/AdminTeacherRepositoryTest.java`

**Interfaces:**
- Produces: `Specification<User> TeacherSpecification.filtered(String search, AccountStatus status, UUID institutionId)` that always includes `role = TEACHER`.
- Produces: `Optional<User> UserRepository.findByIdAndRole(UUID id, Role role)`.
- Produces: `Optional<Instant> AuthSessionRepository.findLastUsedAtByUserId(UUID userId)`.
- Produces: `AdminTeacherMapper.toResponse(User teacher, Instant lastLoginAt)`.

- [ ] **Step 1: Write failing teacher repository tests**

Persist teachers across institutions and statuses plus a student matching the search. Assert the student never appears; name/email/matriculation search, status, institution, paging, role-scoped lookup, and maximum session `lastUsedAt` all return hand-derived results.

- [ ] **Step 2: Run repository tests red**

Run: `./mvnw -Dtest=AdminTeacherRepositoryTest test`

Expected: compilation fails because teacher specification and query contracts are absent.

- [ ] **Step 3: Implement teacher read queries and DTO mapping**

Make `UserRepository` extend `JpaSpecificationExecutor<User>`. Build predicates only for supplied filters and always constrain the role. Map `emailVerified` from `emailVerifiedAt != null` and preserve nullable `lastLoginAt`.

- [ ] **Step 4: Run repository tests green**

Run: `./mvnw -Dtest=AdminTeacherRepositoryTest test`

Expected: PASS.

- [ ] **Step 5: Commit atomically**

```bash
git add backend/src/main/java/com/ifsc/contacerta/specification/TeacherSpecification.java backend/src/main/java/com/ifsc/contacerta/repository/UserRepository.java backend/src/main/java/com/ifsc/contacerta/repository/AuthSessionRepository.java backend/src/main/java/com/ifsc/contacerta/dto/admin/AdminTeacherResponse.java backend/src/main/java/com/ifsc/contacerta/mapper/AdminTeacherMapper.java backend/src/test/java/com/ifsc/contacerta/repository/AdminTeacherRepositoryTest.java
git commit -m "feat: adiciona consultas administrativas de professores"
```

### Task 4: Create, List, Detail, and Edit Teachers

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/admin/CreateTeacherRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/admin/PatchTeacherRequest.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/entity/User.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/AccountLifecycleService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/AdminTeacherService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/AdminTeacherController.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/AdminTeacherServiceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/controller/AdminTeacherControllerTest.java`

**Interfaces:**
- Consumes: Task 3 teacher filters and mapper.
- Consumes: `AccountLifecycleService.inviteTeacher(User pendingTeacher)`.
- Produces: `PageResponse<AdminTeacherResponse> list(String search, AccountStatus status, UUID institutionId, Pageable pageable)`.
- Produces: `AdminTeacherResponse create(CreateTeacherRequest request)`, `get(UUID teacherId)`, and `update(UUID teacherId, PatchTeacherRequest request)`.
- Produces: entity method `updateTeacherProfile(String fullName, String registrationNumber, Institution institution)`.

- [ ] **Step 1: Write failing teacher creation and editing tests**

Cover normalized email, unique email, active institution, `TEACHER/PENDING` without password, invitation enqueueing, role-scoped detail, partial edit, immutable email, stale version, allowed institution change without history, and blocked change when `AdminHistoryQueryRepository.hasTeacherHistory` is true for rooms, lessons, videos, materials, or audited grants.

```java
AdminTeacherResponse response = service.create(new CreateTeacherRequest(
		"Ana Souza", "ANA@EXAMPLE.COM", "MAT-10", institution.getId()
));
assertThat(response.status()).isEqualTo(AccountStatus.PENDING);
assertThat(userRepository.findById(response.id()).orElseThrow().getPasswordHash()).isNull();
```

- [ ] **Step 2: Run service tests red**

Run: `./mvnw -Dtest=AdminTeacherServiceTest test`

Expected: compilation fails because teacher mutation contracts are absent.

- [ ] **Step 3: Implement creation and editing**

Persist the teacher before calling `inviteTeacher`. Reject inactive destination institutions with `INSTITUTION_INACTIVE`, duplicate emails with the existing `EMAIL_ALREADY_REGISTERED` code, non-teacher IDs with `TEACHER_NOT_FOUND`, and history-bound moves with `TEACHER_INSTITUTION_CHANGE_BLOCKED`.

- [ ] **Step 4: Run service tests green**

Run: `./mvnw -Dtest=AdminTeacherServiceTest test`

Expected: PASS.

- [ ] **Step 5: Write failing controller contract tests**

Cover list filters/page envelope, detail, `201` plus `Location`, absence of any password field in accepted/returned JSON, partial PATCH with mandatory version, validation errors, and the `401`/`403`/admin matrix.

- [ ] **Step 6: Expose teacher read/write endpoints**

Use allow-listed sort fields `fullName`, `email`, `registrationNumber`, `status`, `createdAt`, and `updatedAt`. Map `POST`, `GET`, and `PATCH` under `/admin/teachers`.

- [ ] **Step 7: Run Task 4 tests green**

Run: `./mvnw -Dtest=AdminTeacherServiceTest,AdminTeacherControllerTest,SecurityConfigTest test`

Expected: PASS.

- [ ] **Step 8: Commit atomically**

```bash
git add backend/src/main/java/com/ifsc/contacerta/dto/admin backend/src/main/java/com/ifsc/contacerta/entity/User.java backend/src/main/java/com/ifsc/contacerta/service/AccountLifecycleService.java backend/src/main/java/com/ifsc/contacerta/service/AdminTeacherService.java backend/src/main/java/com/ifsc/contacerta/controller/AdminTeacherController.java backend/src/test/java/com/ifsc/contacerta/service/AdminTeacherServiceTest.java backend/src/test/java/com/ifsc/contacerta/controller/AdminTeacherControllerTest.java
git commit -m "feat: implementa cadastro e edicao de professores"
```

### Task 5: Implement Teacher Account Transitions and Administrative Reset

**Files:**
- Modify: `backend/src/main/java/com/ifsc/contacerta/entity/User.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/AccountLifecycleService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/AdminTeacherService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/controller/AdminTeacherController.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/service/AdminTeacherServiceTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/controller/AdminTeacherControllerTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/AdminTeacherLifecycleIntegrationTest.java`

**Interfaces:**
- Produces: `AdminTeacherResponse activate(UUID teacherId)` and `deactivate(UUID teacherId)`.
- Produces: `void sendPasswordReset(UUID teacherId)`.
- Produces: `AccountLifecycleService.sendPasswordReset(User eligibleUser)` using the existing password-reset token and mail factory.
- Produces: entity methods `activateFromInactive()` and `deactivate()`.

- [ ] **Step 1: Write failing transition service tests**

Assert inactive-to-active, active activation idempotency, pending activation `TEACHER_INVITATION_REQUIRED`, active/pending deactivation, repeated deactivation idempotency, and revocation calls for both auth sessions and refresh tokens.

```java
assertThatThrownBy(() -> service.activate(pendingTeacher.getId()))
		.isInstanceOfSatisfying(ApiException.class,
				error -> assertThat(error.getCode()).isEqualTo("TEACHER_INVITATION_REQUIRED"));
```

- [ ] **Step 2: Run transition tests red**

Run: `./mvnw -Dtest=AdminTeacherServiceTest test`

Expected: transition tests fail because the methods are absent.

- [ ] **Step 3: Implement legal transitions and transactional revocation**

Use the injected `Clock` for revocation timestamps. Deactivation calls `AuthSessionRepository.revokeAllActiveByUserId` and `RefreshTokenRepository.revokeAllActiveByUserId` before returning the response. Do not activate pending accounts.

- [ ] **Step 4: Write failing reset tests**

Assert only active teachers receive a new `PASSWORD_RESET` token/outbox record, pending and inactive teachers return `TEACHER_PASSWORD_RESET_UNAVAILABLE`, and no response or entity exposes a password.

- [ ] **Step 5: Implement administrative reset through account lifecycle**

Add a transactionally joined `sendPasswordReset(User)` operation that creates/replaces the token and enqueues the existing recovery message. It must not use the public email rate limiter because the authenticated admin action is keyed by teacher ID and audited by endpoint access.

- [ ] **Step 6: Add routes and integration coverage**

Map `/activate`, `/deactivate`, and `/password-reset`; state actions need no body and reset returns `202`. In the PostgreSQL integration test, create a real session and refresh token, deactivate the teacher, and assert both are revoked.

- [ ] **Step 7: Run Task 5 tests green**

Run: `./mvnw -Dtest=AdminTeacherServiceTest,AdminTeacherControllerTest,AdminTeacherLifecycleIntegrationTest,AuthServiceRefreshTest test`

Expected: PASS.

- [ ] **Step 8: Commit atomically**

```bash
git add backend/src/main/java/com/ifsc/contacerta/entity/User.java backend/src/main/java/com/ifsc/contacerta/service/AccountLifecycleService.java backend/src/main/java/com/ifsc/contacerta/service/AdminTeacherService.java backend/src/main/java/com/ifsc/contacerta/controller/AdminTeacherController.java backend/src/test/java/com/ifsc/contacerta/service/AdminTeacherServiceTest.java backend/src/test/java/com/ifsc/contacerta/controller/AdminTeacherControllerTest.java backend/src/test/java/com/ifsc/contacerta/service/AdminTeacherLifecycleIntegrationTest.java
git commit -m "feat: gerencia ciclo administrativo de professores"
```

### Task 6: Add Admin Dashboard Aggregates

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/admin/AdminDashboardResponse.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/InstitutionRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/UserRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/AdminDashboardService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/AdminDashboardController.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/AdminDashboardServiceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/controller/AdminDashboardControllerTest.java`

**Interfaces:**
- Produces: `long InstitutionRepository.countByActive(boolean active)`.
- Produces: `long UserRepository.countByRoleAndStatus(Role role, AccountStatus status)` and `long UserRepository.countByRole(Role role)`.
- Produces: `AdminDashboardResponse AdminDashboardService.get()`.

- [ ] **Step 1: Write failing dashboard service tests**

Use literal repository counts and assert the exact nested response fields; teacher totals must exclude students/admins and institution totals must equal active plus inactive.

```java
assertThat(service.get()).isEqualTo(new AdminDashboardResponse(
		new InstitutionCounts(10, 8, 2),
		new TeacherCounts(25, 3, 20, 2)
));
```

- [ ] **Step 2: Run service tests red**

Run: `./mvnw -Dtest=AdminDashboardServiceTest test`

Expected: compilation fails because dashboard contracts are absent.

- [ ] **Step 3: Implement focused counts and dashboard mapping**

Use repository `count` queries only; do not load entity lists. Represent nested counts as records inside or alongside `AdminDashboardResponse` with JSON field names matching the frontend.

- [ ] **Step 4: Write failing dashboard controller/security tests**

Assert exact JSON, anonymous `401`, teacher/student `403`, and admin `200`. Assert the response has no rooms, content, attempts, reports, or ranking fields.

- [ ] **Step 5: Expose dashboard and run Task 6 green**

Map `GET /admin/dashboard` and delegate directly to the service.

Run: `./mvnw -Dtest=AdminDashboardServiceTest,AdminDashboardControllerTest,SecurityConfigTest test`

Expected: PASS.

- [ ] **Step 6: Commit atomically**

```bash
git add backend/src/main/java/com/ifsc/contacerta/dto/admin/AdminDashboardResponse.java backend/src/main/java/com/ifsc/contacerta/repository/InstitutionRepository.java backend/src/main/java/com/ifsc/contacerta/repository/UserRepository.java backend/src/main/java/com/ifsc/contacerta/service/AdminDashboardService.java backend/src/main/java/com/ifsc/contacerta/controller/AdminDashboardController.java backend/src/test/java/com/ifsc/contacerta/service/AdminDashboardServiceTest.java backend/src/test/java/com/ifsc/contacerta/controller/AdminDashboardControllerTest.java
git commit -m "feat: adiciona dashboard administrativo"
```

### Task 7: Verify Complete Administrative Flow

**Files:**
- Create: `backend/src/test/java/com/ifsc/contacerta/controller/AdminFlowIntegrationTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/support/DatabaseIsolationTest.java`

**Interfaces:**
- Consumes: all institution, teacher, dashboard, invitation, outbox, session, and security interfaces from Tasks 1–6.
- Produces: executable acceptance coverage for the Angular admin contracts.

- [ ] **Step 1: Write the end-to-end admin flow test**

With PostgreSQL Testcontainers and an authenticated admin, exercise: create institution; create pending teacher; verify invitation outbox; accept invitation; list/filter teacher; reject pending activation; deactivate active teacher; verify session/refresh revocation; reactivate inactive teacher; block institution change after creating a room; return dashboard counts; block deletion of the used institution.

- [ ] **Step 2: Run the integration test and correct owning layers**

Run: `./mvnw -Dtest=AdminFlowIntegrationTest test`

Expected: PASS. If a contract fails, fix the service/controller that owns the behavior and keep the test assertion unchanged unless it contradicts the approved spec.

- [ ] **Step 3: Run focused administrative verification**

Run:

```bash
./mvnw -Dtest=AdminPageableFactoryTest,AdminInstitutionRepositoryTest,AdminInstitutionServiceTest,AdminInstitutionControllerTest,AdminTeacherRepositoryTest,AdminTeacherServiceTest,AdminTeacherControllerTest,AdminTeacherLifecycleIntegrationTest,AdminDashboardServiceTest,AdminDashboardControllerTest,AdminFlowIntegrationTest,SecurityConfigTest test
```

Expected: all administrative tests pass with zero failures and errors.

- [ ] **Step 4: Run full verification**

Run:

```bash
./mvnw verify
git diff --check
git status --short
```

Expected: 0 Maven failures/errors; no whitespace errors; only the intentional integration-test changes remain uncommitted.

- [ ] **Step 5: Commit atomically**

```bash
git add backend/src/test/java/com/ifsc/contacerta/controller/AdminFlowIntegrationTest.java backend/src/test/java/com/ifsc/contacerta/support/DatabaseIsolationTest.java
git commit -m "test: valida fluxo administrativo completo"
```
