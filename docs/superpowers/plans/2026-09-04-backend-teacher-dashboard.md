# Dashboard do professor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** disponibilizar `GET /teacher/dashboard` com contagens isoladas do professor autenticado.

**Architecture:** `TeacherDashboardService` valida professor ativo e compõe um DTO de records aninhados. Repositórios fornecem contagens agregadas e sempre recebem o `teacherId`, sem carregar entidades completas nem usar escopo apenas por instituição.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, PostgreSQL, Maven, JUnit 5, Mockito e Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-04-backend-teacher-dashboard-design.md`

## Global Constraints

- Controller → service → repository; controllers não acessam repositórios diretamente.
- Não expor entidades JPA, e-mails, nomes de alunos ou campos administrativos.
- Usar imports explícitos, tabulação em Java, `@RequiredArgsConstructor` e transações `readOnly`.
- Todas as contagens devem ser limitadas ao `teacherId` autenticado.
- Nenhuma migration é necessária.

---

### Task 1: Add teacher-scoped aggregate repository methods

**Files:**
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/RoomRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/RoomMembershipRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/LessonRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/repository/TeacherDashboardRepositoryTest.java`

**Interfaces:** produce `countByTeacherId`, room archive counts, distinct student count through `membership.room.teacher.id`, membership status count through the same relation, and status counts for lessons/assignments. Methods return `long` and accept `UUID teacherId` (plus `ContentStatus`/`MembershipStatus` where needed).

- [ ] **Step 1: Write the failing repository test.** Create a PostgreSQL integration test with two teachers, rooms, memberships, lessons and assignments; assert each teacher sees only their own rows, archived rooms remain in `total`, and one student in two rooms is distinct in `students.total` but counted twice in active memberships.
- [ ] **Step 2: Run the test to verify it fails.** Run `rtk ./mvnw -Dtest=TeacherDashboardRepositoryTest test` from `backend`; expected failure is missing repository methods/test class.
- [ ] **Step 3: Implement aggregate methods.** Use derived methods where expressible (`countByTeacherId`, `countByTeacherIdAndArchivedAtIsNull`, `countByTeacherIdAndArchivedAtIsNotNull`, `countByTeacherIdAndStatus`) and explicit JPQL `count(distinct membership.student.id)` / membership status queries joining `membership.room.teacher.id`. Keep all return types `long`.
- [ ] **Step 4: Run the test to verify it passes.** Run the same Maven command and confirm all repository isolation assertions pass.
- [ ] **Step 5: Commit.** `rtk git add backend/src/main/java/com/ifsc/contacerta/repository backend/src/test/java/com/ifsc/contacerta/repository/TeacherDashboardRepositoryTest.java && rtk git commit -m "feat: adiciona contagens do dashboard do professor"`

### Task 2: Implement the dashboard service and public DTO

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/teacher/TeacherDashboardResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/TeacherDashboardService.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/TeacherDashboardServiceTest.java`

**Interfaces:** consume the repository methods from Task 1 and `UserRepository.findById(UUID)`; produce `TeacherDashboardService.get(UUID teacherId)` returning `TeacherDashboardResponse`.

- [ ] **Step 1: Write the failing service tests.** Mockito-test `get` for a teacher with zero rows and a populated teacher. Assert room total/active/archived, distinct students/active memberships, lesson total/published/draft and assignment total/published. Add tests for missing user (`404 TEACHER_NOT_FOUND`), wrong role (`403 TEACHER_REQUIRED`) and inactive account (`403 ACCOUNT_INACTIVE`).
- [ ] **Step 2: Run the tests to verify they fail.** Run `rtk ./mvnw -Dtest=TeacherDashboardServiceTest test`; expected compilation failure is missing DTO/service.
- [ ] **Step 3: Implement minimal DTO/service.** Add records `RoomCounts(long total,long active,long archived)`, `StudentCounts(long total,long activeMemberships)`, `LessonCounts(long total,long published,long draft)` and `AssignmentCounts(long total,long published)`. Validate `Role.TEACHER` and `AccountStatus.ACTIVE`, then call every scoped aggregate once and construct the envelope inside `@Transactional(readOnly = true)`.
- [ ] **Step 4: Run tests to verify green.** Run the same command and confirm all service cases pass.
- [ ] **Step 5: Commit.** `rtk git add backend/src/main/java/com/ifsc/contacerta/dto/teacher/TeacherDashboardResponse.java backend/src/main/java/com/ifsc/contacerta/service/TeacherDashboardService.java backend/src/test/java/com/ifsc/contacerta/service/TeacherDashboardServiceTest.java && rtk git commit -m "feat: compoe dashboard do professor"`

### Task 3: Expose the HTTP endpoint and verify security integration

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/TeacherDashboardController.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/controller/TeacherDashboardControllerTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/config/SecurityConfigTest.java`

**Interfaces:** controller maps `GET /teacher/dashboard`, resolves `CurrentUser`, delegates to `TeacherDashboardService.get(currentUser.userId())`, and returns the DTO.

- [ ] **Step 1: Write failing controller/security tests.** Standalone MockMvc verifies UUID delegation and JSON serialization. Add integration assertions that unauthenticated requests return `401`, students return `403 TEACHER_REQUIRED`, and an authenticated teacher receives `200` with the aggregate envelope.
- [ ] **Step 2: Run tests to verify they fail.** Run `rtk ./mvnw -Dtest=TeacherDashboardControllerTest,SecurityConfigTest test`; expected failure is the missing controller mapping.
- [ ] **Step 3: Implement the controller.** Add `@RestController`, `@RequestMapping("/teacher/dashboard")`, `@RequiredArgsConstructor`, and `@GetMapping` method receiving `@AuthenticationPrincipal CurrentUser`; do not add repository access or duplicate authorization.
- [ ] **Step 4: Run focused regression.** Run `rtk ./mvnw -Dtest=TeacherDashboardControllerTest,SecurityConfigTest,TeacherRoomControllerTest,TeacherLessonControllerTest,TeacherLessonAssignmentControllerTest test` and confirm green.
- [ ] **Step 5: Commit.** `rtk git diff --check && rtk git add backend/src/main/java/com/ifsc/contacerta/controller/TeacherDashboardController.java backend/src/test/java/com/ifsc/contacerta/controller/TeacherDashboardControllerTest.java backend/src/test/java/com/ifsc/contacerta/config/SecurityConfigTest.java && rtk git commit -m "feat: expoe dashboard do professor"`

### Task 4: Verify the branch

- [ ] **Step 1:** Run `rtk ./mvnw -Dtest=TeacherDashboardRepositoryTest,TeacherDashboardServiceTest,TeacherDashboardControllerTest,SecurityConfigTest,TeacherRoomControllerTest,TeacherLessonControllerTest,TeacherLessonAssignmentControllerTest test`.
- [ ] **Step 2:** Run `rtk ./mvnw -DskipTests package`.
- [ ] **Step 3:** Run `rtk git diff --check origin/main...HEAD`, `rtk git status --short`, and `rtk git log --oneline origin/main..HEAD`.
- [ ] **Step 4:** Run `rtk ./mvnw test`; if the known shared Testcontainers lifecycle issue interrupts the full suite, report it separately without changing production behavior.
