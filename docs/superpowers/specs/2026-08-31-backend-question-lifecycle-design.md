# Backend Question Lifecycle Design

## Objective

Make question creation, editing, ordering, deletion, and lesson duplication reliable while preserving the immutable history of attempts. Teachers may edit assigned or published content for future attempts; existing attempt snapshots must never change.

## Scope

This design covers:

- collision-free question positions;
- complete question PATCH behavior;
- physical deletion versus logical archival;
- read-only behavior for archived lessons;
- full lesson duplication, including active questions;
- PostgreSQL integration and concurrency tests.

It does not introduce a general content revision system, change attempt scoring, or alter historical snapshots.

## Content lifecycle

A lesson in `DRAFT` or `PUBLISHED` status may have its questions created, edited, reordered, duplicated, archived, or deleted by its owner. Editing published or assigned content affects only attempts started after the transaction commits. Attempts already started continue to use `attempt_question_snapshots` and `attempt_option_snapshots`.

An `ARCHIVED` lesson is read-only. Every question mutation returns `422 LESSON_ARCHIVED`, including create, update, duplicate into the lesson, reorder, and delete/archive.

Question lists continue to expose archived questions to the teacher with `archived = true`. Student attempt creation uses only active questions.

## Position allocation and ordering

Question position is unique within a lesson. Mutation flows acquire a pessimistic write lock on the owning lesson before reading or changing its question collection. Creation and duplication allocate `max(position) + 1` while holding that lock.

Reordering validates that the request contains every question ID in the lesson exactly once, including archived questions because the teacher list and its positions cover the complete collection. It then performs a two-phase update:

1. move every current position into a disjoint temporary range and flush;
2. assign final positions `1..N` in request order and flush.

The temporary range is derived from the collection size and existing maximum, so it cannot overlap current or final positions. This keeps the existing immediate unique constraint and avoids schema-specific deferred constraints.

## Complete question updates

PATCH is a partial update. The service merges supplied fields with the current question and validates the resulting complete configuration before mutating the entity.

The resulting state obeys these rules:

- `SINGLE_CHOICE`: at least two options and exactly one correct option;
- `MULTIPLE_CHOICE`: at least two options and at least two correct options;
- `TRUE_FALSE`: no options and a non-null boolean answer;
- `NUMERIC`: no options, non-null answer, non-negative tolerance, non-null unit, and non-negative decimal places.

Changing type clears fields that do not belong to the new type. Choice options are replaced as one ordered aggregate. Existing option IDs may be retained only when they belong to the same question; omitted or new options receive new IDs. Snapshot option IDs and text remain untouched because snapshots are separate entities.

The request keeps optimistic `version` validation. Nested options receive cascaded Jakarta validation, and prompt/explanation/numeric constraints match the database limits.

## Delete and archive semantics

A question is physically deleted only when both conditions are true:

- the lesson has never been assigned to a room;
- no `attempt_question_snapshots` row references the question.

Otherwise, delete becomes logical archival by setting `active = false`. Repeating delete/archive is idempotent. The API returns no entity for the existing DELETE endpoint, so this distinction does not change its HTTP response.

Archiving preserves positions. New attempts exclude inactive questions, while historical attempts continue to resolve entirely from snapshots.

## Lesson duplication

Duplicating a lesson creates a new `DRAFT` lesson owned by the same teacher, with new IDs. It copies lesson metadata and every active question in position order, including:

- prompt, type, and explanation;
- ordered alternatives and their correctness;
- boolean answer;
- numeric answer, tolerance, unit, and decimal places.

Archived questions are not copied. The entire aggregate is created in one transaction; partial lesson copies are not committed.

## Repository and domain boundaries

`QuestionService` remains responsible for authorization, lesson lifecycle checks, transaction boundaries, validation, and DTO mapping. Domain entities own state transitions such as replacing configuration, moving position, and archiving.

`LessonRepository` gains an owner-scoped pessimistic-lock query. `QuestionRepository` gains explicit queries for maximum position and snapshot-reference checks. `LessonAssignmentRepository.existsByRoomIdAndLessonId` is complemented by a lesson-wide assignment existence query.

Controllers remain thin and repositories are not injected into controllers. JPA entities are not exposed as API contracts.

## Error behavior

- missing or foreign lesson/question: `404` with the existing domain code;
- archived lesson mutation: `422 LESSON_ARCHIVED`;
- invalid resulting question configuration: `422` with the existing type-specific code;
- invalid reorder set: `422 INVALID_QUESTION_ORDER`;
- stale version: `409 VERSION_CONFLICT`;
- malformed nested option or numeric constraint: `422 VALIDATION_ERROR`.

Database integrity exceptions are not used as normal control flow.

## Verification

Tests use PostgreSQL/Testcontainers for behaviors governed by constraints or locks. Required regression coverage includes:

- creating a second question assigns position 2;
- concurrent creation does not duplicate a position;
- swapping positions 1 and 2 succeeds;
- complete PATCH changes future snapshots but not an existing snapshot;
- changing between every question family clears incompatible state;
- deleting an unused draft question removes it physically;
- deleting an assigned or snapshotted question archives it;
- archived lessons reject every question mutation;
- lesson duplication copies active questions and their complete configuration with new IDs;
- archived questions are excluded from the duplicate;
- full `./mvnw verify` and `git diff --check` pass.

