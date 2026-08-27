# Backend Attempts and Progress Design

## Goal

Implement step 5 of `docs/backend-spec.md`: the authenticated student learning path, attempt lifecycle, immutable snapshots and answers, scoring, expiration, extra-attempt grants, and room-scoped progress. The server remains the source of truth for availability, time, score, passing, stars, and XP.

This slice exposes the student lesson and attempt endpoints plus the teacher extra-attempt endpoint. Ranking, achievements, room dashboards, media, reports, and account onboarding remain in their later roadmap slices.

## Architecture

The subsystem uses synchronous transactions and a materialized room progress projection. Attempt finalization persists the result and updates progress atomically; it does not introduce events or eventual consistency.

Responsibilities are separated into focused components:

- `StudentLessonService`: visible room path, lock reasons, lesson detail, and attempt history;
- `AttemptService`: start, resume, retrieve, answer, submit, result, and expiration orchestration;
- `AttemptScoringService`: pure correction rules for the four question types;
- `StudentProgressService`: prerequisites, best-result deltas, XP, stars, level, and completion;
- `ExtraAttemptGrantService`: teacher authorization and audited grants;
- `AttemptExpirationService`: scheduled batch discovery and delegation to the same finalizer;
- dedicated mappers: public in-progress DTOs and completed-result DTOs.

Controllers validate transport input and delegate to services. Repositories own persistence queries and locks. JPA entities are never API contracts.

## Persistence Model

Flyway migration `V7__create_attempt_and_progress_tables.sql` adds the following tables.

### `attempts`

- `id`, `assignment_id`, `student_id`, and one-based `sequence`;
- `status`: `IN_PROGRESS`, `SUBMITTED`, or `EXPIRED`;
- `started_at`, nullable `expires_at`, and nullable `submitted_at`;
- `total_questions`, `answered_questions`, and `correct_answers`;
- nullable `score_percent`, `passed`, `stars`, and `xp_credited` until finalization;
- `created_at`, `updated_at`, and optimistic-lock `version`.

Constraints enforce a unique sequence per student and assignment, one partial-unique `IN_PROGRESS` row per student and assignment, non-negative totals, `answered_questions <= total_questions`, `correct_answers <= total_questions`, score in `0..100`, stars in `0..3`, and non-negative XP.

### `attempt_question_snapshots`

Each row belongs to an attempt and freezes:

- original `question_id`, type, prompt, explanation, and one-based position;
- boolean or numeric gabarito where applicable;
- numeric tolerance, unit, and decimal places.

The pair `(attempt_id, position)` and `(attempt_id, question_id)` is unique. Snapshots are immutable after insert.

### `attempt_option_snapshots`

Each row belongs to a question snapshot and freezes the original option identifier, text, correctness, and one-based position. The public API uses the snapshot option identifier, never the mutable source option identifier. The pair `(question_snapshot_id, position)` is unique.

### `attempt_answers`

One immutable row per question snapshot stores the normalized answer shape, `correct`, and `answered_at`. Choice answers use `attempt_answer_selected_options`, unique per answer and option snapshot. Database checks prevent multiple payload shapes on one answer where possible; the service enforces the question-type-specific shape.

### `extra_attempt_grants`

Each grant stores assignment, student, granting teacher, positive quantity, and `created_at`. Rows are append-only and constitute the required audit trail. Available attempts use the sum of all grants; grants never delete history.

### `room_student_progress`

One row per room and student stores total XP, level, total best stars, completed-assignment count, passed-assignment count, last activity, timestamps, and version. The `(room_id, student_id)` pair is unique. The projection can be rebuilt from finalized attempts.

### `idempotency_records`

Each record stores user, HTTP method, route scope, key, SHA-256 request hash, response status, response content type, response `Location`, response body snapshot, optional attempt reference, creation time, and expiration time. The `(user_id, key)` pair is unique; method, route scope, and request hash must all match on replay. Default retention is 24 hours and is configurable.

Indexes cover active-attempt lookup, student history, assignment results, expired-attempt batches, prerequisite checks, progress by room, grants, and idempotency expiration.

## API Contract

The authenticated endpoints are:

```text
GET  /student/rooms/{roomId}/lessons
GET  /student/rooms/{roomId}/lessons/{lessonId}
GET  /student/rooms/{roomId}/lessons/{lessonId}/attempts
POST /student/room-lessons/{assignmentId}/attempts
GET  /student/attempts/{attemptId}
PUT  /student/attempts/{attemptId}/answers/{questionSnapshotId}
POST /student/attempts/{attemptId}/submit
GET  /student/attempts/{attemptId}/result
POST /teacher/room-lessons/{assignmentId}/students/{studentId}/extra-attempts
```

All resources derive the caller from the authenticated principal. Cross-student, cross-teacher, cross-room, and inactive-membership lookups use ownership-scoped queries and return `404`.

### Student lesson path

`GET /student/rooms/{roomId}/lessons` returns the complete published assignment path ordered by position. Each item includes lesson summary, availability, limits, best result, attempts used and available, and one lock state:

- `AVAILABLE`;
- `NOT_OPEN_YET`;
- `CLOSED`;
- `PREREQUISITE_REQUIRED`;
- `ATTEMPT_LIMIT_REACHED`.

An existing `IN_PROGRESS` attempt keeps its assignment resumable even when the normal attempt limit has since been reached. Archived rooms remain readable but do not permit new attempts.

`GET /student/rooms/{roomId}/lessons/{lessonId}` resolves the unique room assignment for that lesson and returns theory plus assignment configuration and progress. It never exposes source question content or gabaritos.

Attempt history is paginated with the project standard `PageResponse`, defaults to page 0 and size 20, and permits at most size 100.

### Starting an attempt

`POST /student/room-lessons/{assignmentId}/attempts` requires a non-blank `Idempotency-Key` header.

- A new attempt returns `201 Created`, `Location`, and a public attempt DTO.
- Replaying the same key and same request scope returns the stored original status, headers, and body.
- Reusing a key with a different request hash or scope returns `409 IDEMPOTENCY_KEY_REUSED`.
- A new key while an attempt is already `IN_PROGRESS` returns that attempt with `200 OK` and does not create a row.

The public attempt DTO contains snapshot IDs, prompts, types, shuffled option IDs/text, numeric presentation metadata, current answer values, start/expiry times, and server time. It omits correctness, gabaritos, and explanations.

### Recording an answer

The accepted JSON shape depends on the snapshot type:

```json
{ "selectedOptionIds": ["uuid"] }
{ "booleanValue": true }
{ "numericValue": "100.50" }
```

Exactly one shape is accepted. Numeric input accepts comma or point, is normalized to `BigDecimal`, and is stored canonically. An identical replay returns the existing `{ "correct", "answeredAt" }`; any attempted change returns `409 ANSWER_ALREADY_RECORDED`. An expired or finalized attempt rejects new answers.

### Submission and result

Submission is idempotent and returns the completed result. If server time is at or after `expiresAt`, the status is `EXPIRED`; otherwise it is `SUBMITTED`. Both states are final and include omitted questions as incorrect.

`GET /result` returns `409 ATTEMPT_IN_PROGRESS` before finalization. After finalization it returns score, passing, stars, credited XP, and full answer review including gabaritos and explanations.

### Extra attempts

The teacher request is `{ "quantity": 1 }`, with a positive maximum of 100 per grant. The assignment must belong to the teacher and the student must have an active membership in its room. A grant against an unlimited assignment is rejected as unnecessary. The response includes total granted, used, and currently available attempts.

## Starting Rules and Snapshot Selection

Starting an attempt requires:

- an active `STUDENT` account and active membership;
- a non-archived room;
- a `PUBLISHED` assignment within its availability window;
- a passed finalized attempt for the immediately preceding published assignment;
- remaining base attempts plus grants, unless the assignment is unlimited;
- enough currently active lesson questions for the configured question count.

The membership row is pessimistically locked before active-attempt and sequence checks. Database uniqueness is the final concurrency guard.

If `questionCount` is null, all active questions are selected. When `shuffleQuestions` is false, selection and order follow source position; when true, a `SecureRandom`-backed `RandomGenerator` shuffles before truncation. `shuffleOptions` independently controls option order. Snapshot inserts and the attempt row share one transaction.

`expiresAt` is null only when both time limit and due date are null. Otherwise it is the earliest of `startedAt + timeLimit` and `dueAt`.

## Scoring and Progress

Correction uses only snapshots:

- `SINGLE_CHOICE`: exactly one selected snapshot option and it is correct;
- `MULTIPLE_CHOICE`: the selected ID set exactly equals the correct ID set;
- `TRUE_FALSE`: submitted boolean equals the frozen boolean;
- `NUMERIC`: `abs(answer - expected) <= tolerance`.

The score is `correctAnswers * 100 / totalQuestions`, rounded to the nearest integer with `HALF_UP`. Passing uses the room's frozen current `passingScorePercent` at finalization. Stars use the normative thresholds: below 50 is zero, 50–69 is one, 70–89 is two, and 90–100 is three.

Potential XP is `correctAnswers * 10`. Credited XP is only the positive difference between this potential and the best prior potential for the same student and assignment. Repeating or worsening a result credits zero.

Progress totals use best-per-assignment deltas:

- XP adds only newly credited XP;
- stars add only a positive improvement over previous best stars;
- completed assignments count an assignment on its first finalized attempt;
- passed assignments count it on its first passing result;
- level is `floor(roomXpTotal / 100) + 1`;
- last activity advances on answer and finalization.

The finalizer pessimistically locks the attempt and the membership/progress scope. Attempt result and progress update commit atomically, so concurrent finalization cannot double-credit rewards.

## Expiration

A configurable scheduler, enabled by default every 60 seconds, selects at most 100 expired `IN_PROGRESS` attempt IDs per batch. A separate transactional finalizer processes each ID, obtains the same locks as manual submission, and safely ignores an attempt already finalized by another request. Fetching, answering, submitting, or requesting the result of an expired attempt also triggers the same expiration path before responding.

## Error Contract

The existing RFC 9457 infrastructure is reused. Stable codes include:

- identity and scope: `STUDENT_NOT_FOUND`, `STUDENT_REQUIRED`, `ACCOUNT_INACTIVE`, `MEMBERSHIP_NOT_FOUND`, `ASSIGNMENT_NOT_FOUND`, `ATTEMPT_NOT_FOUND`, `QUESTION_SNAPSHOT_NOT_FOUND`;
- availability: `ROOM_ARCHIVED`, `ASSIGNMENT_NOT_AVAILABLE`, `ASSIGNMENT_CLOSED`, `PREREQUISITE_NOT_MET`, `ATTEMPT_LIMIT_REACHED`, `ASSIGNMENT_CONTENT_UNAVAILABLE`;
- lifecycle: `ATTEMPT_FINISHED`, `ATTEMPT_IN_PROGRESS`, `ANSWER_ALREADY_RECORDED`;
- payload: `INVALID_ANSWER`, `INVALID_EXTRA_ATTEMPT_QUANTITY`, `UNLIMITED_ATTEMPTS`;
- idempotency and concurrency: `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_REUSED`, `VERSION_CONFLICT`.

Known role mismatch uses `403`. Invisible resources use `404`. Invalid domain payloads use `422`. Lifecycle, attempt-limit, idempotency, and version conflicts use `409`.

## Testing

- Persistence integration tests validate Flyway v7, mappings, constraints, immutable snapshot relationships, partial uniqueness, indexes, and optimistic locking on PostgreSQL through Testcontainers.
- Unit tests cover all scoring types, exact-set multiple choice, tolerance boundaries, percentage rounding, stars, XP delta, level, selection, availability, prerequisites, limits, and extra grants.
- Service tests cover identity, ownership, locks, idempotency, resume, expiration, snapshot creation, immutable answer replay, submission, and progress updates.
- Concurrency integration tests race attempt creation, answer insertion, and finalization and assert one active attempt, one answer, and one reward credit.
- MockMvc tests cover all nine routes, authentication/role matrix, validation, Problem Details, pagination, status and Location semantics, and absence of gabarito/explanation before finalization.
- The full `./mvnw verify` suite must pass before completion.

## Delivery

Work occurs on `feat/backend-attempts-progress`, created from updated `main`, in an isolated worktree. Commits remain independently reviewable: design, persistence, scoring, start/snapshots, answers, finalization/progress, student queries, grants/expiration, HTTP API, and final hardening. Existing local changes in the primary checkout are excluded.
