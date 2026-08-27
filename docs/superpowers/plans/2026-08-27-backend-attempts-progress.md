# Backend Attempts and Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the authenticated student learning path, complete attempt lifecycle, immutable snapshots and answers, scoring, expiration, extra-attempt grants, and room-scoped progress.

**Architecture:** Use JPA aggregates backed by PostgreSQL constraints and explicit pessimistic locks. Attempt finalization and the materialized room progress projection update synchronously in one transaction; pure scoring and dedicated mappers keep orchestration services focused.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Spring Security, Spring Data JPA/Hibernate, Flyway, PostgreSQL 18, Jackson 3, Jakarta Validation, JUnit 5, Mockito, AssertJ, MockMvc, and Testcontainers.

**Spec:** `docs/superpowers/specs/2026-08-27-backend-attempts-progress-design.md`

## Global Constraints

- Follow `backend/AGENTS.md`: explicit imports, technical layers, DTO-only API contracts, dedicated mappers where possible, Lombok without `@Data`, and no inline fully qualified type names.
- Use `backend/mvnw` from the backend directory and prefix every shell command with `rtk`.
- All student, teacher, room, assignment, and attempt scope comes from the authenticated principal and ownership-scoped repository queries.
- Invisible resources return `404`; known role mismatches return `403`; domain payload errors return `422`; lifecycle/idempotency/limit conflicts return `409`.
- No in-progress response exposes gabaritos, correct option flags, expected numeric/boolean values, or explanations.
- All timestamps use the injected UTC `Clock`; randomization uses the injected secure `RandomGenerator`.
- Write the failing test first, observe the expected failure, implement the minimum complete behavior, rerun the focused tests, and commit each task atomically.
- Do not modify or include the primary checkout's local changes.

Stable Problem Details mappings for every task:

- `403`: `STUDENT_REQUIRED`, `ACCOUNT_INACTIVE`, and known incompatible teacher/student roles.
- `404`: `STUDENT_NOT_FOUND`, `MEMBERSHIP_NOT_FOUND`, `ASSIGNMENT_NOT_FOUND`, `ATTEMPT_NOT_FOUND`, and `QUESTION_SNAPSHOT_NOT_FOUND`; use scoped queries so invisible cross-owner resources use the same codes.
- `422`: `ASSIGNMENT_NOT_AVAILABLE`, `ASSIGNMENT_CLOSED`, `ASSIGNMENT_CONTENT_UNAVAILABLE`, `INVALID_ANSWER`, and `INVALID_EXTRA_ATTEMPT_QUANTITY`.
- `409`: `ROOM_ARCHIVED`, `PREREQUISITE_NOT_MET`, `ATTEMPT_LIMIT_REACHED`, `ATTEMPT_FINISHED`, `ATTEMPT_IN_PROGRESS`, `ANSWER_ALREADY_RECORDED`, `UNLIMITED_ATTEMPTS`, `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_REUSED`, and `VERSION_CONFLICT`.

## File Structure

### Domain and persistence

- `model/AttemptStatus.java`: `IN_PROGRESS`, `SUBMITTED`, `EXPIRED`.
- `model/AttemptAvailabilityStatus.java`: student path lock state.
- `entity/Attempt.java`: lifecycle and finalized totals only.
- `entity/AttemptQuestionSnapshot.java`: immutable frozen question and owned option snapshots.
- `entity/AttemptOptionSnapshot.java`: immutable frozen option.
- `entity/AttemptAnswer.java`: immutable normalized answer and selected snapshot options.
- `entity/ExtraAttemptGrant.java`: append-only audited teacher grant.
- `entity/RoomStudentProgress.java`: materialized best-result totals per student and room.
- `entity/IdempotencyRecord.java`: exact start-attempt replay record.
- Matching repositories provide scoped reads, projections, and pessimistic locks.

### Services and mapping

- `service/AttemptScoringService.java`: pure answer validation and correction.
- `service/AttemptService.java`: attempt start/read/answer/submit/result orchestration.
- `service/StudentProgressService.java`: prerequisite and best-result projection logic.
- `service/StudentLessonService.java`: student path, lesson detail, and history.
- `service/ExtraAttemptGrantService.java`: teacher grant case.
- `service/AttemptExpirationService.java`: scheduled discovery and transactional expiry.
- `service/IdempotencyHasher.java`: SHA-256 scope hash.
- `mapper/AttemptMapper.java`, `StudentLessonMapper.java`, and `ExtraAttemptGrantMapper.java`: DTO conversion with explicit public/result boundaries.

### HTTP contracts

- `dto/attempt/`: attempt, question, answer, result, and history records.
- `dto/studentlesson/`: path and lesson-detail records.
- `dto/extraattempt/`: grant request and response records.
- `controller/StudentLessonController.java`, `StudentAttemptController.java`, and `TeacherExtraAttemptController.java`: the nine approved routes.

---

### Task 1: Persist Attempts and Immutable Snapshots

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__create_attempt_and_progress_tables.sql`
- Create: `backend/src/main/java/com/ifsc/contacerta/model/AttemptStatus.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/Attempt.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/AttemptQuestionSnapshot.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/AttemptOptionSnapshot.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/AttemptAnswer.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/AttemptQuestionSnapshotRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/AttemptAnswerRepository.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/support/PostgresIntegrationTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/entity/AttemptPersistenceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/repository/AttemptRepositoryTest.java`

**Interfaces:**
- Consumes: existing `LessonAssignment`, `Question`, `QuestionOption`, and `User` entities.
- Produces: `Attempt(LessonAssignment, User, int, Instant, Instant)`, `addSnapshot(Question, int, List<QuestionOption>)`, `finalizeAs(AttemptStatus, Instant, int, int, int, boolean, int, int)`, and locked/scoped repository methods.

- [ ] **Step 1: Write failing persistence tests**

Create tests named `devePersistirTentativaComSnapshotsImutaveis`, `deveImpedirDuasTentativasAtivas`, `deveImpedirSequenciaDuplicada`, `devePersistirUmaRespostaPorSnapshot`, `deveBloquearTentativaParaFinalizacao`, and `deveDetectarVersaoDesatualizada`. The optimistic-lock persistence test loads the same attempt in two independent entity managers, commits the first change, and asserts that the second flush throws `OptimisticLockException`; Task 9 proves the HTTP translation to `409 VERSION_CONFLICT`. The primary mapping assertion is:

```java
Attempt attempt = new Attempt(assignment, student, 1, startedAt, expiresAt);
attempt.addSnapshot(question, 1, question.getOptions());
entityManager.persist(attempt);
entityManager.flush();
entityManager.clear();

Attempt persisted = entityManager.find(Attempt.class, attempt.getId());
assertThat(persisted.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
assertThat(persisted.getSnapshots()).hasSize(1);
assertThat(persisted.getSnapshots().getFirst().getOptions()).hasSize(2);
```

Update the shared truncation statement so `TRUNCATE ... CASCADE` starts with the new attempt/progress/idempotency tables before existing tables.

- [ ] **Step 2: Run tests and confirm missing-schema/type failure**

Run: `rtk ./mvnw -Dtest=AttemptPersistenceTest,AttemptRepositoryTest test`

Expected: FAIL because the migration, enum, entities, and repositories do not exist.

- [ ] **Step 3: Add schema and aggregate mappings**

Create all seven tables and join table specified by the design. Use a PostgreSQL partial unique index:

```sql
create unique index uk_attempts_one_in_progress
    on attempts (assignment_id, student_id)
    where status = 'IN_PROGRESS';
```

Map snapshots with `cascade = CascadeType.ALL`, no public mutation after creation, and ordered defensive-copy getters. `AttemptAnswer` exposes these factories:

```java
public static AttemptAnswer choice(
		AttemptQuestionSnapshot snapshot,
		Set<AttemptOptionSnapshot> selectedOptions,
		boolean correct,
		Instant answeredAt
);
public static AttemptAnswer booleanAnswer(
		AttemptQuestionSnapshot snapshot, boolean value, boolean correct, Instant answeredAt
);
public static AttemptAnswer numeric(
		AttemptQuestionSnapshot snapshot, BigDecimal value, boolean correct, Instant answeredAt
);
```

Repository contracts:

```java
Optional<Attempt> findByIdAndStudentId(UUID id, UUID studentId);
Optional<Attempt> findByAssignmentIdAndStudentIdAndStatus(
		UUID assignmentId, UUID studentId, AttemptStatus status
);
long countByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select attempt from Attempt attempt where attempt.id = :id")
Optional<Attempt> findByIdForUpdate(@Param("id") UUID id);
```

- [ ] **Step 4: Run focused persistence tests**

Run: `rtk ./mvnw -Dtest=AttemptPersistenceTest,AttemptRepositoryTest test`

Expected: PASS with Flyway at version 7 and Hibernate schema validation clean.

- [ ] **Step 5: Commit persistence aggregate**

```bash
rtk git add backend/src/main/resources/db/migration/V7__create_attempt_and_progress_tables.sql backend/src/main/java/com/ifsc/contacerta/model/AttemptStatus.java backend/src/main/java/com/ifsc/contacerta/entity/Attempt.java backend/src/main/java/com/ifsc/contacerta/entity/AttemptQuestionSnapshot.java backend/src/main/java/com/ifsc/contacerta/entity/AttemptOptionSnapshot.java backend/src/main/java/com/ifsc/contacerta/entity/AttemptAnswer.java backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java backend/src/main/java/com/ifsc/contacerta/repository/AttemptQuestionSnapshotRepository.java backend/src/main/java/com/ifsc/contacerta/repository/AttemptAnswerRepository.java backend/src/test/java/com/ifsc/contacerta/support/PostgresIntegrationTest.java backend/src/test/java/com/ifsc/contacerta/entity/AttemptPersistenceTest.java backend/src/test/java/com/ifsc/contacerta/repository/AttemptRepositoryTest.java
rtk git commit -m "feat: persiste tentativas e snapshots"
```

### Task 2: Persist Progress, Grants, and Idempotency

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/ExtraAttemptGrant.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/RoomStudentProgress.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/IdempotencyRecord.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/ExtraAttemptGrantRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/RoomStudentProgressRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/IdempotencyRecordRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/RoomMembershipRepository.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/repository/AttemptSupportRepositoryTest.java`

**Interfaces:**
- Consumes: Task 1 schema and existing membership aggregate.
- Produces: append-only grants, progress projection mutation, exact idempotency replay, and membership/progress locks.

- [ ] **Step 1: Write failing repository tests**

Create tests named `deveSomarTentativasExtras`, `deveBloquearProgresso`, `deveImpedirChaveIdempotenteDuplicadaPorUsuario`, `deveBuscarRegistroIdempotenteMesmoAposExpirar`, and `deveBloquearMatriculaDoAlunoNaSala`.

```java
ExtraAttemptGrant grant = new ExtraAttemptGrant(assignment, student, teacher, 2, now);
grantRepository.saveAndFlush(grant);
assertThat(grantRepository.sumQuantityByAssignmentIdAndStudentId(
		assignment.getId(), student.getId()
)).isEqualTo(2L);
```

- [ ] **Step 2: Run and confirm missing types**

Run: `rtk ./mvnw -Dtest=AttemptSupportRepositoryTest test`

Expected: FAIL because the support entities and repositories do not exist.

- [ ] **Step 3: Implement support persistence**

Use these core methods:

```java
public void applyResult(
		int xpDelta,
		int starsDelta,
		boolean firstCompletion,
		boolean firstPass,
		Instant activityAt
);

@Query("select coalesce(sum(grant.quantity), 0) from ExtraAttemptGrant grant "
		+ "where grant.assignment.id = :assignmentId and grant.student.id = :studentId")
long sumQuantityByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<RoomMembership> findForUpdateByRoomIdAndStudentId(UUID roomId, UUID studentId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<RoomStudentProgress> findForUpdateByRoomIdAndStudentId(UUID roomId, UUID studentId);

Optional<IdempotencyRecord> findByUserIdAndKey(UUID userId, String key);
```

The service, not the repository, compares `IdempotencyRecord.expiresAt` with the injected clock so it can distinguish replay, conflict, and an expired row that must be deleted and flushed before the key is reused. `RoomStudentProgress.applyResult` recalculates `level = floor(totalXp / 100) + 1` after adding non-negative deltas.

- [ ] **Step 4: Run support persistence tests**

Run: `rtk ./mvnw -Dtest=AttemptSupportRepositoryTest test`

Expected: PASS.

- [ ] **Step 5: Commit support persistence**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/entity/ExtraAttemptGrant.java backend/src/main/java/com/ifsc/contacerta/entity/RoomStudentProgress.java backend/src/main/java/com/ifsc/contacerta/entity/IdempotencyRecord.java backend/src/main/java/com/ifsc/contacerta/repository/ExtraAttemptGrantRepository.java backend/src/main/java/com/ifsc/contacerta/repository/RoomStudentProgressRepository.java backend/src/main/java/com/ifsc/contacerta/repository/IdempotencyRecordRepository.java backend/src/main/java/com/ifsc/contacerta/repository/RoomMembershipRepository.java backend/src/test/java/com/ifsc/contacerta/repository/AttemptSupportRepositoryTest.java
rtk git commit -m "feat: persiste progresso e idempotencia"
```

### Task 3: Correct Snapshot Answers

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/attempt/RecordAttemptAnswerRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/AttemptScoringService.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/AttemptScoringServiceTest.java`

**Interfaces:**
- Consumes: immutable question/option snapshots and typed request payload.
- Produces: `ScoredAnswer validateAndScore(AttemptQuestionSnapshot, RecordAttemptAnswerRequest)` with normalized values.

Use this exact request contract:

```java
public record RecordAttemptAnswerRequest(
		List<UUID> selectedOptionIds,
		Boolean booleanValue,
		String numericValue
) {}
```

The scorer rejects repeated selected IDs instead of silently deduplicating them.

- [ ] **Step 1: Write failing scoring tests**

Create tests named `deveCorrigirEscolhaUnica`, `deveExigirConjuntoExatoNaMultiplaEscolha`, `deveCorrigirVerdadeiroFalso`, `deveAceitarVirgulaNaRespostaNumerica`, `deveAceitarLimiteExatoDaTolerancia`, `deveRejeitarOpcaoDeOutroSnapshot`, and `deveRejeitarPayloadIncompativel`.

```java
AttemptScoringService.ScoredAnswer scored = service.validateAndScore(
		numericSnapshot("100.00", "0.50"),
		new RecordAttemptAnswerRequest(null, null, "100,50")
);
assertThat(scored.correct()).isTrue();
assertThat(scored.numericValue()).isEqualByComparingTo("100.50");
```

- [ ] **Step 2: Run and verify red**

Run: `rtk ./mvnw -Dtest=AttemptScoringServiceTest test`

Expected: FAIL because the request and scoring service do not exist.

- [ ] **Step 3: Implement the pure scorer**

Define:

```java
public record ScoredAnswer(
		Set<AttemptOptionSnapshot> selectedOptions,
		Boolean booleanValue,
		BigDecimal numericValue,
		boolean correct
) {
}
```

Validate exactly one request field. Single choice requires one option; multiple choice compares sets; true/false requires one boolean; numeric replaces comma with point, constructs `BigDecimal`, and compares absolute difference to the frozen tolerance. Throw `422 INVALID_ANSWER` for all shape, membership, or parse errors.

- [ ] **Step 4: Run scorer tests**

Run: `rtk ./mvnw -Dtest=AttemptScoringServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit scoring**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/attempt/RecordAttemptAnswerRequest.java backend/src/main/java/com/ifsc/contacerta/service/AttemptScoringService.java backend/src/test/java/com/ifsc/contacerta/service/AttemptScoringServiceTest.java
rtk git commit -m "feat: corrige respostas de tentativas"
```

### Task 4: Start Attempts Idempotently and Build Snapshots

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/config/AttemptProperties.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/config/AttemptConfig.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/IdempotencyHasher.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/StudentProgressService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/AttemptService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mapper/AttemptMapper.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptStartResult.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptQuestionResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptOptionResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptAnswerValueResponse.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/QuestionRepository.java`
- Modify: `backend/src/main/resources/application.properties`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/AttemptStartServiceTest.java`

**Interfaces:**
- Consumes: Tasks 1–2 repositories, `Clock`, `RandomGenerator`, and Jackson `ObjectMapper`.
- Produces: `AttemptStartResult start(UUID studentId, UUID assignmentId, String idempotencyKey)`, prerequisite queries, and public attempt mapping without secrets.

Use these exact public contracts:

```java
public record AttemptStartResult(HttpStatus status, URI location, AttemptResponse body) {}

public record AttemptResponse(
		UUID id,
		UUID assignmentId,
		int sequence,
		AttemptStatus status,
		Instant startedAt,
		Instant expiresAt,
		Instant serverTime,
		List<AttemptQuestionResponse> questions,
		long version
) {}

public record AttemptQuestionResponse(
		UUID id,
		QuestionType type,
		String prompt,
		int position,
		NumericUnit unit,
		Integer decimalPlaces,
		List<AttemptOptionResponse> options,
		AttemptAnswerValueResponse answer
) {}

public record AttemptOptionResponse(UUID id, String text, int position) {}

public record AttemptAnswerValueResponse(
		Set<UUID> selectedOptionIds,
		Boolean booleanValue,
		String numericValue,
		Instant answeredAt
) {}
```

`expiresAt` and `answer` are nullable. Empty choice selections use an empty set. These records deliberately have no correctness, explanation, correct-option, expected-boolean, expected-numeric, or tolerance fields.

- [ ] **Step 1: Write failing start tests**

Create tests named `deveCriarTentativaComSnapshots`, `deveCalcularExpiracaoPeloMenorPrazo`, `deveReproduzirRespostaDaMesmaChave`, `deveReutilizarChaveDepoisDaExpiracaoDoRegistro`, `deveRejeitarChaveReutilizadaEmOutraAtribuicao`, `deveRetomarTentativaAtivaComNovaChave`, `deveExigirMatriculaAtiva`, `deveRespeitarDisponibilidade`, `deveExigirPrerequisito`, `deveAplicarLimiteComConcessoes`, `devePermitirTentativasIlimitadas`, `deveRejeitarConteudoInsuficiente`, and `deveOmitirGabaritoNoDtoPublico`.

```java
AttemptStartResult result = service.start(student.getId(), assignment.getId(), "key-1");
assertThat(result.status()).isEqualTo(HttpStatus.CREATED);
assertThat(result.location()).hasPath("/student/attempts/" + result.body().id());
assertThat(objectMapper.writeValueAsString(result.body()))
		.doesNotContain("correct", "explanation", "correctBoolean", "correctNumericValue");
```

- [ ] **Step 2: Run and verify red**

Run: `rtk ./mvnw -Dtest=AttemptStartServiceTest test`

Expected: FAIL because start orchestration and DTOs do not exist.

- [ ] **Step 3: Implement start flow**

Add properties:

```properties
app.attempt.idempotency-ttl=PT24H
app.attempt.expiration-batch-size=100
app.attempt.expiration-fixed-delay=60000
```

Define `AttemptProperties` as `@ConfigurationProperties("app.attempt")` with `Duration idempotencyTtl` and `int expirationBatchSize`. Register it through `@EnableConfigurationProperties(AttemptProperties.class)` on `AttemptConfig`. Add `QuestionRepository.findByLessonIdAndActiveTrueOrderByPositionAsc(UUID)`.

Create `StudentProgressService` with the initial prerequisite contract; later tasks extend this same class:

```java
@Transactional(readOnly = true)
public boolean hasPassedAssignment(UUID studentId, UUID assignmentId);
```

`AttemptService.start` executes in this order: validate non-blank key with `409 IDEMPOTENCY_KEY_REQUIRED`; require active student; load assignment through active membership scope; inspect an existing idempotency record; lock membership; inspect the key a second time after acquiring the lock; replay an unexpired exact method/scope/hash match; reject an unexpired mismatch with `409 IDEMPOTENCY_KEY_REUSED`; delete and flush an expired record before allowing reuse; reuse an active attempt; validate room/status/window/prerequisite/limit; load active questions; select and shuffle; compute expiry; create attempt/snapshots; flush; map public response; serialize and persist the exact status, content type, `Location`, and body. Hash the UTF-8 canonical string `POST\n/student/room-lessons/{assignmentId}/attempts\n` (empty request body after the final newline) with SHA-256.

The mapper signatures are:

```java
public AttemptResponse toPublicResponse(Attempt attempt, Instant serverTime);
public String writePublicResponse(AttemptResponse response);
public AttemptResponse readPublicResponse(String json);
```

- [ ] **Step 4: Run start tests**

Run: `rtk ./mvnw -Dtest=AttemptStartServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit start flow**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/config/AttemptProperties.java backend/src/main/java/com/ifsc/contacerta/config/AttemptConfig.java backend/src/main/java/com/ifsc/contacerta/service/IdempotencyHasher.java backend/src/main/java/com/ifsc/contacerta/service/StudentProgressService.java backend/src/main/java/com/ifsc/contacerta/service/AttemptService.java backend/src/main/java/com/ifsc/contacerta/mapper/AttemptMapper.java backend/src/main/java/com/ifsc/contacerta/dto/attempt backend/src/main/java/com/ifsc/contacerta/repository/QuestionRepository.java backend/src/main/resources/application.properties backend/src/test/java/com/ifsc/contacerta/service/AttemptStartServiceTest.java
rtk git commit -m "feat: inicia tentativas idempotentes"
```

### Task 5: Retrieve Attempts and Record Immutable Answers

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptAnswerReceiptResponse.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/AttemptService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/mapper/AttemptMapper.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/AttemptAnswerServiceTest.java`

**Interfaces:**
- Consumes: Task 3 scorer and Task 4 public DTO mapper.
- Produces: `get(UUID, UUID)` and `answer(UUID, UUID, UUID, RecordAttemptAnswerRequest)`.

Use this receipt contract:

```java
public record AttemptAnswerReceiptResponse(boolean correct, Instant answeredAt) {}
```

- [ ] **Step 1: Write failing retrieval/answer tests**

Create tests named `deveRetomarTentativaDoProprioAluno`, `deveOcultarTentativaDeOutroAluno`, `deveRegistrarRespostaImutavel`, `deveRetornarRespostaIdenticaExistente`, `deveRejeitarAlteracaoDaResposta`, `deveRejeitarSnapshotDeOutraTentativa`, `deveRejeitarRespostaAposFinalizacao`, and `deveExpirarAntesDeResponder`.

```java
AttemptAnswerReceiptResponse first = service.answer(studentId, attemptId, snapshotId, request);
AttemptAnswerReceiptResponse replay = service.answer(studentId, attemptId, snapshotId, request);
assertThat(replay).isEqualTo(first);
verify(answerRepository, times(1)).save(any(AttemptAnswer.class));
```

- [ ] **Step 2: Run and verify red**

Run: `rtk ./mvnw -Dtest=AttemptAnswerServiceTest test`

Expected: FAIL because retrieval and answer methods are missing.

- [ ] **Step 3: Implement locked answer insertion**

Add:

```java
@Transactional
public AttemptResponse get(UUID studentId, UUID attemptId);

@Transactional
public AttemptAnswerReceiptResponse answer(
		UUID studentId,
		UUID attemptId,
		UUID questionSnapshotId,
		RecordAttemptAnswerRequest request
);
```

Load and pessimistically lock the attempt through student scope, expire it when necessary, require `IN_PROGRESS`, ensure the snapshot belongs to it, and check for an existing answer before inserting. Return the existing row only when canonical values match; otherwise throw `409 ANSWER_ALREADY_RECORDED`. The attempt lock serializes competing inserts, while the unique constraint remains the final database guard.

- [ ] **Step 4: Run retrieval/answer tests**

Run: `rtk ./mvnw -Dtest=AttemptAnswerServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit immutable answers**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptAnswerReceiptResponse.java backend/src/main/java/com/ifsc/contacerta/service/AttemptService.java backend/src/main/java/com/ifsc/contacerta/mapper/AttemptMapper.java backend/src/test/java/com/ifsc/contacerta/service/AttemptAnswerServiceTest.java
rtk git commit -m "feat: registra respostas imutaveis"
```

### Task 6: Finalize Attempts and Update Progress

**Files:**
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/StudentProgressService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptResultResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptQuestionResultResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptResultOptionResponse.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/AttemptService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/mapper/AttemptMapper.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/StudentProgressServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/AttemptFinalizationServiceTest.java`

**Interfaces:**
- Consumes: finalized answer correctness and locked membership/progress scope.
- Produces: `submit(UUID, UUID)`, `result(UUID, UUID)`, `expire(UUID)`, and atomic best-result deltas.

Use these exact result contracts:

```java
public record AttemptResultResponse(
		UUID id,
		UUID assignmentId,
		int sequence,
		AttemptStatus status,
		Instant startedAt,
		Instant expiresAt,
		Instant submittedAt,
		int totalQuestions,
		int answeredQuestions,
		int correctAnswers,
		int scorePercent,
		boolean passed,
		int stars,
		int xpCredited,
		List<AttemptQuestionResultResponse> questions,
		long version
) {}

public record AttemptQuestionResultResponse(
		UUID id,
		QuestionType type,
		String prompt,
		String explanation,
		int position,
		NumericUnit unit,
		Integer decimalPlaces,
		List<AttemptResultOptionResponse> options,
		AttemptAnswerValueResponse answer,
		Set<UUID> correctOptionIds,
		Boolean correctBoolean,
		String correctNumericValue,
		String absoluteTolerance,
		boolean correct
) {}

public record AttemptResultOptionResponse(UUID id, String text, int position, boolean correct) {}
```

Fields that do not apply to a question type are nullable; `correctOptionIds` is empty for non-choice questions. Only this finalized-result boundary may expose the correctness and explanation fields.

- [ ] **Step 1: Write failing progress/finalization tests**

Create tests named `deveArredondarPercentualHalfUp`, `deveCalcularFaixasDeEstrelas`, `deveCreditarSomenteMelhoriaDeXp`, `deveCreditarSomenteMelhoriaDeEstrelas`, `deveContarPrimeiraConclusaoEAprovacao`, `deveCalcularNivel`, `deveFinalizarComOmissoesIncorretas`, `deveExpirarPeloRelogioDoServidor`, `deveSubmeterIdempotentemente`, `deveRejeitarResultadoEmAndamento`, and `deveExporGabaritoSomenteNoResultado`.

```java
AttemptResultResponse result = service.submit(studentId, attemptId);
assertThat(result.scorePercent()).isEqualTo(67);
assertThat(result.stars()).isEqualTo(1);
assertThat(result.xpCredited()).isEqualTo(20);
assertThat(result.questions()).allSatisfy(question ->
		assertThat(question.explanation()).isNotNull()
);
```

- [ ] **Step 2: Run and verify red**

Run: `rtk ./mvnw -Dtest=StudentProgressServiceTest,AttemptFinalizationServiceTest test`

Expected: FAIL because progress and finalization are missing.

- [ ] **Step 3: Implement finalization and deltas**

Add repository aggregate queries for previous best potential XP, best stars, any completion, and any pass, excluding the current attempt. Extend `StudentProgressService` with the pure delta calculation and progress mutation:

```java
public ProgressDelta calculateDelta(
		int correctAnswers,
		int stars,
		int previousBestPotentialXp,
		int previousBestStars,
		boolean previouslyCompleted,
		boolean previouslyPassed,
		boolean passed
);

public record ProgressDelta(
		int xpDelta,
		int starsDelta,
		boolean firstCompletion,
		boolean firstPass
) {}

@Transactional
public void touch(UUID roomId, UUID studentId, Instant activityAt);
```

Extend `AttemptService` with the lifecycle methods:

```java

@Transactional
public AttemptResultResponse submit(UUID studentId, UUID attemptId);

@Transactional
public AttemptResultResponse result(UUID studentId, UUID attemptId);

@Transactional
public void expire(UUID attemptId);
```

Round `correct * 100 / total` with `BigDecimal` and `RoundingMode.HALF_UP`. Lock attempt, membership, and progress before changing state. Call `touch` from the answer flow added in Task 5, without awarding XP or stars. Flush before mapping the result version. Both `SUBMITTED` and `EXPIRED` use one private finalizer and return the same result shape.

- [ ] **Step 4: Run finalization tests**

Run: `rtk ./mvnw -Dtest=StudentProgressServiceTest,AttemptFinalizationServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit finalization/progress**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/service/StudentProgressService.java backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptResultResponse.java backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptQuestionResultResponse.java backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptResultOptionResponse.java backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java backend/src/main/java/com/ifsc/contacerta/service/AttemptService.java backend/src/main/java/com/ifsc/contacerta/mapper/AttemptMapper.java backend/src/test/java/com/ifsc/contacerta/service/StudentProgressServiceTest.java backend/src/test/java/com/ifsc/contacerta/service/AttemptFinalizationServiceTest.java
rtk git commit -m "feat: finaliza tentativas e atualiza progresso"
```

### Task 7: Expose the Student Lesson Path and History Service

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/model/AttemptAvailabilityStatus.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/StudentLessonService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mapper/StudentLessonMapper.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/studentlesson/StudentLessonPathResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/studentlesson/StudentLessonDetailResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptHistoryResponse.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/StudentLessonServiceTest.java`

**Interfaces:**
- Consumes: assignments, membership, attempts, grants, and progress.
- Produces: `listPath`, `getLesson`, and paginated `listAttempts`.

Use these exact read-model contracts:

```java
public record StudentLessonPathResponse(
		UUID assignmentId,
		UUID lessonId,
		String title,
		String summary,
		int position,
		AttemptAvailabilityStatus availabilityStatus,
		Instant availableFrom,
		Instant dueAt,
		Integer timeLimitMinutes,
		Integer maxAttempts,
		long attemptsUsed,
		Long attemptsAvailable,
		Integer bestScorePercent,
		boolean passed,
		int bestStars,
		UUID activeAttemptId
) {}

public record StudentLessonDetailResponse(
		UUID assignmentId,
		UUID lessonId,
		String title,
		String summary,
		String theoryMarkdown,
		int position,
		AttemptAvailabilityStatus availabilityStatus,
		Instant availableFrom,
		Instant dueAt,
		Integer timeLimitMinutes,
		Integer maxAttempts,
		Integer questionCount,
		long attemptsUsed,
		Long attemptsAvailable,
		Integer bestScorePercent,
		boolean passed,
		int bestStars,
		UUID activeAttemptId
) {}

public record AttemptHistoryResponse(
		UUID id,
		int sequence,
		AttemptStatus status,
		Instant startedAt,
		Instant expiresAt,
		Instant submittedAt,
		Integer scorePercent,
		Boolean passed,
		Integer stars,
		Integer xpCredited
) {}
```

`attemptsAvailable` is null for unlimited assignments. Best/finalized fields are null until a finalized attempt exists; `activeAttemptId` is null when there is no resumable attempt.

Availability precedence is deterministic: an existing `IN_PROGRESS` attempt is `AVAILABLE`; otherwise a future `availableFrom` is `NOT_OPEN_YET`; an elapsed `dueAt` is `CLOSED`; an unmet immediately previous published assignment is `PREREQUISITE_REQUIRED`; an exhausted finite limit is `ATTEMPT_LIMIT_REACHED`; all remaining assignments are `AVAILABLE`. An archived room remains readable through these DTOs, while Task 4 still rejects creation of a new attempt with `ROOM_ARCHIVED`.

- [ ] **Step 1: Write failing lesson path tests**

Create tests named `deveListarSomenteAtribuicoesPublicadasEmOrdem`, `deveIndicarBloqueioPorData`, `deveIndicarBloqueioPorPrerequisito`, `deveIndicarLimiteEAindaPermitirRetomada`, `deveCalcularTentativasDisponiveisComConcessoes`, `deveExporTeoriaSemQuestoes`, `deveListarHistoricoPaginado`, `devePermitirLeituraDeSalaArquivada`, and `deveOcultarSalaSemMatriculaAtiva`.

```java
List<StudentLessonPathResponse> path = service.listPath(studentId, roomId);
assertThat(path)
		.extracting(StudentLessonPathResponse::position, StudentLessonPathResponse::availabilityStatus)
		.containsExactly(tuple(1, AVAILABLE), tuple(2, PREREQUISITE_REQUIRED));
```

- [ ] **Step 2: Run and verify red**

Run: `rtk ./mvnw -Dtest=StudentLessonServiceTest test`

Expected: FAIL because student lesson contracts and service do not exist.

- [ ] **Step 3: Implement student read model**

Add ownership-scoped assignment queries by room/student membership and by room/lesson. Define:

```java
@Transactional(readOnly = true)
public List<StudentLessonPathResponse> listPath(UUID studentId, UUID roomId);

@Transactional(readOnly = true)
public StudentLessonDetailResponse getLesson(UUID studentId, UUID roomId, UUID lessonId);

@Transactional
public PageResponse<AttemptHistoryResponse> listAttempts(
		UUID studentId, UUID roomId, UUID lessonId, Pageable pageable
);
```

Expire stale attempts before returning history. Limit page size to 100 in the controller task. The mapper derives `AVAILABLE`, `NOT_OPEN_YET`, `CLOSED`, `PREREQUISITE_REQUIRED`, or `ATTEMPT_LIMIT_REACHED` from service-calculated facts and never queries repositories itself.

- [ ] **Step 4: Run student lesson tests**

Run: `rtk ./mvnw -Dtest=StudentLessonServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit student read model**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/model/AttemptAvailabilityStatus.java backend/src/main/java/com/ifsc/contacerta/service/StudentLessonService.java backend/src/main/java/com/ifsc/contacerta/mapper/StudentLessonMapper.java backend/src/main/java/com/ifsc/contacerta/dto/studentlesson backend/src/main/java/com/ifsc/contacerta/dto/attempt/AttemptHistoryResponse.java backend/src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java backend/src/test/java/com/ifsc/contacerta/service/StudentLessonServiceTest.java
rtk git commit -m "feat: expoe trilha de licoes do aluno"
```

### Task 8: Grant Extra Attempts and Expire Attempts Automatically

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/service/ExtraAttemptGrantService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/AttemptExpirationService.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/config/AttemptSchedulingConfig.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mapper/ExtraAttemptGrantMapper.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/extraattempt/GrantExtraAttemptsRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/extraattempt/ExtraAttemptGrantResponse.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/ExtraAttemptGrantServiceTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/AttemptExpirationServiceTest.java`

**Interfaces:**
- Consumes: teacher assignment ownership, active membership, Task 6 finalizer, and configured batch size.
- Produces: audited grants and scheduled expiration batches.

Use these exact grant contracts:

```java
public record GrantExtraAttemptsRequest(Integer quantity) {}

public record ExtraAttemptGrantResponse(
		UUID assignmentId,
		UUID studentId,
		long totalGranted,
		long used,
		long currentlyAvailable
) {}
```

- [ ] **Step 1: Write failing grant/expiration tests**

Create tests named `deveConcederTentativasComoProfessorDono`, `deveSomarConcessoes`, `deveRejeitarQuantidadeForaDeUmACem`, `deveRejeitarAtribuicaoIlimitada`, `deveExigirMatriculaAtiva`, `deveOcultarAtribuicaoDeOutroProfessor`, `deveBuscarLoteDeTentativasVencidas`, and `deveIgnorarFinalizacaoConcorrenteNoScheduler`.

```java
ExtraAttemptGrantResponse response = service.grant(
		teacherId, assignmentId, studentId, new GrantExtraAttemptsRequest(2)
);
assertThat(response.totalGranted()).isEqualTo(2);
assertThat(response.currentlyAvailable()).isEqualTo(2);
```

- [ ] **Step 2: Run and verify red**

Run: `rtk ./mvnw -Dtest=ExtraAttemptGrantServiceTest,AttemptExpirationServiceTest test`

Expected: FAIL because grant and scheduler services do not exist.

- [ ] **Step 3: Implement grants and scheduler**

Validate `quantity != null && quantity >= 1 && quantity <= 100` in `ExtraAttemptGrantService` and throw `422 INVALID_EXTRA_ATTEMPT_QUANTITY` so the stable domain code is not replaced by a generic Bean Validation error. Reject a null `maxAttempts` with `409 UNLIMITED_ATTEMPTS`. Add an attempt repository query returning at most `batchSize` expired IDs ordered by expiry. `AttemptExpirationService` uses:

```java
@Scheduled(fixedDelayString = "${app.attempt.expiration-fixed-delay:60000}")
public void expireDueAttempts();
```

`expireDueAttempts` iterates over the repository's bounded ID list and invokes `attemptService.expire(attemptId)`. Because `AttemptService` is a separate Spring bean, each call crosses the transactional proxy and does not suffer from scheduler self-invocation.

Place `@EnableScheduling` in `AttemptSchedulingConfig`, not `ContacertaApplication`, so the primary checkout's local application-class change is never involved.

- [ ] **Step 4: Run grant/expiration tests**

Run: `rtk ./mvnw -Dtest=ExtraAttemptGrantServiceTest,AttemptExpirationServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit grants and expiration**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/service/ExtraAttemptGrantService.java backend/src/main/java/com/ifsc/contacerta/service/AttemptExpirationService.java backend/src/main/java/com/ifsc/contacerta/config/AttemptSchedulingConfig.java backend/src/main/java/com/ifsc/contacerta/mapper/ExtraAttemptGrantMapper.java backend/src/main/java/com/ifsc/contacerta/dto/extraattempt backend/src/main/java/com/ifsc/contacerta/repository/AttemptRepository.java backend/src/test/java/com/ifsc/contacerta/service/ExtraAttemptGrantServiceTest.java backend/src/test/java/com/ifsc/contacerta/service/AttemptExpirationServiceTest.java
rtk git commit -m "feat: concede e expira tentativas"
```

### Task 9: Expose the Nine HTTP Routes

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/StudentLessonController.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/StudentAttemptController.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/controller/TeacherExtraAttemptController.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/exception/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/controller/StudentLessonControllerTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/controller/StudentAttemptControllerTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/controller/TeacherExtraAttemptControllerTest.java`

**Interfaces:**
- Consumes: service methods from Tasks 4–8 and authenticated `CurrentUser`.
- Produces: the nine routes from the approved design with exact status, Location, pagination, and Problem Details behavior.

- [ ] **Step 1: Write failing MockMvc tests**

Create route tests for all nine endpoints. Required named cases include `deveCriarTentativaComLocation`, `deveRetomarTentativaComStatusOk`, `deveExigirIdempotencyKey`, `deveOmitirGabaritoAntesDaFinalizacao`, `deveRegistrarResposta`, `deveSubmeterEExporResultado`, `deveListarTrilhaEHistorico`, `deveConcederTentativaExtra`, `deveExigirBearer`, `deveBloquearPerfilIncompativel`, `deveTraduzirOptimisticLockParaVersionConflict`, and `deveRetornarProblemDetailsDePropriedadeEConflito`.

```java
mockMvc.perform(post("/student/room-lessons/{assignmentId}/attempts", assignmentId)
			.header("Authorization", bearer(studentLogin))
			.header("Idempotency-Key", "attempt-1"))
		.andExpect(status().isCreated())
		.andExpect(header().string("Location", matchesPattern("/student/attempts/[0-9a-f-]{36}")))
		.andExpect(jsonPath("$.questions[0].correct").doesNotExist())
		.andExpect(jsonPath("$.questions[0].explanation").doesNotExist());
```

- [ ] **Step 2: Run and verify route failures**

Run: `rtk ./mvnw -Dtest=StudentLessonControllerTest,StudentAttemptControllerTest,TeacherExtraAttemptControllerTest test`

Expected: FAIL with `404` because the controllers do not exist.

- [ ] **Step 3: Implement thin controllers**

Map the approved routes exactly. `StudentAttemptController.start` declares `@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey` so the service can return stable code `IDEMPOTENCY_KEY_REQUIRED`; it converts `AttemptStartResult` into `ResponseEntity`, including the stored `Location` only when present. Use `@PageableDefault(size = 20)` and reject `size > 100` with `422 VALIDATION_ERROR`. Every method passes only `currentUser.userId()` and path/header/body inputs to its service. Add one `GlobalExceptionHandler` method for `ObjectOptimisticLockingFailureException` and `OptimisticLockException` that emits `409 VERSION_CONFLICT` through the existing `response(...)` helper.

- [ ] **Step 4: Run HTTP tests**

Run: `rtk ./mvnw -Dtest=StudentLessonControllerTest,StudentAttemptControllerTest,TeacherExtraAttemptControllerTest test`

Expected: PASS with all nine routes and the security matrix covered.

- [ ] **Step 5: Commit HTTP API**

```bash
rtk git add backend/src/main/java/com/ifsc/contacerta/controller/StudentLessonController.java backend/src/main/java/com/ifsc/contacerta/controller/StudentAttemptController.java backend/src/main/java/com/ifsc/contacerta/controller/TeacherExtraAttemptController.java backend/src/main/java/com/ifsc/contacerta/exception/GlobalExceptionHandler.java backend/src/test/java/com/ifsc/contacerta/controller/StudentLessonControllerTest.java backend/src/test/java/com/ifsc/contacerta/controller/StudentAttemptControllerTest.java backend/src/test/java/com/ifsc/contacerta/controller/TeacherExtraAttemptControllerTest.java
rtk git commit -m "feat: expoe tentativas e progresso"
```

### Task 10: Prove Concurrency and Complete the Slice

**Files:**
- Create: `backend/src/test/java/com/ifsc/contacerta/concurrency/AttemptConcurrencyTest.java`
- Modify only when a failing concurrency test identifies a concrete defect in files from Tasks 1–9.

**Interfaces:**
- Consumes: the complete persistence and service implementation.
- Produces: PostgreSQL-backed evidence for the three mandatory race conditions and a verified release candidate.

- [ ] **Step 1: Write failing concurrency tests**

Use two executor threads, independent `TransactionTemplate` transactions, a `CountDownLatch` start gate, and PostgreSQL through `PostgresIntegrationTest`. Create tests named `deveCriarUmaUnicaTentativaAtivaConcorrentemente`, `deveReproduzirMesmaChaveIdempotenteConcorrentemente`, `devePersistirUmaUnicaRespostaConcorrentemente`, and `deveCreditarXpUmaUnicaVezNaFinalizacaoConcorrente`. The idempotency race asserts that both calls return the same status, `Location`, body, and attempt ID, and that exactly one idempotency row exists.

```java
List<Future<AttemptResultResponse>> results = IntStream.range(0, 2)
		.mapToObj(index -> executor.submit(() -> {
			startGate.await();
			return transactionTemplate.execute(status -> attemptService.submit(studentId, attemptId));
		}))
		.toList();
startGate.countDown();
assertThat(results).allSatisfy(result -> assertThat(result.get().xpCredited()).isEqualTo(10));
assertThat(progressRepository.findByRoomIdAndStudentId(roomId, studentId).orElseThrow().getTotalXp())
		.isEqualTo(10);
```

- [ ] **Step 2: Run concurrency tests and observe any race failure**

Run: `rtk ./mvnw -Dtest=AttemptConcurrencyTest test`

Expected before hardening: at least one test must demonstrate either the database constraint or lock behavior. If all pass immediately, retain them as proof and do not introduce speculative code.

- [ ] **Step 3: Fix only reproduced concurrency defects**

For a reproduced race, add the narrowest missing lock, unique-conflict translation, or reload path in the responsible repository/service. Preserve these external results: one active attempt; identical answer replay versus changed-answer `409`; one finalized result and one progress credit.

- [ ] **Step 4: Run focused and full verification**

Run:

```bash
rtk ./mvnw -Dtest=AttemptConcurrencyTest test
rtk ./mvnw verify
rtk git diff --check main...HEAD
rtk git status --short
```

Expected: concurrency tests PASS; full suite reports zero failures/errors and builds the JAR; diff check prints nothing; worktree is clean after the final commit.

- [ ] **Step 5: Commit proven hardening when changes exist**

```bash
rtk git add backend/src/test/java/com/ifsc/contacerta/concurrency/AttemptConcurrencyTest.java backend/src/main/java/com/ifsc/contacerta
rtk git commit -m "test: valida concorrencia de tentativas"
```

If only the new concurrency test changed, the same commit contains that test alone. Do not create an empty commit.

- [ ] **Step 6: Review against the spec**

Read `docs/superpowers/specs/2026-08-27-backend-attempts-progress-design.md` line by line and verify each persistence field, route, status, error code, secrecy rule, expiration behavior, scoring formula, progress delta, configuration default, and test requirement against current files and fresh command output. Fix any Critical or Important finding with a red-green regression test and a separate atomic commit before branch completion.
