# Backend Gamification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add room-scoped student ranking and seven persisted, idempotent achievements that unlock atomically with attempt finalization.

**Architecture:** Add a fixed achievement catalog and an `achievement_unlocks` persistence model, evaluated from the updated `RoomStudentProgress` inside attempt finalization. Calculate ranking with PostgreSQL window queries over active memberships, then expose ranking and achievement DTOs through a student-only controller and an authorization-scoped service.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Spring Data JPA, PostgreSQL 18, Flyway, JUnit 5, Mockito, MockMvc, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-08-29-backend-gamification-design.md`

## Global Constraints

- Follow `backend/AGENTS.md`: controllers call services, services call repositories, and JPA entities never become API contracts.
- Use explicit imports, tabs in Java/XML, Lombok `@RequiredArgsConstructor` for constructor injection, `@Getter` and protected no-arg constructors for JPA entities.
- Commands must use the Maven wrapper and be prefixed with `rtk`.
- Achievement history starts at deployment; do not backfill attempts finalized before migration V9.
- Both `SUBMITTED` and `EXPIRED` attempts are official results for pass and achievement calculations.
- Ranking and achievements are strictly room-scoped; only `ACTIVE` memberships appear in ranking.
- Ranking order is fixed: XP descending, stars descending, earliest completion ascending with nulls last, student UUID ascending.
- Ranking is computed in PostgreSQL; never load a full room ranking into application memory.
- Unlock creation is atomic and conflict-safe; do not use a check-then-insert sequence as the concurrency safeguard.
- Preserve unrelated working-tree changes and stage only files named by the current task.

---

## File Structure

### New production files

- `backend/src/main/resources/db/migration/V9__create_achievement_unlocks.sql` — table, constraints, and lookup index.
- `backend/src/main/java/com/ifsc/contacerta/model/AchievementCode.java` — stable persisted achievement codes.
- `backend/src/main/java/com/ifsc/contacerta/entity/AchievementUnlock.java` — read model for persisted unlock timestamps.
- `backend/src/main/java/com/ifsc/contacerta/repository/AchievementUnlockRepository.java` — conflict-safe insert and room/student lookup.
- `backend/src/main/java/com/ifsc/contacerta/service/AchievementUnlockService.java` — evaluates all definitions after progress updates.
- `backend/src/main/java/com/ifsc/contacerta/repository/RankingRowProjection.java` — database projection for ranked rows.
- `backend/src/main/java/com/ifsc/contacerta/repository/RankingRepository.java` — paginated window query plus self-position query.
- `backend/src/main/java/com/ifsc/contacerta/dto/gamification/RankingEntryResponse.java` — public anonymized ranking row.
- `backend/src/main/java/com/ifsc/contacerta/dto/gamification/RankingResponse.java` — page metadata, content, and stable `self` row.
- `backend/src/main/java/com/ifsc/contacerta/dto/gamification/AchievementResponse.java` — catalog item with criterion progress and unlock timestamp.
- `backend/src/main/java/com/ifsc/contacerta/dto/gamification/AchievementCollectionResponse.java` — fixed catalog wrapper.
- `backend/src/main/java/com/ifsc/contacerta/service/StudentGamificationService.java` — authorization, ranking mapping, catalog assembly, and anonymization.
- `backend/src/main/java/com/ifsc/contacerta/controller/StudentGamificationController.java` — the two student GET endpoints.

### Modified production files

- `backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java` — count distinct first pass across both final statuses.
- `backend/src/main/java/com/ifsc/contacerta/service/AttemptFinalizationService.java` — invoke achievement evaluation after applying progress.

### New or modified tests

- `backend/src/test/java/com/ifsc/contacerta/entity/AchievementUnlockPersistenceTest.java`
- `backend/src/test/java/com/ifsc/contacerta/service/AchievementUnlockServiceTest.java`
- `backend/src/test/java/com/ifsc/contacerta/service/AttemptFinalizationServiceTest.java`
- `backend/src/test/java/com/ifsc/contacerta/service/AchievementFinalizationIntegrationTest.java`
- `backend/src/test/java/com/ifsc/contacerta/repository/RankingRepositoryTest.java`
- `backend/src/test/java/com/ifsc/contacerta/service/StudentGamificationServiceTest.java`
- `backend/src/test/java/com/ifsc/contacerta/controller/StudentGamificationControllerTest.java`
- `backend/src/test/java/com/ifsc/contacerta/config/SecurityConfigTest.java`
- `backend/src/test/java/com/ifsc/contacerta/support/PostgresIntegrationTest.java` — truncate the new table during database isolation.

---

### Task 1: Persisted Achievement Catalog and Unlock Evaluation

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__create_achievement_unlocks.sql`
- Create: `backend/src/main/java/com/ifsc/contacerta/model/AchievementCode.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/AchievementUnlock.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/AchievementUnlockRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/AchievementUnlockService.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/entity/AchievementUnlockPersistenceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/AchievementUnlockServiceTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/support/PostgresIntegrationTest.java`

**Interfaces:**
- Consumes: `RoomStudentProgress.getTotalXp()`, `getPassedAssignmentCount()`, room/student identifiers, current finalized score, and authoritative `Instant finalizedAt`.
- Produces: `AchievementCode` and `AchievementUnlockService.evaluate(RoomStudentProgress progress, int scorePercent, Instant unlockedAt)` for Task 2; `AchievementUnlockRepository.findByRoomIdAndStudentId(UUID, UUID)` for Task 4.

- [ ] **Step 1: Write failing persistence and evaluation tests**

Create `AchievementUnlockPersistenceTest` extending `PostgresIntegrationTest` and annotate the test with `@Transactional`. Persist a room/student fixture, call the repository twice with the same `(roomId, studentId, FIRST_PASS)`, flush, and assert exactly one row whose `unlockedAt` matches the supplied instant. Add `achievement_unlocks` at the beginning of the shared `TRUNCATE TABLE` list so foreign keys never leak between tests.

Create `AchievementUnlockServiceTest` with a mocked repository and parameterized cases:

```java
@ParameterizedTest
@CsvSource({
		"99,0,99,false,false,false",
		"100,0,99,true,false,false",
		"500,0,99,true,true,false",
		"1000,0,99,true,true,true"
})
void deveDesbloquearLimiaresDeXp(
		int totalXp,
		int passedCount,
		int score,
		boolean xp100,
		boolean xp500,
		boolean xp1000
) {
	RoomStudentProgress progress = progress(totalXp, passedCount);
	service.evaluate(progress, score, FINALIZED_AT);
	verifyUnlock(AchievementCode.XP_100, xp100);
	verifyUnlock(AchievementCode.XP_500, xp500);
	verifyUnlock(AchievementCode.XP_1000, xp1000);
}
```

Add focused tests for `FIRST_PASS` at passed counts 0/1, `PERFECT_SCORE` at 99/100, and `PASSED_5`/`PASSED_10` at 4/5/9/10. Every positive verification must assert the exact room, student, code, and `FINALIZED_AT` passed to the repository.

- [ ] **Step 2: Run the focused tests and verify the expected red state**

Run:

```bash
rtk ./mvnw -Dtest=AchievementUnlockPersistenceTest,AchievementUnlockServiceTest test
```

Expected: test compilation fails because the achievement enum, entity, repository, and service do not exist.

- [ ] **Step 3: Add migration, enum, entity, repository, and evaluator**

Migration core:

```sql
create table achievement_unlocks (
    id uuid primary key,
    room_id uuid not null references rooms(id),
    student_id uuid not null references users(id),
    achievement_code varchar(32) not null,
    unlocked_at timestamptz not null,
    constraint uk_achievement_unlocks_room_student_code unique (room_id, student_id, achievement_code),
    constraint ck_achievement_unlocks_code check (achievement_code in (
        'FIRST_PASS', 'PERFECT_SCORE', 'XP_100', 'XP_500', 'XP_1000', 'PASSED_5', 'PASSED_10'
    ))
);

create index idx_achievement_unlocks_room_student
    on achievement_unlocks (room_id, student_id, unlocked_at);
```

Define the enum exactly as:

```java
public enum AchievementCode {
	FIRST_PASS,
	PERFECT_SCORE,
	XP_100,
	XP_500,
	XP_1000,
	PASSED_5,
	PASSED_10
}
```

Map `AchievementUnlock` with `UUID id`, lazy `Room room`, lazy `User student`, `@Enumerated(EnumType.STRING) AchievementCode code`, and `Instant unlockedAt`. Use the constructor `AchievementUnlock(Room room, User student, AchievementCode code, Instant unlockedAt)`.

In `AchievementUnlockRepository`, provide:

```java
List<AchievementUnlock> findByRoomIdAndStudentId(UUID roomId, UUID studentId);

@Modifying
@Query(value = """
		insert into achievement_unlocks (id, room_id, student_id, achievement_code, unlocked_at)
		values (:id, :roomId, :studentId, :code, :unlockedAt)
		on conflict (room_id, student_id, achievement_code) do nothing
		""", nativeQuery = true)
int insertIfAbsent(UUID id, UUID roomId, UUID studentId, String code, Instant unlockedAt);
```

Implement `AchievementUnlockService.evaluate(...)` as a fixed sequence of criterion checks. Each satisfied criterion calls `insertIfAbsent(UUID.randomUUID(), roomId, studentId, code.name(), unlockedAt)`. Do not query first; the atomic insert owns idempotency.

- [ ] **Step 4: Run focused tests and migration validation**

Run:

```bash
rtk ./mvnw -Dtest=AchievementUnlockPersistenceTest,AchievementUnlockServiceTest,DatabaseIsolationTest test
```

Expected: all selected tests pass; Flyway validates and applies migrations V1 through V9.

- [ ] **Step 5: Commit Task 1**

```bash
rtk git add backend/src/main/resources/db/migration/V9__create_achievement_unlocks.sql backend/src/main/java/com/ifsc/contacerta/model/AchievementCode.java backend/src/main/java/com/ifsc/contacerta/entity/AchievementUnlock.java backend/src/main/java/com/ifsc/contacerta/repository/AchievementUnlockRepository.java backend/src/main/java/com/ifsc/contacerta/service/AchievementUnlockService.java backend/src/test/java/com/ifsc/contacerta/entity/AchievementUnlockPersistenceTest.java backend/src/test/java/com/ifsc/contacerta/service/AchievementUnlockServiceTest.java backend/src/test/java/com/ifsc/contacerta/support/PostgresIntegrationTest.java
rtk git commit -m "feat(backend): persist achievement unlocks"
```

---

### Task 2: Atomic Achievement Unlocks During Attempt Finalization

**Files:**
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/AttemptFinalizationService.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/service/AttemptFinalizationServiceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/AchievementFinalizationIntegrationTest.java`

**Interfaces:**
- Consumes: `AchievementUnlockService.evaluate(RoomStudentProgress, int, Instant)` from Task 1.
- Produces: finalization that counts first pass across `FINAL_STATUSES` and evaluates achievements after applying the progress delta.

- [ ] **Step 1: Extend finalization tests for expired passes and evaluation ordering**

Add an `AchievementUnlockService` mock to the fixture and constructor. In the existing happy-path test, capture the `RoomStudentProgress` passed to `evaluate` and assert evaluation receives the updated object, score `50`, and the exact `finalizedAt`.

Add a regression test where an `EXPIRED` attempt passes and the repository reports one prior finalized passed attempt. Verify `passedAssignmentCount` does not increase and evaluation still runs:

```java
when(attemptRepository.countByAssignmentIdAndStudentIdAndStatusInAndPassedTrue(
		assignmentId, studentId, FINAL_STATUSES
)).thenReturn(1L);

service.finalizeAttempt(attempt, AttemptStatus.EXPIRED, finalizedAt);

assertThat(progress.getPassedAssignmentCount()).isZero();
verify(achievementUnlockService).evaluate(progress, attempt.getScorePercent(), finalizedAt);
```

Add the complementary case with count `0` and assert the first expired pass increments `passedAssignmentCount` once.

Create `AchievementFinalizationIntegrationTest` extending `PostgresIntegrationTest`. Persist a complete institution/teacher/student/room/lesson/assignment/attempt fixture with one correct snapshot answer, invoke the transactional `AttemptService.submit(...)` path, and assert in the database that the attempt is finalized, room progress is updated, and the qualifying unlock rows use the same submitted instant. Invoke submit again and assert XP, passed count, and unlock row counts remain unchanged. Add a rollback case that makes `AchievementUnlockRepository.insertIfAbsent(...)` throw from a Spring `@MockitoSpyBean` (`org.springframework.test.context.bean.override.mockito.MockitoSpyBean`); assert the attempt remains `IN_PROGRESS`, no progress mutation commits, and no unlock row exists.

- [ ] **Step 2: Run the finalization test and verify it fails**

Run:

```bash
rtk ./mvnw -Dtest=AttemptFinalizationServiceTest test
```

Expected: compilation or verification fails because the new repository method and service dependency are absent.

- [ ] **Step 3: Correct the repository query and integrate the evaluator**

Replace the single-status count with:

```java
long countByAssignmentIdAndStudentIdAndStatusInAndPassedTrue(
		UUID assignmentId,
		UUID studentId,
		List<AttemptStatus> statuses
);
```

In `AttemptFinalizationService`, inject `AchievementUnlockService`, calculate `firstPass` with `FINAL_STATUSES`, call `progress.applyResult(...)`, then call:

```java
achievementUnlockService.evaluate(progress, score, finalizedAt);
```

Keep the call inside `finalizeAttempt`; the existing transactional callers make attempt, projection, and native unlock inserts one transaction. Do not catch persistence exceptions.

While modifying this file, replace the existing inline `java.math.BigDecimal` and `java.math.RoundingMode` references with explicit imports, as required by `backend/AGENTS.md`.

- [ ] **Step 4: Run finalization and attempt regression tests**

Run:

```bash
rtk ./mvnw -Dtest=AttemptFinalizationServiceTest,AchievementFinalizationIntegrationTest,AttemptServiceAuthorizationTest,ExtraAttemptGrantServiceTest test
```

Expected: all selected tests pass, including both expired-pass cases.

- [ ] **Step 5: Commit Task 2**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java backend/src/main/java/com/ifsc/contacerta/service/AttemptFinalizationService.java backend/src/test/java/com/ifsc/contacerta/service/AttemptFinalizationServiceTest.java backend/src/test/java/com/ifsc/contacerta/service/AchievementFinalizationIntegrationTest.java
rtk git commit -m "feat(backend): unlock achievements on finalization"
```

---

### Task 3: Database-Calculated Room Ranking

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/RankingRowProjection.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/RankingRepository.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/repository/RankingRepositoryTest.java`

**Interfaces:**
- Consumes: active memberships, optional `RoomStudentProgress`, and finalized attempts from existing tables.
- Produces: `Page<RankingRowProjection> findPage(UUID roomId, Pageable pageable)` and `Optional<RankingRowProjection> findStudent(UUID roomId, UUID studentId)` for Task 4.

- [ ] **Step 1: Write PostgreSQL repository tests for the complete ordering contract**

Create `RankingRepositoryTest` extending `PostgresIntegrationTest`. Build persisted fixtures with:

- two students ordered by different XP;
- equal XP ordered by stars;
- equal XP/stars ordered by earliest `submitted_at` among finalized attempts;
- equal XP/stars/completion ordered by fixed UUID;
- an active student with no progress row;
- a removed student with high XP excluded from results;
- one student in another room excluded from results.

Assert page 0/size 2 positions and totals, a later page, and `findStudent` for a student outside the first page. Reactivate the removed membership and assert it reappears without losing progress.

- [ ] **Step 2: Run the ranking repository test and verify it fails**

Run:

```bash
rtk ./mvnw -Dtest=RankingRepositoryTest test
```

Expected: test compilation fails because `RankingRepository` and `RankingRowProjection` do not exist.

- [ ] **Step 3: Implement the projection and native window queries**

Projection getters:

```java
public interface RankingRowProjection {
	long getPosition();
	UUID getStudentId();
	String getFullName();
	int getTotalXp();
	int getTotalStars();
	int getLevel();
}
```

Use this ranked CTE in both repository queries:

```sql
with ranked as (
    select row_number() over (
               order by coalesce(progress.total_xp, 0) desc,
                        coalesce(progress.total_best_stars, 0) desc,
                        completion.first_completed_at asc nulls last,
                        student.id asc
           ) as position,
           student.id as student_id,
           student.full_name as full_name,
           coalesce(progress.total_xp, 0) as total_xp,
           coalesce(progress.total_best_stars, 0) as total_stars,
           coalesce(progress.level, 1) as level
    from room_memberships membership
    join users student on student.id = membership.student_id
    left join room_student_progress progress
           on progress.room_id = membership.room_id
          and progress.student_id = membership.student_id
    left join lateral (
        select min(attempt.submitted_at) as first_completed_at
        from attempts attempt
        join lesson_assignments assignment on assignment.id = attempt.assignment_id
        where assignment.room_id = membership.room_id
          and attempt.student_id = membership.student_id
          and attempt.status in ('SUBMITTED', 'EXPIRED')
    ) completion on true
    where membership.room_id = :roomId
      and membership.status = 'ACTIVE'
)
```

The page query selects every projection column from `ranked` ordered by `position`, accepts `Pageable`, and declares a count query over active memberships in the room. The self query appends `where student_id = :studentId`. Keep SQL aliases in snake case so Spring's projection mapping resolves getters consistently.

- [ ] **Step 4: Run repository and isolation tests**

Run:

```bash
rtk ./mvnw -Dtest=RankingRepositoryTest,DatabaseIsolationTest test
```

Expected: ordering, pagination, active membership, self lookup, and cross-room isolation tests all pass.

- [ ] **Step 5: Commit Task 3**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/repository/RankingRowProjection.java backend/src/main/java/com/ifsc/contacerta/repository/RankingRepository.java backend/src/test/java/com/ifsc/contacerta/repository/RankingRepositoryTest.java
rtk git commit -m "feat(backend): query room ranking"
```

---

### Task 4: Student Gamification Service and Public Contracts

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/gamification/RankingEntryResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/gamification/RankingResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/gamification/AchievementResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/gamification/AchievementCollectionResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/StudentGamificationService.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/StudentGamificationServiceTest.java`

**Interfaces:**
- Consumes: ranking methods from Task 3, unlock lookup from Task 1, `RoomStudentProgressRepository.findByRoomIdAndStudentId`, `RoomMembershipRepository.findByRoomIdAndStudentId`, and `UserRepository.findById`.
- Produces: `RankingResponse ranking(UUID studentId, UUID roomId, int page, int size)` and `AchievementCollectionResponse achievements(UUID studentId, UUID roomId)` for Task 5.

- [ ] **Step 1: Write service tests for authorization, mapping, anonymization, and catalog progress**

Mock all repositories and cover:

- inactive/missing membership returns `ApiException` with `404 ROOM_NOT_FOUND`;
- missing, non-student, or inactive authenticated user follows existing student-service error conventions;
- page values reach `PageRequest.of(page, size)` and the response preserves page metadata;
- the self row is fetched independently and marked `currentStudent = true` even outside page content;
- `Ana Beatriz Souza -> Ana S.`, ` João   Silva -> João S.`, and `Madonna -> Madonna`;
- the catalog returns the exact enum order;
- XP and pass progress are capped at target;
- locked entries have `unlockedAt = null`;
- `PERFECT_SCORE` current value is `1` only when its unlock row exists;
- no progress row returns XP/pass progress zero and does not fail.

Representative DTO signatures:

```java
public record RankingEntryResponse(
		long position,
		UUID studentId,
		String displayName,
		int totalXp,
		int totalStars,
		int level,
		boolean currentStudent
) {}

public record RankingResponse(
		List<RankingEntryResponse> content,
		RankingEntryResponse self,
		int page,
		int size,
		long totalElements,
		int totalPages
) {}

public record AchievementResponse(
		AchievementCode code,
		String title,
		String description,
		int current,
		int target,
		boolean unlocked,
		Instant unlockedAt
) {}
```

- [ ] **Step 2: Run the service test and verify it fails**

Run:

```bash
rtk ./mvnw -Dtest=StudentGamificationServiceTest test
```

Expected: test compilation fails because the DTOs and service are absent.

- [ ] **Step 3: Implement DTOs and the scoped read service**

Implement both methods as `@Transactional(readOnly = true)`. Reuse the established `STUDENT_NOT_FOUND`, `STUDENT_REQUIRED`, `ACCOUNT_INACTIVE`, and concealed `ROOM_NOT_FOUND` errors from `StudentMediaService`.

Build a `Map<AchievementCode, Instant>` from persisted unlocks. Define immutable metadata in a private ordered list:

```java
private static final List<AchievementDefinition> DEFINITIONS = List.of(
		new AchievementDefinition(FIRST_PASS, "Primeira aprovação", "Aprove uma lição nesta sala.", 1, ProgressKind.PASSES),
		new AchievementDefinition(PERFECT_SCORE, "Nota perfeita", "Obtenha 100% em uma tentativa.", 1, ProgressKind.UNLOCK),
		new AchievementDefinition(XP_100, "100 XP", "Conquiste 100 XP nesta sala.", 100, ProgressKind.XP),
		new AchievementDefinition(XP_500, "500 XP", "Conquiste 500 XP nesta sala.", 500, ProgressKind.XP),
		new AchievementDefinition(XP_1000, "1.000 XP", "Conquiste 1.000 XP nesta sala.", 1000, ProgressKind.XP),
		new AchievementDefinition(PASSED_5, "Cinco aprovações", "Aprove cinco lições nesta sala.", 5, ProgressKind.PASSES),
		new AchievementDefinition(PASSED_10, "Dez aprovações", "Aprove dez lições nesta sala.", 10, ProgressKind.PASSES)
);
```

Use `Math.min(sourceValue, target)` for public progress. Map names in one private `anonymize(String fullName)` method using normalized whitespace and Unicode-safe `substring(0, 1)` for the final token initial. Mark each content row by comparing its student UUID with the authenticated student's UUID.

- [ ] **Step 4: Run service and neighboring unit tests**

Run:

```bash
rtk ./mvnw -Dtest=StudentGamificationServiceTest,StudentMediaServiceTest,StudentLessonServiceTest test
```

Expected: all selected tests pass with unchanged existing student-service behavior.

- [ ] **Step 5: Commit Task 4**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/gamification backend/src/main/java/com/ifsc/contacerta/service/StudentGamificationService.java backend/src/test/java/com/ifsc/contacerta/service/StudentGamificationServiceTest.java
rtk git commit -m "feat(backend): expose gamification service contracts"
```

---

### Task 5: Student Ranking and Achievement Endpoints

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/StudentGamificationController.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/controller/StudentGamificationControllerTest.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/config/SecurityConfigTest.java`

**Interfaces:**
- Consumes: `StudentGamificationService.ranking(...)` and `.achievements(...)` from Task 4.
- Produces: `GET /student/rooms/{roomId}/ranking` and `GET /student/rooms/{roomId}/achievements`.

- [ ] **Step 1: Write controller contract and route-security tests**

Use standalone MockMvc with a `CurrentUser` resolver, following `StudentMediaControllerTest`. Stub a ranking where `self` is outside content and assert all fields and page metadata. Stub all seven achievements and assert `code`, `current`, `target`, `unlocked`, and nullable `unlockedAt` serialization.

Test defaults by verifying:

```java
verify(service).ranking(studentId, roomId, 0, 20);
```

Test explicit `?page=2&size=50`, negative page, size zero, and size 101. Invalid values must return `400` without invoking the service.

In `SecurityConfigTest`, add requests proving unauthenticated access returns `401` and an authenticated non-student role receives `403` for both routes. If the current security configuration only checks authentication, add the route matcher in Step 3 and keep the test red first.

- [ ] **Step 2: Run controller and security tests and verify they fail**

Run:

```bash
rtk ./mvnw -Dtest=StudentGamificationControllerTest,SecurityConfigTest test
```

Expected: controller tests fail to compile and role-security assertions fail until routes are implemented and protected.

- [ ] **Step 3: Implement controller validation and student route authorization**

Controller shape:

```java
@RestController
@RequestMapping("/student/rooms/{roomId}")
@RequiredArgsConstructor
public class StudentGamificationController {
	private final StudentGamificationService gamificationService;

	@GetMapping("/ranking")
	public RankingResponse ranking(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return gamificationService.ranking(currentUser.userId(), roomId, page, size);
	}

	@GetMapping("/achievements")
	public AchievementCollectionResponse achievements(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId
	) {
		return gamificationService.achievements(currentUser.userId(), roomId);
	}
}
```

Annotate the controller with `@Validated`. In `SecurityConfig`, add a matcher before `.anyRequest()` that restricts `GET /student/rooms/*/ranking` and `GET /student/rooms/*/achievements` to `hasRole("STUDENT")`. Preserve all existing public routes and authenticated-route behavior.

- [ ] **Step 4: Run endpoint, security, and full backend tests**

Run focused tests first:

```bash
rtk ./mvnw -Dtest=StudentGamificationControllerTest,SecurityConfigTest test
```

Then run the complete suite with Docker/Testcontainers available:

```bash
rtk ./mvnw test
```

Expected: `BUILD SUCCESS`, zero failures and zero errors. Record the final test count in the handoff.

- [ ] **Step 5: Run static hygiene checks**

Run:

```bash
rtk rg -n "import .*\\*|TODO|FIXME|System\\.out|printStackTrace|java\\.(util|time|math)\\." backend/src/main/java/com/ifsc/contacerta/model/AchievementCode.java backend/src/main/java/com/ifsc/contacerta/entity/AchievementUnlock.java backend/src/main/java/com/ifsc/contacerta/repository/AchievementUnlockRepository.java backend/src/main/java/com/ifsc/contacerta/repository/RankingRowProjection.java backend/src/main/java/com/ifsc/contacerta/repository/RankingRepository.java backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java backend/src/main/java/com/ifsc/contacerta/service/AchievementUnlockService.java backend/src/main/java/com/ifsc/contacerta/service/AttemptFinalizationService.java backend/src/main/java/com/ifsc/contacerta/service/StudentGamificationService.java backend/src/main/java/com/ifsc/contacerta/controller/StudentGamificationController.java backend/src/main/java/com/ifsc/contacerta/dto/gamification backend/src/test/java/com/ifsc/contacerta/entity/AchievementUnlockPersistenceTest.java backend/src/test/java/com/ifsc/contacerta/repository/RankingRepositoryTest.java backend/src/test/java/com/ifsc/contacerta/service/AchievementUnlockServiceTest.java backend/src/test/java/com/ifsc/contacerta/service/AttemptFinalizationServiceTest.java backend/src/test/java/com/ifsc/contacerta/service/AchievementFinalizationIntegrationTest.java backend/src/test/java/com/ifsc/contacerta/service/StudentGamificationServiceTest.java backend/src/test/java/com/ifsc/contacerta/controller/StudentGamificationControllerTest.java
rtk git diff --check
rtk git status --short
```

Expected: no wildcard imports, placeholders, debug output, inline fully qualified Java types, whitespace errors, or accidentally staged unrelated files. Existing unrelated working-tree changes may remain unstaged.

- [ ] **Step 6: Commit Task 5**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/controller/StudentGamificationController.java backend/src/main/java/com/ifsc/contacerta/config/SecurityConfig.java backend/src/test/java/com/ifsc/contacerta/controller/StudentGamificationControllerTest.java backend/src/test/java/com/ifsc/contacerta/config/SecurityConfigTest.java
rtk git commit -m "feat(backend): expose student gamification endpoints"
```

---

## Final Verification

- [ ] Confirm commits contain only planned files with `rtk git log --stat --oneline origin/main..HEAD`.
- [ ] Confirm migration V9 recreates a clean database through the full Testcontainers suite.
- [ ] Confirm all seven catalog entries are returned in stable order for a student with no progress.
- [ ] Confirm ranking includes active zero-progress students and excludes removed students.
- [ ] Confirm `self` is returned outside the visible page.
- [ ] Confirm a repeated or concurrent achievement evaluation cannot duplicate unlocks.
- [ ] Confirm both `SUBMITTED` and `EXPIRED` passing attempts increment distinct-pass progress at most once.
- [ ] Confirm unrelated local changes remain unstaged and unmodified.
