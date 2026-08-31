# Backend Question Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tornar criação, edição, ordenação, exclusão e duplicação de questões consistentes, concorrentes e compatíveis com o histórico imutável de tentativas.

**Architecture:** `QuestionService` continuará concentrando autorização, transações e validação, bloqueando pessimisticamente a aula antes de qualquer mutação. As entidades cuidarão das transições internas do agregado; consultas explícitas decidirão posição e exclusão física, enquanto snapshots existentes permanecerão intocados.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA/Hibernate, Jakarta Validation, PostgreSQL 18 via Testcontainers, JUnit 5, AssertJ, Mockito e Maven Wrapper.

**Spec:** `docs/superpowers/specs/2026-08-31-backend-question-lifecycle-design.md`

## Global Constraints

- Aulas `DRAFT` e `PUBLISHED` aceitam mutações; aulas `ARCHIVED` retornam `422 LESSON_ARCHIVED` para toda mutação de questão.
- Alterações afetam apenas tentativas futuras; `attempt_question_snapshots` e `attempt_option_snapshots` existentes nunca são atualizados.
- Toda alocação ou alteração de posição ocorre após lock pessimista da aula proprietária.
- Reordenação inclui questões ativas e arquivadas e mantém a restrição única imediata por `(lesson_id, position)`.
- Exclusão física só ocorre quando a aula nunca foi atribuída e a questão nunca foi referenciada por snapshot; nos demais casos ocorre arquivamento idempotente.
- Duplicação de aula copia apenas questões ativas, com configuração completa, nova identidade e status `DRAFT`.
- Controllers permanecem sem acesso direto a repositories e entidades JPA não são contratos da API.
- Use imports explícitos, Lombok conforme o padrão do repositório e o Maven Wrapper em todos os comandos.
- Toda correção deve ter teste de regressão; locks e constraints devem ser exercitados no PostgreSQL/Testcontainers.

---

## File Map

- `src/main/java/com/ifsc/contacerta/repository/LessonRepository.java`: busca owner-scoped com `PESSIMISTIC_WRITE`.
- `src/main/java/com/ifsc/contacerta/repository/QuestionRepository.java`: máximo de posição e existência de snapshots.
- `src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java`: existência de qualquer atribuição por aula.
- `src/main/java/com/ifsc/contacerta/entity/Question.java`: criação em posição explícita, substituição completa da configuração e cópia do agregado.
- `src/main/java/com/ifsc/contacerta/entity/QuestionOption.java`: atualização controlada de alternativa preservada.
- `src/main/java/com/ifsc/contacerta/entity/QuestionOptionData.java`: comando interno de alternativa com ID opcional.
- `src/main/java/com/ifsc/contacerta/dto/question/CreateQuestionRequest.java`: limites Jakarta e validação aninhada.
- `src/main/java/com/ifsc/contacerta/dto/question/UpdateQuestionRequest.java`: limites Jakarta e validação aninhada no PATCH.
- `src/main/java/com/ifsc/contacerta/dto/question/QuestionOptionRequest.java`: ID opcional e limite de texto já compatíveis com o schema.
- `src/main/java/com/ifsc/contacerta/service/QuestionService.java`: lock, merge/validação, reorder em duas fases e delete/archive.
- `src/main/java/com/ifsc/contacerta/service/LessonService.java`: duplicação transacional das questões ativas.
- `src/test/java/com/ifsc/contacerta/service/QuestionLifecycleIntegrationTest.java`: regressões reais de posição, lock, snapshots e ciclo de vida.
- `src/test/java/com/ifsc/contacerta/service/QuestionServiceTest.java`: matriz unitária de configuração e erros de domínio.
- `src/test/java/com/ifsc/contacerta/service/LessonServiceTest.java`: cópia integral e exclusão de arquivadas.
- `src/test/java/com/ifsc/contacerta/controller/TeacherQuestionControllerTest.java`: contrato HTTP e Jakarta Validation.

---

### Task 1: Allocate and Reorder Positions Safely

**Files:**
- Modify: `src/main/java/com/ifsc/contacerta/repository/LessonRepository.java`
- Modify: `src/main/java/com/ifsc/contacerta/repository/QuestionRepository.java`
- Modify: `src/main/java/com/ifsc/contacerta/entity/Question.java`
- Modify: `src/main/java/com/ifsc/contacerta/entity/QuestionOptionData.java`
- Modify: `src/main/java/com/ifsc/contacerta/service/QuestionService.java`
- Create: `src/test/java/com/ifsc/contacerta/service/QuestionLifecycleIntegrationTest.java`

**Interfaces:**
- Consumes: `PostgresIntegrationTest`, `LessonRepository.findByIdAndTeacherId(UUID, UUID)` and the current create/reorder DTOs.
- Produces: `LessonRepository.findByIdAndTeacherIdForUpdate(UUID lessonId, UUID teacherId)`, `QuestionRepository.findMaximumPositionByLessonId(UUID lessonId)`, `Question.create(..., int position)`, and collision-free `QuestionService.create/reorder`.

- [ ] **Step 1: Write PostgreSQL regressions for sequential create and swap**

```java
class QuestionLifecycleIntegrationTest extends PostgresIntegrationTest {

	@Autowired private QuestionService questionService;
	@Autowired private QuestionRepository questionRepository;

	@Test
	void deveCriarSegundaQuestaoNaPosicaoDois() {
		Fixture fixture = fixtureWithDraftLesson();
		questionService.create(fixture.teacherId(), fixture.lessonId(), choice("Q1"));
		questionService.create(fixture.teacherId(), fixture.lessonId(), choice("Q2"));

		assertThat(questionRepository.findByLessonIdOrderByPositionAsc(fixture.lessonId()))
				.extracting(Question::getPosition)
				.containsExactly(1, 2);
	}

	@Test
	void deveTrocarPosicoesSemViolarRestricaoUnica() {
		Fixture fixture = fixtureWithTwoQuestions();
		questionService.reorder(fixture.teacherId(), fixture.lessonId(),
				new QuestionOrderRequest(List.of(fixture.secondId(), fixture.firstId())));

		assertThat(questionRepository.findByLessonIdOrderByPositionAsc(fixture.lessonId()))
				.extracting(Question::getId)
				.containsExactly(fixture.secondId(), fixture.firstId());
	}
}
```

- [ ] **Step 2: Run the focused tests and verify the current defects**

Run: `./mvnw -Dtest=QuestionLifecycleIntegrationTest#deveCriarSegundaQuestaoNaPosicaoDois+deveTrocarPosicoesSemViolarRestricaoUnica test`

Expected: FAIL; the second insert or direct swap violates the unique `(lesson_id, position)` constraint.

- [ ] **Step 3: Add the owner-scoped lock and maximum-position query**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select lesson from Lesson lesson where lesson.id = :lessonId and lesson.teacher.id = :teacherId")
Optional<Lesson> findByIdAndTeacherIdForUpdate(
		@Param("lessonId") UUID lessonId,
		@Param("teacherId") UUID teacherId
);
```

```java
@Query("select coalesce(max(question.position), 0) from Question question where question.lesson.id = :lessonId")
int findMaximumPositionByLessonId(@Param("lessonId") UUID lessonId);
```

- [ ] **Step 4: Make position explicit in the domain factory**

```java
public record QuestionOptionData(UUID id, String text, boolean correct) {
}
```

```java
public static Question create(
		Lesson lesson,
		QuestionType type,
		String prompt,
		String explanation,
		List<QuestionOptionData> options,
		int position
) {
	return new Question(lesson, type, prompt, explanation, options, position);
}
```

The constructor assigns `this.position = position`; creation maps request options with `id = null`. Update all existing factory call sites and test fixtures in the same change.

- [ ] **Step 5: Lock create and implement two-phase reorder**

```java
Lesson lesson = requireOwnedLessonForUpdate(teacherId, lessonId);
requireMutable(lesson);
int position = questionRepository.findMaximumPositionByLessonId(lessonId) + 1;
Question question = Question.create(lesson, type, prompt, explanation, options, position);
```

```java
int maximum = questions.stream().mapToInt(Question::getPosition).max().orElse(0);
int temporaryStart = maximum + questions.size() + 1;
for (int index = 0; index < questions.size(); index++) {
	questions.get(index).moveTo(temporaryStart + index);
}
questionRepository.flush();

Map<UUID, Question> byId = questions.stream()
		.collect(Collectors.toMap(Question::getId, Function.identity()));
for (int index = 0; index < request.questionIds().size(); index++) {
	byId.get(request.questionIds().get(index)).moveTo(index + 1);
}
questionRepository.flush();
```

Validate size, uniqueness and exact ID set before the temporary phase; use imported `HashSet`, `Map`, `Function` and `Collectors`, never inline FQNs.

- [ ] **Step 6: Add and run a concurrent-create regression**

Use two executor tasks, each calling `questionService.create` after a shared `CountDownLatch`; collect both futures and assert the persisted positions are exactly `1, 2`.

```java
start.countDown();
first.get(10, TimeUnit.SECONDS);
second.get(10, TimeUnit.SECONDS);
assertThat(questionRepository.findByLessonIdOrderByPositionAsc(lessonId))
		.extracting(Question::getPosition)
		.containsExactly(1, 2);
```

Run: `./mvnw -Dtest=QuestionLifecycleIntegrationTest test`

Expected: PASS; no `DataIntegrityViolationException`, duplicate position, or timeout.

- [ ] **Step 7: Commit the independently passing position work**

```bash
git add backend/src/main backend/src/test/java/com/ifsc/contacerta/service/QuestionLifecycleIntegrationTest.java
git commit -m "fix: garante ordenacao consistente de questoes"
```

---

### Task 2: Implement Complete Partial PATCH and Immutable Snapshot Behavior

**Files:**
- Modify: `src/main/java/com/ifsc/contacerta/dto/question/CreateQuestionRequest.java`
- Modify: `src/main/java/com/ifsc/contacerta/dto/question/UpdateQuestionRequest.java`
- Verify: `src/main/java/com/ifsc/contacerta/dto/question/QuestionOptionRequest.java`
- Modify: `src/main/java/com/ifsc/contacerta/entity/Question.java`
- Modify: `src/main/java/com/ifsc/contacerta/entity/QuestionOption.java`
- Modify: `src/main/java/com/ifsc/contacerta/service/QuestionService.java`
- Modify: `src/test/java/com/ifsc/contacerta/service/QuestionServiceTest.java`
- Modify: `src/test/java/com/ifsc/contacerta/service/QuestionLifecycleIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1's `QuestionOptionData(UUID, String, boolean)`, locked lesson lookup, and explicit question position.
- Produces: `Question.replaceConfiguration(...)`, nested request validation, option-ID ownership validation, and complete merged PATCH semantics.

- [ ] **Step 1: Write unit tests for every family transition and invalid merged state**

Add parameterized cases covering `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `TRUE_FALSE`, and `NUMERIC`. Each case updates from a different family and asserts incompatible fields are cleared.

```java
@Test
void deveTrocarQuestaoNumericaParaVerdadeiroFalsoELimparConfiguracao() {
	Question question = numericQuestion();
	when(questionRepository.findByIdAndLessonTeacherId(question.getId(), teacherId))
			.thenReturn(Optional.of(question));
	when(lessonRepository.findByIdAndTeacherIdForUpdate(question.getLesson().getId(), teacherId))
			.thenReturn(Optional.of(question.getLesson()));

	QuestionResponse response = service.update(teacherId, question.getId(),
			new UpdateQuestionRequest(null, QuestionType.TRUE_FALSE, null, null,
					true, null, null, null, null, question.getVersion()));

	assertThat(response.correctBoolean()).isTrue();
	assertThat(response.correctNumericValue()).isNull();
	assertThat(response.absoluteTolerance()).isNull();
	assertThat(response.unit()).isNull();
	assertThat(response.decimalPlaces()).isNull();
	assertThat(response.options()).isEmpty();
}
```

Also assert `INVALID_QUESTION_OPTIONS`, `INVALID_TRUE_FALSE_ANSWER`, `INVALID_NUMERIC_CONFIGURATION`, `INVALID_QUESTION_OPTION` for a foreign option ID, and `VERSION_CONFLICT`.

- [ ] **Step 2: Run the unit tests and observe the incomplete PATCH**

Run: `./mvnw -Dtest=QuestionServiceTest test`

Expected: FAIL because `update` currently changes only prompt and explanation and the entity cannot replace type-specific configuration.

- [ ] **Step 3: Add precise Jakarta constraints and nested validation**

Keep the existing `@NotBlank @Size(max = 500)` option text and optional option ID. Apply `List<@Valid QuestionOptionRequest>` to both requests and `@Digits(integer = 13, fraction = 6)` to numeric values/tolerance. Keep `@NotBlank`/`@NotNull` on create and `@NotNull @Min(0)` on update version. Do not invent a length cap for prompt/explanation because PostgreSQL stores both as `text`.

Confirm the existing option request remains exactly:

```java
public record QuestionOptionRequest(
		UUID id,
		@NotBlank @Size(max = 500) String text,
		boolean correct
) {
}
```

- [ ] **Step 4: Add aggregate replacement methods**

```java
void update(String text, boolean correct, int position) {
	this.text = text;
	this.correct = correct;
	this.position = position;
}
```

```java
public void replaceConfiguration(
		QuestionType type,
		String prompt,
		String explanation,
		List<QuestionOptionData> optionData,
		Boolean correctBoolean,
		BigDecimal correctNumericValue,
		BigDecimal absoluteTolerance,
		NumericUnit unit,
		Integer decimalPlaces
) {
	this.type = type;
	this.prompt = prompt;
	this.explanation = explanation;
	replaceOptions(optionData);
	this.correctBoolean = type == QuestionType.TRUE_FALSE ? correctBoolean : null;
	this.correctNumericValue = type == QuestionType.NUMERIC ? correctNumericValue : null;
	this.absoluteTolerance = type == QuestionType.NUMERIC ? absoluteTolerance : null;
	this.unit = type == QuestionType.NUMERIC ? unit : null;
	this.decimalPlaces = type == QuestionType.NUMERIC ? decimalPlaces : null;
}
```

`replaceOptions` indexes current options by ID, rejects any supplied ID absent from that map with `IllegalArgumentException`, reuses and updates matching entities, creates entities for null IDs, and replaces the list in request order. For non-choice types pass an empty list.

- [ ] **Step 5: Merge PATCH fields, validate the resulting state, then mutate**

Create a private service record `QuestionConfiguration` containing the nine resulting fields. `merge(Question, UpdateQuestionRequest)` preserves current values for null PATCH fields except that a changed family starts with empty/inapplicable family fields. `validate(QuestionConfiguration)` applies the exact rules from the spec before `replaceConfiguration`.

```java
QuestionConfiguration configuration = merge(question, request);
validate(configuration);
try {
	question.replaceConfiguration(configuration.type(), configuration.prompt(),
			configuration.explanation(), configuration.options(), configuration.correctBoolean(),
			configuration.correctNumericValue(), configuration.absoluteTolerance(),
			configuration.unit(), configuration.decimalPlaces());
} catch (IllegalArgumentException exception) {
	throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_QUESTION_OPTION",
			"Question option does not belong to this question.");
}
```

- [ ] **Step 6: Prove old snapshots stay immutable and new snapshots use edited content**

In the PostgreSQL integration test, persist an attempt and capture its first snapshot; update the source question; start a second attempt through `AttemptService`; clear the persistence context between reads.

```java
assertThat(oldSnapshotRepository.findById(oldSnapshotId).orElseThrow().getPrompt())
		.isEqualTo("Antes");
assertThat(newSnapshotRepository.findByAttemptIdOrderByPositionAsc(newAttemptId).getFirst().getPrompt())
		.isEqualTo("Depois");
```

Run: `./mvnw -Dtest=QuestionServiceTest,QuestionLifecycleIntegrationTest test`

Expected: PASS for all family transitions, option ownership, stale version, and snapshot temporal behavior.

- [ ] **Step 7: Commit the complete PATCH**

```bash
git add backend/src/main backend/src/test/java/com/ifsc/contacerta/service
git commit -m "fix: completa atualizacao de questoes"
```

---

### Task 3: Enforce Archived Lessons and Delete-or-Archive Semantics

**Files:**
- Modify: `src/main/java/com/ifsc/contacerta/repository/LessonAssignmentRepository.java`
- Modify: `src/main/java/com/ifsc/contacerta/repository/QuestionRepository.java`
- Modify: `src/main/java/com/ifsc/contacerta/service/QuestionService.java`
- Modify: `src/test/java/com/ifsc/contacerta/service/QuestionServiceTest.java`
- Modify: `src/test/java/com/ifsc/contacerta/service/QuestionLifecycleIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1's locked lesson mutation path and Task 2's complete update.
- Produces: `LessonAssignmentRepository.existsByLessonId(UUID)`, `QuestionRepository.existsSnapshotByQuestionId(UUID)`, `requireMutable(Lesson)`, and idempotent delete/archive behavior.

- [ ] **Step 1: Write delete-policy PostgreSQL regressions**

Add separate tests asserting:

```java
questionService.delete(teacherId, unusedQuestionId);
assertThat(questionRepository.findById(unusedQuestionId)).isEmpty();
```

```java
questionService.delete(teacherId, assignedQuestionId);
assertThat(questionRepository.findById(assignedQuestionId)).get()
		.extracting(Question::isActive).isEqualTo(false);
```

Repeat the second delete and assert it still succeeds. Add the same archival assertion for an unassigned question referenced by an attempt snapshot.

- [ ] **Step 2: Write a mutation matrix for archived lessons**

Parameterized invocations cover `create`, `update`, `duplicate` into the archived lesson, `reorder`, and `delete`. Every invocation must produce:

```java
assertThatThrownBy(mutation)
		.isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
			assertThat(exception.getCode()).isEqualTo("LESSON_ARCHIVED");
		});
```

- [ ] **Step 3: Run tests and verify physical-only deletion/read-write defects**

Run: `./mvnw -Dtest=QuestionServiceTest,QuestionLifecycleIntegrationTest test`

Expected: FAIL because deletion is unconditional and most mutation paths do not inspect lesson status.

- [ ] **Step 4: Add explicit existence queries**

```java
boolean existsByLessonId(UUID lessonId);
```

```java
@Query("select (count(snapshot) > 0) from AttemptQuestionSnapshot snapshot where snapshot.question.id = :questionId")
boolean existsSnapshotByQuestionId(@Param("questionId") UUID questionId);
```

- [ ] **Step 5: Centralize the archived guard and implement the policy**

Every mutation first resolves the owner-scoped locked lesson, then calls:

```java
private void requireMutable(Lesson lesson) {
	if (lesson.getStatus() == ContentStatus.ARCHIVED) {
		throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
				"LESSON_ARCHIVED", "Archived lessons are read-only.");
	}
}
```

Deletion becomes:

```java
boolean assigned = lessonAssignmentRepository.existsByLessonId(lesson.getId());
boolean snapshotted = questionRepository.existsSnapshotByQuestionId(question.getId());
if (!assigned && !snapshotted) {
	questionRepository.delete(question);
	return;
}
question.archive();
```

`Question.archive()` remains naturally idempotent and positions are not compacted.

- [ ] **Step 6: Run the lifecycle suite**

Run: `./mvnw -Dtest=QuestionServiceTest,QuestionLifecycleIntegrationTest test`

Expected: PASS; unused questions disappear, assigned/snapshotted questions remain inactive, and all archived-lesson mutations return the same domain error.

- [ ] **Step 7: Commit lifecycle enforcement**

```bash
git add backend/src/main backend/src/test/java/com/ifsc/contacerta/service
git commit -m "fix: aplica ciclo de vida das questoes"
```

---

### Task 4: Duplicate the Complete Active Lesson Aggregate

**Files:**
- Modify: `src/main/java/com/ifsc/contacerta/entity/Question.java`
- Modify: `src/main/java/com/ifsc/contacerta/service/LessonService.java`
- Modify: `src/test/java/com/ifsc/contacerta/service/LessonServiceTest.java`
- Modify: `src/test/java/com/ifsc/contacerta/service/QuestionLifecycleIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1's explicit position factory and Task 2's complete question configuration model.
- Produces: `Question.duplicateInto(Lesson target, int position)` and atomic lesson duplication with active questions only.

- [ ] **Step 1: Write unit and persistence regressions for complete duplication**

Build a source lesson containing active choice, boolean and numeric questions plus one archived question. Assert:

```java
LessonDetailResponse response = lessonService.duplicate(teacherId, sourceLessonId);
Lesson copy = lessonRepository.findById(response.id()).orElseThrow();
List<Question> copied = questionRepository.findByLessonIdOrderByPositionAsc(copy.getId());

assertThat(copy.getStatus()).isEqualTo(ContentStatus.DRAFT);
assertThat(copied).hasSize(3).extracting(Question::getPosition).containsExactly(1, 2, 3);
assertThat(copied).noneMatch(question -> sourceQuestionIds.contains(question.getId()));
assertThat(copied.getFirst().getOptions()).noneMatch(
		option -> sourceOptionIds.contains(option.getId()));
assertThat(copied).extracting(Question::getType)
		.containsExactly(QuestionType.SINGLE_CHOICE, QuestionType.TRUE_FALSE, QuestionType.NUMERIC);
```

Also assert prompt, explanation, correctness, boolean value, numeric value, tolerance, unit and decimal places match their active source counterparts.

- [ ] **Step 2: Run duplication tests and observe metadata-only behavior**

Run: `./mvnw -Dtest=LessonServiceTest,QuestionLifecycleIntegrationTest test`

Expected: FAIL because `LessonService.duplicate` currently saves only lesson metadata.

- [ ] **Step 3: Add a complete domain copy operation**

```java
public Question duplicateInto(Lesson target, int targetPosition) {
	List<QuestionOptionData> copiedOptions = options.stream()
			.map(option -> new QuestionOptionData(null, option.getText(), option.isCorrect()))
			.toList();
	Question copy = Question.create(target, type, prompt, explanation, copiedOptions, targetPosition);
	copy.replaceConfiguration(type, prompt, explanation, copiedOptions, correctBoolean,
			correctNumericValue, absoluteTolerance, unit, decimalPlaces);
	return copy;
}
```

- [ ] **Step 4: Copy active questions in the existing transaction**

```java
Lesson copy = lessonRepository.save(source.duplicate(source.getTitle() + " (cópia)"));
List<Question> questions = questionRepository
		.findByLessonIdAndActiveTrueOrderByPositionAsc(source.getId());
for (int index = 0; index < questions.size(); index++) {
	questionRepository.save(questions.get(index).duplicateInto(copy, index + 1));
}
```

Keep the method `@Transactional`, so any failure rolls back both lesson and questions.

- [ ] **Step 5: Run tests and commit**

Run: `./mvnw -Dtest=LessonServiceTest,QuestionLifecycleIntegrationTest test`

Expected: PASS; active aggregate configuration matches, all copied IDs differ, archived questions are absent, and positions are contiguous.

```bash
git add backend/src/main/java/com/ifsc/contacerta/entity/Question.java backend/src/main/java/com/ifsc/contacerta/service/LessonService.java backend/src/test/java/com/ifsc/contacerta/service
git commit -m "feat: duplica questoes ativas da aula"
```

---

### Task 5: Lock the HTTP Contract and Run the Full Quality Gate

**Files:**
- Create: `src/test/java/com/ifsc/contacerta/controller/TeacherQuestionControllerTest.java`
- Modify if a regression exposes a mismatch: `src/main/java/com/ifsc/contacerta/exception/GlobalExceptionHandler.java`
- Modify if a regression exposes a mismatch: `src/main/java/com/ifsc/contacerta/controller/TeacherQuestionController.java`

**Interfaces:**
- Consumes: Tasks 1–4 service behavior and existing `GlobalExceptionHandler` response format.
- Produces: stable HTTP status/code coverage for validation, archived lessons, invalid order and optimistic conflicts.

- [ ] **Step 1: Add focused MockMvc contract tests**

Use the controller's existing authenticated-teacher setup. Cover malformed nested option, out-of-range numeric scale, archived lesson, invalid reorder and stale version.

```java
mockMvc.perform(patch("/api/teacher/questions/{questionId}", questionId)
		.with(jwt().jwt(jwt -> jwt.subject(teacherId.toString())))
		.contentType(MediaType.APPLICATION_JSON)
		.content("""
				{"options":[{"text":"","correct":true}],"version":0}
				"""))
		.andExpect(status().isUnprocessableEntity())
		.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
```

For mocked service failures, throw `ApiException` and assert `LESSON_ARCHIVED`/422, `INVALID_QUESTION_ORDER`/422 and `VERSION_CONFLICT`/409.

- [ ] **Step 2: Run the controller test and correct only observed contract mismatches**

Run: `./mvnw -Dtest=TeacherQuestionControllerTest test`

Expected: PASS. If it fails, make the smallest controller/handler change required to preserve the exact status and codes defined in the spec; do not introduce a new response shape.

- [ ] **Step 3: Run all question/lesson regression tests together**

Run: `./mvnw -Dtest=QuestionServiceTest,QuestionLifecycleIntegrationTest,LessonServiceTest,TeacherQuestionControllerTest test`

Expected: PASS with no failures or errors.

- [ ] **Step 4: Run the complete Maven quality gate**

Run: `./mvnw verify`

Expected: BUILD SUCCESS and all tests pass.

- [ ] **Step 5: Inspect formatting and scope**

Run: `git diff --check`

Expected: no output.

Run: `git status --short`

Expected: only the intended lifecycle implementation and test files are listed.

- [ ] **Step 6: Commit the contract coverage**

```bash
git add backend/src/main backend/src/test
git commit -m "test: cobre contrato do ciclo de questoes"
```

- [ ] **Step 7: Review final history and diff before integration**

Run: `git log --oneline main..HEAD`

Expected: the four implementation commits plus this contract-test commit, with the earlier design commit retained.

Run: `git diff --stat main...HEAD`

Expected: changes remain limited to the design/plan documentation and question/lesson lifecycle code and tests.
