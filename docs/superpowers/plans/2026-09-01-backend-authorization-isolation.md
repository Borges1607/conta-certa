# Backend Authorization and Institutional Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure every protected backend resource is loaded through the authenticated user's ownership, institution, or active-membership scope without revealing cross-tenant resource existence.

**Architecture:** Keep authentication in Spring Security and enforce domain authorization in services through explicit Spring Data repository methods. Each protected lookup includes the complete ownership chain; missing and out-of-scope resources share the same stable `404` contract, while role failures remain `403`.

**Tech Stack:** Java 21, Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, Flyway, JUnit 5, Mockito, Maven Wrapper

**Spec:** `docs/superpowers/specs/2026-09-01-backend-authorization-isolation-design.md`

## Global Constraints

- Controllers delegate to services and never access repositories directly.
- Do not expose JPA entities as API contracts.
- Use explicit imports; do not use wildcard imports or fully qualified type names in fields, signatures, or implementations.
- Preserve the existing Lombok and tab-indentation conventions.
- Return `401` for invalid authentication, `403` for wrong-role access, and indistinguishable `404` responses for missing or out-of-scope resources.
- Evaluate `409` and `422` business rules only after authorization succeeds.
- Do not add migrations, Hibernate tenant filters, teacher sharing, or a general-purpose authorization framework.
- Write each regression test before its implementation change and run the focused test both red and green.

---

### Task 1: Make Room Membership Boundaries Indistinguishable

**Files:**
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/RoomRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/RoomMembershipRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/RoomMembershipService.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/RoomMembershipServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/repository/RoomRepositoryTest.java`

**Interfaces:**
- Produces: `Optional<Room> findByJoinCodeHashAndInstitutionId(String joinCodeHash, UUID institutionId)`
- Produces: `Optional<RoomMembership> findByRoomIdAndStudentIdAndStatus(UUID roomId, UUID studentId, MembershipStatus status)`
- Produces: stable `ROOM_NOT_FOUND` and `MEMBERSHIP_NOT_FOUND` `ApiException` contracts.

- [ ] **Step 1: Write failing service tests for cross-institution join and missing removal membership**

Add tests that mock an active student, return `Optional.empty()` from `findByJoinCodeHashAndInstitutionId`, and assert `HttpStatus.NOT_FOUND` plus code `ROOM_NOT_FOUND`. Add a removal test where the teacher owns the room but `findByRoomIdAndStudentId` is empty, asserting `NOT_FOUND/MEMBERSHIP_NOT_FOUND` instead of `NoSuchElementException`.

```java
assertThatThrownBy(() -> service.join(studentId, "ABC123"))
		.isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(exception.getCode()).isEqualTo("ROOM_NOT_FOUND");
		});
verify(roomRepository).findByJoinCodeHashAndInstitutionId(joinCodeHash, institutionId);
```

- [ ] **Step 2: Run the focused tests and confirm the new contract fails**

Run: `cd backend && ./mvnw -Dtest=RoomMembershipServiceTest test`

Expected: FAIL because the institution-scoped repository method and stable membership exception are not implemented.

- [ ] **Step 3: Add the scoped room query and replace unscoped/implicit failures**

Declare `findByJoinCodeHashAndInstitutionId`. In `join`, query with the authenticated student's institution and remove the separate `INSTITUTION_MISMATCH` branch. In `remove`, replace both bare `orElseThrow()` calls with explicit `TEACHER_NOT_FOUND` or `MEMBERSHIP_NOT_FOUND` `ApiException` values. Make `requireStudent` and `requireTeacher` fail explicitly when the user is absent instead of silently succeeding.

- [ ] **Step 4: Add a repository test proving the institution predicate**

Persist two institutions and query one room with the other institution ID. Assert the result is empty, then query with the correct institution ID and assert the room is returned.

- [ ] **Step 5: Run room membership and repository tests**

Run: `cd backend && ./mvnw -Dtest=RoomMembershipServiceTest,RoomRepositoryTest test`

Expected: PASS.

- [ ] **Step 6: Commit the room boundary change**

```bash
git add backend/src/main/java/com/ifsc/contacerta/repository/RoomRepository.java backend/src/main/java/com/ifsc/contacerta/repository/RoomMembershipRepository.java backend/src/main/java/com/ifsc/contacerta/service/RoomMembershipService.java backend/src/test/java/com/ifsc/contacerta/service/RoomMembershipServiceTest.java backend/src/test/java/com/ifsc/contacerta/repository/RoomRepositoryTest.java
git commit -m "fix: isola acesso a matriculas por instituicao"
```

### Task 2: Harden Teacher-Owned Content Chains

**Files:**
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/MediaAssignmentRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/LessonAssignmentService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/MediaAssignmentService.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/LessonAssignmentServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/MediaAssignmentServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/repository/LessonAssignmentRepositoryTest.java`

**Interfaces:**
- Produces: `List<LessonAssignment> findByRoomIdAndRoomTeacherIdForUpdate(UUID roomId, UUID teacherId)` using a pessimistic-write query.
- Produces: `List<MediaAssignment> findByRoomIdAndRoomTeacherIdOrderByPositionAsc(UUID roomId, UUID teacherId)`.
- Consumes: existing `findByIdAndRoomIdAndRoomTeacherId` methods for single-resource lookups.

- [ ] **Step 1: Write failing tests for mixed-owner reorder and mutation requests**

Cover a teacher supplying an owned `roomId` with an assignment ID from another room, and a teacher reordering a room owned by another teacher. Assert the relevant `LESSON_ASSIGNMENT_NOT_FOUND`, `MEDIA_ASSIGNMENT_NOT_FOUND`, or `ROOM_NOT_FOUND` `404` and verify no entity mutation or save occurs.

- [ ] **Step 2: Run the focused service tests and verify failure**

Run: `cd backend && ./mvnw -Dtest=LessonAssignmentServiceTest,MediaAssignmentServiceTest test`

Expected: at least one new mixed-chain assertion FAILS because bulk queries are scoped only by `roomId`.

- [ ] **Step 3: Scope bulk assignment queries by the authenticated teacher**

Replace teacher-side `findByRoomIdForUpdate(roomId)` and `findByRoomIdOrderByPositionAsc(roomId)` calls with methods whose query also requires `room.teacher.id = :teacherId`. Keep student-side listing methods unchanged because they are guarded by active membership.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select assignment from LessonAssignment assignment "
		+ "where assignment.room.id = :roomId and assignment.room.teacher.id = :teacherId")
List<LessonAssignment> findByRoomIdAndRoomTeacherIdForUpdate(
		@Param("roomId") UUID roomId,
		@Param("teacherId") UUID teacherId
);
```

- [ ] **Step 4: Add the repository regression for a foreign teacher**

Persist assignments for two teachers and assert the scoped locking query returns only the authenticated teacher's room assignments.

- [ ] **Step 5: Run content-chain tests**

Run: `cd backend && ./mvnw -Dtest=LessonAssignmentServiceTest,MediaAssignmentServiceTest,LessonAssignmentRepositoryTest test`

Expected: PASS.

- [ ] **Step 6: Commit the teacher content-chain change**

```bash
git add backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java backend/src/main/java/com/ifsc/contacerta/repository/MediaAssignmentRepository.java backend/src/main/java/com/ifsc/contacerta/service/LessonAssignmentService.java backend/src/main/java/com/ifsc/contacerta/service/MediaAssignmentService.java backend/src/test/java/com/ifsc/contacerta/service/LessonAssignmentServiceTest.java backend/src/test/java/com/ifsc/contacerta/service/MediaAssignmentServiceTest.java backend/src/test/java/com/ifsc/contacerta/repository/LessonAssignmentRepositoryTest.java
git commit -m "fix: protege cadeias de conteudo do professor"
```

### Task 3: Enforce Stored-File Ownership at the Storage Boundary

**Files:**
- Modify: `backend/src/main/java/com/ifsc/contacerta/storage/FileStorage.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/storage/PostgresFileStorage.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/MaterialService.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/MaterialServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/storage/PostgresFileStorageTest.java`

**Interfaces:**
- Produces: `Optional<StoredFile> findByIdAndOwnerTeacherId(UUID fileId, UUID teacherId)` on `FileStorage`.
- Consumes: existing `StoredFileRepository.findByIdAndOwnerTeacherId(UUID, UUID)`.

- [ ] **Step 1: Write a failing material test that requires an ownership-scoped storage call**

Mock `fileStorage.findByIdAndOwnerTeacherId(fileId, teacherId)` as empty and assert material creation returns `404 FILE_NOT_FOUND`. Verify the unscoped `findById(fileId)` is never used by `MaterialService`.

- [ ] **Step 2: Run the material test and confirm the interface is missing**

Run: `cd backend && ./mvnw -Dtest=MaterialServiceTest test`

Expected: test compilation FAILS because `FileStorage.findByIdAndOwnerTeacherId` does not exist.

- [ ] **Step 3: Add and use the scoped storage operation**

Add the interface method, delegate directly to `StoredFileRepository.findByIdAndOwnerTeacherId`, and change `MaterialService.requireOwnedFile` to use it without loading a foreign file first.

```java
private StoredFile requireOwnedFile(UUID teacherId, UUID fileId) {
	return fileStorage.findByIdAndOwnerTeacherId(fileId, teacherId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "File was not found."));
}
```

- [ ] **Step 4: Test the PostgreSQL storage adapter delegation**

Assert `PostgresFileStorage.findByIdAndOwnerTeacherId(fileId, teacherId)` returns the repository result and calls the exact scoped repository method.

- [ ] **Step 5: Run file and material tests**

Run: `cd backend && ./mvnw -Dtest=MaterialServiceTest,PostgresFileStorageTest test`

Expected: PASS.

- [ ] **Step 6: Commit the file ownership change**

```bash
git add backend/src/main/java/com/ifsc/contacerta/storage/FileStorage.java backend/src/main/java/com/ifsc/contacerta/storage/PostgresFileStorage.java backend/src/main/java/com/ifsc/contacerta/service/MaterialService.java backend/src/test/java/com/ifsc/contacerta/service/MaterialServiceTest.java backend/src/test/java/com/ifsc/contacerta/storage/PostgresFileStorageTest.java
git commit -m "fix: consulta arquivos pelo professor proprietario"
```

### Task 4: Scope Student Lesson and Media Reads to Active Membership

**Files:**
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/MediaAssignmentRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/StudentLessonService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/StudentMediaService.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/StudentLessonServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/StudentMediaServiceTest.java`

**Interfaces:**
- Produces: `Optional<LessonAssignment> findAccessibleByRoomIdAndLessonIdAndStudentId(UUID roomId, UUID lessonId, UUID studentId, MembershipStatus membershipStatus)`.
- Produces: `List<LessonAssignment> findAccessibleByRoomIdAndStudentIdAndStatusOrderByPositionAsc(UUID roomId, UUID studentId, MembershipStatus membershipStatus, ContentStatus assignmentStatus)`.
- Produces: `List<MediaAssignment> findAccessibleByRoomIdAndStudentIdOrderByPositionAsc(UUID roomId, UUID studentId, MembershipStatus membershipStatus)`.
- Consumes: authenticated `studentId`; no student identifier from request payloads.

- [ ] **Step 1: Write failing tests for absent, removed, and cross-institution memberships**

For lesson detail/history and room media lists, return no result from the new scoped query and assert `404 ROOM_NOT_FOUND` for inaccessible rooms or `404 ASSIGNMENT_NOT_FOUND` for inaccessible nested lessons. Add positive tests using an active membership.

- [ ] **Step 2: Run the student lesson and media tests red**

Run: `cd backend && ./mvnw -Dtest=StudentLessonServiceTest,StudentMediaServiceTest test`

Expected: FAIL because current reads separately validate membership and then query assignments by room only.

- [ ] **Step 3: Add membership-scoped repository queries and use them in services**

Use JPQL joins from assignment to room memberships with `membership.student.id`, `membership.status`, and matching room. Keep `ContentStatus.PUBLISHED` in student list/detail queries. Return stable `404` before evaluating availability windows or completion state.

```java
@Query("""
		select assignment from LessonAssignment assignment
		join RoomMembership membership on membership.room = assignment.room
		where assignment.room.id = :roomId
		and assignment.lesson.id = :lessonId
		and assignment.status = com.ifsc.contacerta.model.ContentStatus.PUBLISHED
		and membership.student.id = :studentId
		and membership.status = :membershipStatus
		""")
Optional<LessonAssignment> findAccessibleByRoomIdAndLessonIdAndStudentId(
		UUID roomId, UUID lessonId, UUID studentId, MembershipStatus membershipStatus
);
```

- [ ] **Step 4: Run the student access tests green**

Run: `cd backend && ./mvnw -Dtest=StudentLessonServiceTest,StudentMediaServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit the student content change**

```bash
git add backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java backend/src/main/java/com/ifsc/contacerta/repository/MediaAssignmentRepository.java backend/src/main/java/com/ifsc/contacerta/service/StudentLessonService.java backend/src/main/java/com/ifsc/contacerta/service/StudentMediaService.java backend/src/test/java/com/ifsc/contacerta/service/StudentLessonServiceTest.java backend/src/test/java/com/ifsc/contacerta/service/StudentMediaServiceTest.java
git commit -m "fix: limita conteudo do aluno a matriculas ativas"
```

### Task 5: Protect Attempt, Answer, and Snapshot Chains

**Files:**
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/AttemptQuestionSnapshotRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/AttemptService.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/AttemptServiceAuthorizationTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/repository/AttemptRepositoryTest.java`

**Interfaces:**
- Produces: `Optional<LessonAssignment> findAccessibleByIdAndStudentId(UUID assignmentId, UUID studentId, MembershipStatus status)`.
- Produces: `Optional<Attempt> findByIdAndStudentIdForUpdate(UUID attemptId, UUID studentId)`.
- Produces: `Optional<AttemptQuestionSnapshot> findByIdAndAttemptIdAndAttemptStudentId(UUID snapshotId, UUID attemptId, UUID studentId)`.

- [ ] **Step 1: Write failing authorization tests for cross-student and mixed-attempt identifiers**

Cover starting an assignment without an active membership, recording an answer with a snapshot from another attempt, and submitting/reviewing another student's attempt. Assert `ASSIGNMENT_NOT_FOUND`, `QUESTION_SNAPSHOT_NOT_FOUND`, or `ATTEMPT_NOT_FOUND` with `404`, and verify scoring/finalization is not invoked.

- [ ] **Step 2: Run attempt authorization tests and verify failure**

Run: `cd backend && ./mvnw -Dtest=AttemptServiceAuthorizationTest test`

Expected: FAIL where `findById`, snapshot-only lookup, or unscoped lock lookup currently permits loading before validation.

- [ ] **Step 3: Implement scoped attempt-chain queries**

Load start assignments through the active membership query. Replace snapshot lookup with the full snapshot + attempt + student predicate. Replace the write lock lookup with `findByIdAndStudentIdForUpdate`; do not load and filter another student's attempt in memory.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select attempt from Attempt attempt where attempt.id = :attemptId and attempt.student.id = :studentId")
Optional<Attempt> findByIdAndStudentIdForUpdate(
		@Param("attemptId") UUID attemptId,
		@Param("studentId") UUID studentId
);
```

- [ ] **Step 4: Add repository tests for both matching and foreign student IDs**

Persist attempts for two students and assert the scoped lock query returns only the matching owner. Persist snapshots for separate attempts and assert a mixed snapshot/attempt/student tuple returns empty.

- [ ] **Step 5: Run attempt tests**

Run: `cd backend && ./mvnw -Dtest=AttemptServiceAuthorizationTest,AttemptRepositoryTest test`

Expected: PASS.

- [ ] **Step 6: Commit the attempt-chain protection**

```bash
git add backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java backend/src/main/java/com/ifsc/contacerta/repository/AttemptQuestionSnapshotRepository.java backend/src/main/java/com/ifsc/contacerta/service/AttemptService.java backend/src/test/java/com/ifsc/contacerta/service/AttemptServiceAuthorizationTest.java backend/src/test/java/com/ifsc/contacerta/repository/AttemptRepositoryTest.java
git commit -m "fix: protege propriedade das tentativas"
```

### Task 6: Scope Extra Grants, Gamification, and Teacher Reports

**Files:**
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/RoomMembershipRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/ExtraAttemptGrantService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/StudentGamificationService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/TeacherReportFilterFactory.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/ExtraAttemptGrantServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/StudentGamificationServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/TeacherReportFilterFactoryTest.java`

**Interfaces:**
- Produces: `Optional<LessonAssignment> findByIdAndRoomTeacherId(UUID assignmentId, UUID teacherId)`.
- Consumes: `findByIdAndRoomTeacherId(UUID assignmentId, UUID teacherId)` for grants.
- Consumes: active membership methods that scope both room and student.
- Produces: no raw `findById(assignmentId)` followed by in-memory ownership filtering.

- [ ] **Step 1: Write failing tests for cross-owner grants and report filter ID mixing**

Assert a foreign teacher cannot grant attempts for an existing assignment; a student from a different room cannot be targeted; report filters cannot combine an owned room with a lesson or student outside that room. Assert all are stable `404` responses. Add a gamification test for removed membership.

- [ ] **Step 2: Run the three focused service test classes red**

Run: `cd backend && ./mvnw -Dtest=ExtraAttemptGrantServiceTest,StudentGamificationServiceTest,TeacherReportFilterFactoryTest test`

Expected: at least the new repository-interaction assertions FAIL because some ownership checks occur after broad loads.

- [ ] **Step 3: Replace broad loads with scoped repository calls**

Use the teacher-scoped assignment method in grants. Use a single active-membership lookup for student validation. Ensure report room, lesson, and student predicates include the authenticated teacher and the selected room; retain dedicated report projections instead of adding `Specification`.

- [ ] **Step 4: Run grants, gamification, and report tests green**

Run: `cd backend && ./mvnw -Dtest=ExtraAttemptGrantServiceTest,StudentGamificationServiceTest,TeacherReportFilterFactoryTest,TeacherReportServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit the reporting and support-flow protection**

```bash
git add backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java backend/src/main/java/com/ifsc/contacerta/repository/RoomMembershipRepository.java backend/src/main/java/com/ifsc/contacerta/service/ExtraAttemptGrantService.java backend/src/main/java/com/ifsc/contacerta/service/StudentGamificationService.java backend/src/main/java/com/ifsc/contacerta/service/TeacherReportFilterFactory.java backend/src/test/java/com/ifsc/contacerta/service/ExtraAttemptGrantServiceTest.java backend/src/test/java/com/ifsc/contacerta/service/StudentGamificationServiceTest.java backend/src/test/java/com/ifsc/contacerta/service/TeacherReportFilterFactoryTest.java
git commit -m "fix: isola concessoes e relatorios por proprietario"
```

### Task 7: Lock the HTTP Authorization Matrix and Verify the Backend

**Files:**
- Modify: `backend/src/test/java/com/ifsc/contacerta/controller/StudentRoomControllerTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/controller/StudentAttemptControllerTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/controller/TeacherLessonAssignmentControllerTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/controller/TeacherMediaAssignmentControllerTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/controller/TeacherReportControllerSecurityTest.java`

**Interfaces:**
- Consumes: stable service `ApiException` contracts from Tasks 1–6.
- Produces: controller-level regression coverage for `401`, `403`, and `404` without leaking resource ownership.

- [ ] **Step 1: Add parameterized or focused MockMvc tests for the HTTP matrix**

Cover unauthenticated request → `401`, authenticated wrong role → `403`, and service-reported foreign/missing resource → `404` with the expected problem code. For room join, assert both invalid and cross-institution scenarios serialize as `ROOM_NOT_FOUND`.

```java
mockMvc.perform(post("/student/rooms/join")
		.with(authentication(studentAuthentication))
		.contentType(MediaType.APPLICATION_JSON)
		.content("{\"joinCode\":\"FOREIGN\"}"))
		.andExpect(status().isNotFound())
		.andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
```

- [ ] **Step 2: Run controller security tests**

Run: `cd backend && ./mvnw -Dtest=StudentRoomControllerTest,StudentAttemptControllerTest,TeacherLessonAssignmentControllerTest,TeacherMediaAssignmentControllerTest,TeacherReportControllerSecurityTest test`

Expected: PASS after Tasks 1–6 with the existing global exception serialization.

- [ ] **Step 3: Scan production services for remaining broad protected lookups**

Run: `cd backend && rg -n 'findById\\(' src/main/java/com/ifsc/contacerta/service --glob '*.java'`

Expected: every remaining occurrence loads the authenticated user itself or belongs to an explicitly administrative flow. Replace any remaining protected domain-resource lookup with a scoped repository method and add a focused regression test before continuing.

- [ ] **Step 4: Run the complete verification suite**

Run: `cd backend && ./mvnw verify`

Expected: BUILD SUCCESS with zero test failures and zero errors.

- [ ] **Step 5: Check formatting and worktree scope**

Run: `git diff --check && git status --short`

Expected: no whitespace errors and only intentional authorization files changed.

- [ ] **Step 6: Commit the HTTP contract coverage**

```bash
git add backend/src/test/java/com/ifsc/contacerta/controller/StudentRoomControllerTest.java backend/src/test/java/com/ifsc/contacerta/controller/StudentAttemptControllerTest.java backend/src/test/java/com/ifsc/contacerta/controller/TeacherLessonAssignmentControllerTest.java backend/src/test/java/com/ifsc/contacerta/controller/TeacherMediaAssignmentControllerTest.java backend/src/test/java/com/ifsc/contacerta/controller/TeacherReportControllerSecurityTest.java
git commit -m "test: cobre matriz de autorizacao do backend"
```
