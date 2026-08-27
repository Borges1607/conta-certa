# Backend Lesson Assignments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the authenticated teacher API for creating, listing, configuring, deleting, and reordering lesson assignments in a room.

**Architecture:** Persist assignments as an optimistic-locked aggregate owned through `Room`, with direct ownership-scoped repository queries. Keep authorization, validation, position normalization, and JSON presence semantics in a transactional `LessonAssignmentService`; the controller only maps the five HTTP routes.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA/Hibernate, Flyway, PostgreSQL, Jackson 3, Jakarta Validation, JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-08-27-backend-lesson-assignments-design.md`

## Global Constraints

- Follow `backend/AGENTS.md`: explicit imports, technical layers, DTO-only API contracts, Lombok without `@Data`, and selective use of JPA `Specification`.
- All teacher and tenant scope comes from the authenticated user; ownership failures return `404`.
- Omitted create limits default to 30 minutes and 3 attempts; explicit JSON `null` means unlimited.
- Positions are one-based, unique, and contiguous inside each room.
- Every update, deletion, and reorder validates optimistic-lock versions.
- Extra-attempt grants and the student attempt/progress subsystem are outside this plan.
- Run all shell commands through `rtk` and use `backend/mvnw` from the backend directory.
- Each task ends in its own atomic commit and must leave the focused tests green.

## File Structure

- `model/ContentStatus.java`: reuse the existing `DRAFT`, `PUBLISHED`, and `ARCHIVED` values.
- `entity/LessonAssignment.java`: persistence mapping and state transitions only.
- `repository/LessonAssignmentRepository.java`: ordered ownership-scoped queries and pessimistic write loading.
- `dto/assignment/*.java`: create, patch, reorder, and response contracts.
- `service/LessonAssignmentService.java`: authorization, JSON field parsing, validation, transactional mutations, and response mapping.
- `controller/TeacherLessonAssignmentController.java`: five teacher routes.
- `db/migration/V6__create_lesson_assignment_table.sql`: constraints and indexes.
- Matching tests under `src/test/java/com/ifsc/contacerta/`.

---

### Task 1: Persist the Lesson Assignment Aggregate

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__create_lesson_assignment_table.sql`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/LessonAssignment.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/entity/LessonAssignmentPersistenceTest.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/repository/LessonAssignmentRepositoryTest.java`

**Interfaces:**
- Consumes: `Room`, `Lesson`, and `ContentStatus`.
- Produces: `LessonAssignment(Room, Lesson, int, Instant, Instant, Integer, Integer, Integer, boolean, boolean)`, `configure(ContentStatus, Instant, Instant, Integer, Integer, Integer, boolean, boolean)`, `moveTo(int)`, `publish()`, `archive()`, and ordered repository methods.

- [ ] **Step 1: Write failing persistence and repository tests**

```java
@Test
void devePersistirAtribuicaoComConfiguracaoPadrao() {
	LessonAssignment assignment = new LessonAssignment(
			room, lesson, 1, null, null, 30, 3, null, true, true
	);
	entityManager.persist(assignment);
	entityManager.flush();
	entityManager.clear();

	LessonAssignment persisted = entityManager.find(LessonAssignment.class, assignment.getId());
	assertThat(persisted.getRoom().getId()).isEqualTo(room.getId());
	assertThat(persisted.getLesson().getId()).isEqualTo(lesson.getId());
	assertThat(persisted.getStatus()).isEqualTo(ContentStatus.DRAFT);
	assertThat(persisted.getPosition()).isEqualTo(1);
	assertThat(persisted.getTimeLimitMinutes()).isEqualTo(30);
	assertThat(persisted.getMaxAttempts()).isEqualTo(3);
}

@Test
void deveListarAtribuicoesDaSalaNaOrdem() {
	assertThat(repository.findByRoomIdAndRoomTeacherIdOrderByPositionAsc(room.getId(), teacher.getId()))
			.extracting(LessonAssignment::getPosition)
			.containsExactly(1, 2);
}
```

- [ ] **Step 2: Run the tests and confirm the missing types fail compilation**

Run: `rtk ./mvnw -Dtest=LessonAssignmentPersistenceTest,LessonAssignmentRepositoryTest test`

Expected: FAIL because `LessonAssignment` and `LessonAssignmentRepository` do not exist.

- [ ] **Step 3: Add migration, entity, and repository**

The migration must create the approved columns and these constraints:

```sql
constraint uk_lesson_assignments_room_lesson unique (room_id, lesson_id),
constraint uk_lesson_assignments_room_position unique (room_id, position),
constraint ck_lesson_assignments_position check (position > 0),
constraint ck_lesson_assignments_status check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
constraint ck_lesson_assignments_dates check (available_from is null or due_at is null or due_at > available_from),
constraint ck_lesson_assignments_time_limit check (time_limit_minutes is null or time_limit_minutes > 0),
constraint ck_lesson_assignments_max_attempts check (max_attempts is null or max_attempts > 0),
constraint ck_lesson_assignments_question_count check (question_count is null or question_count > 0)
```

Use the unique `(room_id, position)` constraint as the ordered-room index, and add indexes `(lesson_id)` and `(room_id, status, available_from)`. Map `room_id` and `lesson_id` as lazy, non-null `@ManyToOne`; map `version` with `@Version`. Use `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, UUID generation in the domain constructor, and `@PrePersist`/`@PreUpdate` timestamps following `Lesson`.

Repository signatures:

```java
List<LessonAssignment> findByRoomIdAndRoomTeacherIdOrderByPositionAsc(UUID roomId, UUID teacherId);
Optional<LessonAssignment> findByIdAndRoomIdAndRoomTeacherId(UUID id, UUID roomId, UUID teacherId);
boolean existsByRoomIdAndLessonId(UUID roomId, UUID lessonId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select assignment from LessonAssignment assignment where assignment.room.id = :roomId order by assignment.position")
List<LessonAssignment> findByRoomIdForUpdate(@Param("roomId") UUID roomId);
```

- [ ] **Step 4: Run focused persistence tests**

Run: `rtk ./mvnw -Dtest=LessonAssignmentPersistenceTest,LessonAssignmentRepositoryTest test`

Expected: PASS with Flyway at version 6 and no Hibernate schema-validation error.

- [ ] **Step 5: Commit the persistence slice**

```bash
rtk git add backend/src/main/resources/db/migration/V6__create_lesson_assignment_table.sql backend/src/main/java/com/ifsc/contacerta/entity/LessonAssignment.java backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java backend/src/test/java/com/ifsc/contacerta/entity/LessonAssignmentPersistenceTest.java backend/src/test/java/com/ifsc/contacerta/repository/LessonAssignmentRepositoryTest.java
rtk git commit -m "feat: persiste atribuicoes de licoes"
```

### Task 2: Create and List Assignments

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/assignment/CreateLessonAssignmentRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/assignment/LessonAssignmentResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/LessonAssignmentService.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/service/LessonAssignmentServiceTest.java`

**Interfaces:**
- Consumes: Task 1 entity/repository, `UserRepository`, `RoomRepository`, `LessonRepository`, `QuestionRepository`, and `Clock`.
- Produces: `List<LessonAssignmentResponse> list(UUID teacherId, UUID roomId)` and `LessonAssignmentResponse create(UUID teacherId, UUID roomId, CreateLessonAssignmentRequest request)`.

- [ ] **Step 1: Write failing service tests for list, defaults, unlimited values, authorization, insertion, and publication**

```java
@Test
void deveAplicarPadroesQuandoLimitesForemOmitidos() {
	CreateLessonAssignmentRequest request = new CreateLessonAssignmentRequest(
			lesson.getId(), null, null, null, null, null, null, null, null, null
	);
	LessonAssignmentResponse response = service.create(teacher.getId(), room.getId(), request);
	assertThat(response.position()).isEqualTo(2);
	assertThat(response.status()).isEqualTo(ContentStatus.DRAFT);
	assertThat(response.timeLimitMinutes()).isEqualTo(30);
	assertThat(response.maxAttempts()).isEqualTo(3);
}

@Test
void deveAceitarLimitesNulosExplicitos() {
	CreateLessonAssignmentRequest request = requestWithLimits(NullNode.getInstance(), NullNode.getInstance());
	LessonAssignmentResponse response = service.create(teacher.getId(), room.getId(), request);
	assertThat(response.timeLimitMinutes()).isNull();
	assertThat(response.maxAttempts()).isNull();
}

@Test
void deveRejeitarPublicacaoComQuestoesInsuficientes() {
	when(questionRepository.countByLessonIdAndActiveTrue(lesson.getId())).thenReturn(2L);
	assertThatThrownBy(() -> service.create(teacher.getId(), room.getId(), publishedRequestWithQuestionCount(3)))
			.isInstanceOfSatisfying(ApiException.class,
					exception -> assertThat(exception.getCode()).isEqualTo("INSUFFICIENT_ACTIVE_QUESTIONS"));
}
```

Create additional test methods named `deveExigirProfessorAtivo`, `deveOcultarSalaOuLicaoDeOutroProfessor`, `deveImpedirAtribuicaoEmSalaArquivada`, `deveExigirLicaoPublicadaParaAtribuicaoPublicada`, `deveImpedirLicaoDuplicadaNaSala`, `deveAbrirEspacoAoInserirNaPrimeiraPosicao`, and `deveListarSomenteAtribuicoesDaSalaDoProfessor`. Each exception test must assert both the HTTP status and its stable code; the insertion test must capture all saved assignments and assert positions `1, 2, 3` in lesson order.

- [ ] **Step 2: Run the service test and confirm it fails**

Run: `rtk ./mvnw -Dtest=LessonAssignmentServiceTest test`

Expected: FAIL because DTOs and service do not exist.

- [ ] **Step 3: Implement request/response DTOs and create/list service methods**

Use these DTO shapes:

```java
public record CreateLessonAssignmentRequest(
		@NotNull UUID lessonId,
		@Min(1) Integer position,
		ContentStatus status,
		Instant availableFrom,
		Instant dueAt,
		JsonNode timeLimitMinutes,
		JsonNode maxAttempts,
		JsonNode questionCount,
		Boolean shuffleQuestions,
		Boolean shuffleOptions
) {}

public record LessonAssignmentResponse(
		UUID id, UUID roomId, UUID lessonId, String lessonTitle,
		int position, ContentStatus status, Instant availableFrom, Instant dueAt,
		Integer timeLimitMinutes, Integer maxAttempts, Integer questionCount,
		boolean shuffleQuestions, boolean shuffleOptions, long activeQuestionCount,
		Instant createdAt, Instant updatedAt, long version
) {}
```

In `create`, use Java `null` `JsonNode` as omitted and `NullNode` as explicit unlimited. Parse non-null numeric nodes with `canConvertToInt()` and reject non-positive values with `INVALID_ASSIGNMENT_LIMIT`. Default status to `DRAFT`, limits to 30/3, question count to all active questions, and shuffle flags to `true`. Lock the assignment list and validate the requested position in `1..size + 1`. Move existing rows temporarily to collision-free positive positions above `size`, flush, assign their final positions around the insertion, save the new row, and map the response.

- [ ] **Step 4: Run service tests**

Run: `rtk ./mvnw -Dtest=LessonAssignmentServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit create/list behavior**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/assignment/CreateLessonAssignmentRequest.java backend/src/main/java/com/ifsc/contacerta/dto/assignment/LessonAssignmentResponse.java backend/src/main/java/com/ifsc/contacerta/service/LessonAssignmentService.java backend/src/test/java/com/ifsc/contacerta/service/LessonAssignmentServiceTest.java
rtk git commit -m "feat: cria e lista atribuicoes de licoes"
```

### Task 3: Update and Delete Assignments

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/assignment/UpdateLessonAssignmentRequest.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/entity/LessonAssignment.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/LessonAssignmentService.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/service/LessonAssignmentServiceTest.java`

**Interfaces:**
- Consumes: Task 2 service and response mapper.
- Produces: `LessonAssignmentResponse update(UUID, UUID, UUID, UpdateLessonAssignmentRequest)` and `void delete(UUID, UUID, UUID, long)`.

- [ ] **Step 1: Add failing update/delete tests**

```java
@Test
void deveLimparLimiteComNullExplicito() {
	UpdateLessonAssignmentRequest request = new UpdateLessonAssignmentRequest(
			null, null, null, NullNode.getInstance(), null, null, null, null, assignment.getVersion()
	);
	LessonAssignmentResponse response = service.update(teacher.getId(), room.getId(), assignment.getId(), request);
	assertThat(response.timeLimitMinutes()).isNull();
}

@Test
void deveImpedirRemocaoDeAtribuicaoJaDisponivel() {
	assignment.publish();
	assignment.updateAvailability(null, null);
	assertThatThrownBy(() -> service.delete(teacher.getId(), room.getId(), assignment.getId(), assignment.getVersion()))
			.isInstanceOfSatisfying(ApiException.class,
					exception -> assertThat(exception.getCode()).isEqualTo("ASSIGNMENT_ALREADY_IN_USE"));
}
```

Create additional test methods named `devePreservarCamposOmitidos`, `deveLimparCamposConfiguraveisComNullExplicito`, `deveRejeitarIntervaloDeDatasInvalido`, `deveRejeitarLimitesNaoPositivos`, `deveRevalidarQuestoesAoPublicar`, `deveImpedirAlteracaoArquivada`, `deveRemoverRascunho`, `deveRemoverPublicacaoFutura`, `deveFecharLacunaAposRemocao`, `deveRejeitarVersaoDesatualizada`, and `deveOcultarAtribuicaoDeOutroProfessor`. Each test asserts the returned configuration or the exact stable error code defined in the spec.

- [ ] **Step 2: Run the focused service tests and confirm failure**

Run: `rtk ./mvnw -Dtest=LessonAssignmentServiceTest test`

Expected: FAIL because update/delete contracts and methods do not exist.

- [ ] **Step 3: Implement presence-aware update and deletion**

Use this request shape; every `JsonNode` distinguishes omitted Java `null`, explicit `NullNode`, and a concrete JSON value:

```java
public record UpdateLessonAssignmentRequest(
		ContentStatus status,
		JsonNode availableFrom,
		JsonNode dueAt,
		JsonNode timeLimitMinutes,
		JsonNode maxAttempts,
		JsonNode questionCount,
		Boolean shuffleQuestions,
		Boolean shuffleOptions,
		@NotNull @Min(0) Long version
) {}
```

Parse instant nodes only from ISO-8601 strings and return `INVALID_ASSIGNMENT_DATES` for other JSON types or invalid text. Validate the request version before mutation. Treat archived assignments as read-only. When status becomes `PUBLISHED`, require a published lesson and sufficient active questions. `delete` permits `DRAFT` or `PUBLISHED` with `availableFrom.isAfter(clock.instant())`; delete the row, flush, move later rows to collision-free positive positions above the previous list size, flush, then close the gap.

- [ ] **Step 4: Run service tests**

Run: `rtk ./mvnw -Dtest=LessonAssignmentServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit update/delete behavior**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/assignment/UpdateLessonAssignmentRequest.java backend/src/main/java/com/ifsc/contacerta/entity/LessonAssignment.java backend/src/main/java/com/ifsc/contacerta/service/LessonAssignmentService.java backend/src/test/java/com/ifsc/contacerta/service/LessonAssignmentServiceTest.java
rtk git commit -m "feat: configura e remove atribuicoes de licoes"
```

### Task 4: Reorder the Complete Room Path

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/assignment/LessonAssignmentOrderItem.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/assignment/LessonAssignmentOrderRequest.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/LessonAssignmentService.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/service/LessonAssignmentServiceTest.java`

**Interfaces:**
- Consumes: Task 1 pessimistic write query and entity `moveTo(int)`.
- Produces: `List<LessonAssignmentResponse> reorder(UUID teacherId, UUID roomId, LessonAssignmentOrderRequest request)`.

- [ ] **Step 1: Add failing reorder tests**

```java
@Test
void deveReordenarListaCompletaComPosicoesContiguas() {
	LessonAssignmentOrderRequest request = new LessonAssignmentOrderRequest(List.of(
			new LessonAssignmentOrderItem(second.getId(), second.getVersion()),
			new LessonAssignmentOrderItem(first.getId(), first.getVersion())
	));
	assertThat(service.reorder(teacher.getId(), room.getId(), request))
			.extracting(LessonAssignmentResponse::id, LessonAssignmentResponse::position)
			.containsExactly(tuple(second.getId(), 1), tuple(first.getId(), 2));
}
```

Create additional test methods named `deveRejeitarListaDeOrdemVazia`, `deveRejeitarIdentificadorDuplicado`, `deveRejeitarIdentificadorAusenteOuExtra`, `deveRejeitarAtribuicaoDeOutraSala`, `deveRejeitarVersaoDesatualizadaNaOrdenacao`, and `deveImpedirOrdenacaoEmSalaArquivada`. Assert `INVALID_ASSIGNMENT_ORDER` for set mismatches, `VERSION_CONFLICT` for stale versions, and `ROOM_ARCHIVED` for the archived room.

- [ ] **Step 2: Run focused service tests and confirm failure**

Run: `rtk ./mvnw -Dtest=LessonAssignmentServiceTest test`

Expected: FAIL because reorder DTOs and method do not exist.

- [ ] **Step 3: Implement complete-list reorder**

```java
public record LessonAssignmentOrderItem(@NotNull UUID assignmentId, @NotNull @Min(0) Long version) {}
public record LessonAssignmentOrderRequest(@NotEmpty List<@Valid LessonAssignmentOrderItem> assignments) {}
```

Load the room with teacher scope, lock all assignments, compare request IDs with the persisted ID set for exact equality and list-size equality, and validate every version. For a list of size `n`, move rows to unique temporary positions `n + 1` through `2n`, flush, then assign positions `1..n` in request order and flush again. Return the final ordered response list.

- [ ] **Step 4: Run service tests**

Run: `rtk ./mvnw -Dtest=LessonAssignmentServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit reorder behavior**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/assignment/LessonAssignmentOrderItem.java backend/src/main/java/com/ifsc/contacerta/dto/assignment/LessonAssignmentOrderRequest.java backend/src/main/java/com/ifsc/contacerta/service/LessonAssignmentService.java backend/src/test/java/com/ifsc/contacerta/service/LessonAssignmentServiceTest.java
rtk git commit -m "feat: reordena atribuicoes de licoes"
```

### Task 5: Expose and Verify the Teacher HTTP API

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/TeacherLessonAssignmentController.java`
- Create: `backend/src/test/java/com/ifsc/contacerta/controller/TeacherLessonAssignmentControllerTest.java`

**Interfaces:**
- Consumes: all service methods from Tasks 2–4 and authenticated `CurrentUser`.
- Produces: the five routes in the approved spec.

- [ ] **Step 1: Write failing MockMvc tests for all routes and security**

```java
@Test
void deveCriarAtribuicaoEDevolverLocation() throws Exception {
	mockMvc.perform(post("/teacher/rooms/{roomId}/lesson-assignments", room.getId())
			.with(authentication(teacherAuthentication()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"lessonId":"%s","status":"DRAFT"}
					""".formatted(lesson.getId())))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/lesson-assignments/")))
			.andExpect(jsonPath("$.lessonId").value(lesson.getId().toString()));
}
```

Add tests for ordered `GET`, `PATCH`, version supplied as `If-Match`-equivalent request body field, `DELETE` with required `version` query parameter, `PUT /order`, unauthenticated `401`, student role `403`, malformed JSON `400`, validation `422`, ownership `404`, and business conflicts `409` with the stable error code.

- [ ] **Step 2: Run controller test and confirm route failures**

Run: `rtk ./mvnw -Dtest=TeacherLessonAssignmentControllerTest test`

Expected: FAIL with `404` for the missing routes.

- [ ] **Step 3: Implement the controller**

```java
@RestController
@RequestMapping("/teacher/rooms/{roomId}/lesson-assignments")
@RequiredArgsConstructor
public class TeacherLessonAssignmentController {
	private final LessonAssignmentService service;

	@GetMapping
	public List<LessonAssignmentResponse> list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID roomId) {
		return service.list(user.userId(), roomId);
	}

	@PostMapping
	public ResponseEntity<LessonAssignmentResponse> create(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID roomId,
			@Valid @RequestBody CreateLessonAssignmentRequest request) {
		LessonAssignmentResponse response = service.create(user.userId(), roomId, request);
		URI location = URI.create("/teacher/rooms/" + roomId + "/lesson-assignments/" + response.id());
		return ResponseEntity.created(location).body(response);
	}

	@PatchMapping("/{assignmentId}")
	public LessonAssignmentResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID roomId,
			@PathVariable UUID assignmentId, @Valid @RequestBody UpdateLessonAssignmentRequest request) {
		return service.update(user.userId(), roomId, assignmentId, request);
	}

	@DeleteMapping("/{assignmentId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID roomId,
			@PathVariable UUID assignmentId, @RequestParam @Min(0) long version) {
		service.delete(user.userId(), roomId, assignmentId, version);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/order")
	public List<LessonAssignmentResponse> reorder(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID roomId,
			@Valid @RequestBody LessonAssignmentOrderRequest request) {
		return service.reorder(user.userId(), roomId, request);
	}
}
```

Create returns `201` and `/teacher/rooms/{roomId}/lesson-assignments/{id}`. Delete returns `204`. Teacher-role validation remains in the service, consistent with the existing teacher-room flow, so authenticated students receive `403`.

- [ ] **Step 4: Run controller and complete backend suites**

Run: `rtk ./mvnw -Dtest=TeacherLessonAssignmentControllerTest test`

Expected: PASS.

Run: `rtk ./mvnw verify`

Expected: `BUILD SUCCESS`, zero failures and zero errors, with Testcontainers PostgreSQL applying migrations through version 6.

- [ ] **Step 5: Run static and contract checks**

```bash
rtk git diff --check main...HEAD
rtk rg -n --pcre2 "(?<!import )java\\.[a-z].*\\." backend/src/main/java backend/src/test/java
rtk rg -n "import .*\\*|TODO|FIXME" backend/src/main/java backend/src/test/java
rtk git status --short
rtk git log --oneline main..HEAD
```

Expected: no whitespace errors, no inline fully qualified types or wildcard imports in new code, no placeholders, a clean worktree, and the planned atomic commit sequence.

- [ ] **Step 6: Commit the HTTP API**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/controller/TeacherLessonAssignmentController.java backend/src/test/java/com/ifsc/contacerta/controller/TeacherLessonAssignmentControllerTest.java backend/src/main/java/com/ifsc/contacerta/service/LessonAssignmentService.java backend/src/main/java/com/ifsc/contacerta/dto/assignment
rtk git commit -m "feat: expoe atribuicoes de licoes"
```

- [ ] **Step 7: Re-run final verification after the commit**

Run: `rtk ./mvnw verify`

Expected: `BUILD SUCCESS`, zero failures and zero errors.
