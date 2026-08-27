# Backend Lesson Assignments Design

## Goal

Expose the teacher API that connects reusable lessons to an ordered room learning path. The slice covers assignment creation, listing, configuration, deletion, and reordering. Attempts, student progress, and extra-attempt grants remain in the subsequent attempts slice.

## Architecture

`LessonAssignment` is a dedicated JPA entity referencing `Room` and `Lesson`. A `LessonAssignmentRepository` provides direct, ownership-scoped queries; optional filter combinations are not needed, so this slice does not introduce a `Specification`.

`LessonAssignmentService` owns authorization, validation, ordering, and transactions. `TeacherLessonAssignmentController` validates HTTP input and delegates to the service. JPA entities are never exposed through the API.

All operations derive the teacher identifier from the authenticated principal. A room or lesson not owned by that teacher is returned as not found so the API does not disclose another teacher's resources.

## Persistence Model

The `lesson_assignments` table contains:

- `id`, `room_id`, and `lesson_id`;
- a one-based `position` unique within the room;
- `status`: `DRAFT`, `PUBLISHED`, or `ARCHIVED`;
- `available_from` and `due_at` as nullable UTC instants;
- nullable `time_limit_minutes` and `max_attempts`;
- nullable `question_count`;
- `shuffle_questions` and `shuffle_options`;
- `created_at`, `updated_at`, and optimistic-lock `version`.

The database enforces positive positions and positive non-null limits, a valid date interval, supported statuses, uniqueness of `(room_id, lesson_id)`, and uniqueness of `(room_id, position)`. Foreign-key and ordered-list indexes support the expected access paths.

Java creation defaults are `DRAFT`, 30 minutes, three attempts, all active questions, and both shuffle flags enabled. An explicitly supplied JSON `null` for time or attempts means unlimited; omission on creation applies the defaults. This omission-versus-null distinction is represented explicitly in the create request contract instead of being inferred after deserialization.

## API Contract

The authenticated teacher endpoints are:

```text
GET    /teacher/rooms/{roomId}/lesson-assignments
POST   /teacher/rooms/{roomId}/lesson-assignments
PATCH  /teacher/rooms/{roomId}/lesson-assignments/{assignmentId}
DELETE /teacher/rooms/{roomId}/lesson-assignments/{assignmentId}
PUT    /teacher/rooms/{roomId}/lesson-assignments/order
```

Listing returns the complete room path ordered by `position`; this collection is intentionally not paginated because ordering is an aggregate-wide operation. Responses include assignment configuration, lesson summary data, timestamps, and `version`.

Creation accepts the contract from `docs/backend-spec.md` and returns `201` with a resource location. If `position` is omitted, the assignment is appended. Inserting at an occupied position shifts that position and all later assignments by one.

Patch uses nullable fields as "unchanged" except for the fields whose domain supports clearing. Clearable values use explicit presence semantics so a client can distinguish omission from JSON `null`. Every mutation supplies the current `version`.

Reordering receives the complete ordered assignment list, with each assignment identifier and current version. The service rejects missing, duplicated, foreign, or extra identifiers and then assigns contiguous positions from one.

## Business Rules

- The teacher account must be active.
- The room must belong to the teacher and must not be archived.
- The lesson must belong to the same teacher.
- A lesson can appear only once in a room.
- A published assignment requires a published lesson and at least one active question.
- `questionCount`, when set, must be positive and cannot exceed the number of active lesson questions.
- `timeLimitMinutes` and `maxAttempts`, when set, must be positive.
- `dueAt`, when set, must be later than `availableFrom`; if `availableFrom` is absent, `dueAt` must be later than the current server time when the assignment is published.
- An archived assignment is read-only.
- Deletion is allowed for a draft assignment or a published assignment whose `availableFrom` is strictly in the future. A published assignment without `availableFrom`, an already opened or expired assignment, and an archived assignment return `409 ASSIGNMENT_ALREADY_IN_USE`; the supported alternative is archiving through patch.
- Removal closes the positional gap in the same transaction.
- Publishing, inserting, removing, and reordering leave positions contiguous and unique.

## Concurrency and Ordering

Assignment updates use `@Version`, and request versions are checked before mutation to return `409 VERSION_CONFLICT` deterministically. Reordering locks the room's assignment rows for writing before validating versions and changing positions.

To preserve the unique position constraint during shifts and arbitrary reorder operations, the service first moves affected rows to collision-free temporary positions, flushes, then writes their final one-based positions. The whole operation is transactional, so callers never observe the temporary order.

## Errors

The service uses the existing Problem Details infrastructure. Stable error codes include:

- `TEACHER_NOT_FOUND`, `TEACHER_REQUIRED`, and `ACCOUNT_INACTIVE`;
- `ROOM_NOT_FOUND`, `ROOM_ARCHIVED`, `LESSON_NOT_FOUND`, and `ASSIGNMENT_NOT_FOUND`;
- `LESSON_NOT_PUBLISHED`, `LESSON_ALREADY_ASSIGNED`, and `ASSIGNMENT_ARCHIVED`;
- `INVALID_ASSIGNMENT_DATES`, `INVALID_ASSIGNMENT_LIMIT`, and `INSUFFICIENT_ACTIVE_QUESTIONS`;
- `INVALID_ASSIGNMENT_ORDER`, `ASSIGNMENT_ALREADY_IN_USE`, and `VERSION_CONFLICT`.

Ownership failures use `404`. Invalid domain values and insufficient questions use `422`. Duplicate assignment, concurrent version, invalid complete reorder, and deletion of an in-use assignment use `409`.

## Testing

- Persistence integration tests prove Flyway migration compatibility, mappings, constraints, indexes required by queries, and optimistic locking.
- Repository tests prove ordered, teacher-scoped retrieval and write locking.
- Service unit tests cover authorization, defaults, explicit unlimited values, publication requirements, dates, limits, insertion shifts, gap closing, full reorder validation, deletion rules, archived immutability, and version conflicts.
- Controller integration tests cover authentication, role restrictions, request validation, all five routes, response status/location, JSON contracts, and Problem Details mappings.
- The complete Maven suite runs against PostgreSQL through Testcontainers before completion.

## Delivery

Work occurs on `feat/backend-lesson-assignments`, created from the updated `main`, in an isolated worktree. Commits remain independently reviewable: design, persistence, domain/service behavior, HTTP API, and final hardening or documentation. Existing local changes in the primary checkout are not included.
