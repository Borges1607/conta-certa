# Backend Gamification Design

**Date:** 2026-08-29  
**Status:** Approved for implementation planning

## Objective

Implement the student gamification slice defined by `docs/backend-spec.md`: a paginated room ranking and a fixed catalog of room-scoped achievements. This slice exposes only the student endpoints. The teacher ranking remains part of the later reporting module.

The implementation builds on the existing attempt finalization and `room_student_progress` projection. There is no historical production data, so achievement unlocks start when this feature is deployed; no backfill or lazy reconciliation is required.

## Scope

In scope:

- `GET /student/rooms/{roomId}/ranking`;
- `GET /student/rooms/{roomId}/achievements`;
- persisted, idempotent achievement unlocks;
- achievement evaluation in the attempt-finalization transaction;
- deterministic, room-scoped ranking queries;
- anonymized student display names;
- correction of first-pass calculation so both `SUBMITTED` and `EXPIRED` finalized attempts are handled consistently.

Out of scope:

- `GET /teacher/reports/ranking`;
- teacher reports, dashboards, CSV exports, and financial tips;
- configurable achievements;
- historical achievement reconstruction;
- storing ranking positions.

## Architecture

The feature follows the existing layered backend structure:

```text
StudentGamificationController
              |
              v
      GamificationService
          /          \
         v            v
RankingRepository  AchievementUnlockRepository
                         ^
                         |
              AttemptFinalizationService
```

The controller handles transport validation and DTO conversion. `GamificationService` owns room-membership authorization, catalog assembly, ranking orchestration, and name anonymization. Repositories execute scoped persistence and projection queries. Controllers do not access repositories directly, and JPA entities are never exposed as API contracts.

Achievement evaluation is a focused service invoked by `AttemptFinalizationService` after updating `RoomStudentProgress`. It uses the updated projection and the finalized attempt result to determine newly unlocked achievements.

## Persistence

A Flyway migration creates `achievement_unlocks` with:

- `id UUID PRIMARY KEY`;
- `room_id UUID NOT NULL` referencing `rooms`;
- `student_id UUID NOT NULL` referencing `users`;
- `achievement_code VARCHAR NOT NULL`;
- `unlocked_at TIMESTAMPTZ NOT NULL`.

A unique constraint on `(room_id, student_id, achievement_code)` provides database-level idempotency. Indexes support lookup by room/student and achievement code. Achievement definitions are fixed in an enum or equivalent immutable code catalog; they are not database-managed configuration.

No rows are created for attempts finalized before deployment. New finalizations create unlocks using the same authoritative `finalizedAt` instant used for the attempt result.

Ranking position is never persisted. It is calculated from active room memberships with a left join to `room_student_progress`, so active students without a finalized attempt appear with zero XP, zero stars, and level 1.

## Achievement Catalog and Evaluation

The initial catalog contains exactly seven room-scoped achievements:

| Code | Criterion | Progress source |
|---|---|---|
| `FIRST_PASS` | At least one distinct assignment passed | `passedAssignmentCount` |
| `PERFECT_SCORE` | At least one finalized attempt with score 100 | current attempt/unlock state |
| `XP_100` | At least 100 room XP | `totalXp` |
| `XP_500` | At least 500 room XP | `totalXp` |
| `XP_1000` | At least 1,000 room XP | `totalXp` |
| `PASSED_5` | At least five distinct assignments passed | `passedAssignmentCount` |
| `PASSED_10` | At least ten distinct assignments passed | `passedAssignmentCount` |

Both `SUBMITTED` and `EXPIRED` attempts are official finalized results and can unlock achievements when their calculated result satisfies a criterion. The existing first-pass query must therefore consider both final statuses. Passing the same assignment again cannot increment `passedAssignmentCount` or unlock distinct-assignment achievements more than once.

On finalization:

1. calculate answers, score, pass status, stars, and incremental XP;
2. update `RoomStudentProgress`;
3. evaluate all seven definitions using the updated progress and current result;
4. persist only unlocks not already present, with `unlockedAt = finalizedAt`;
5. commit attempt, progress, and unlocks in the same transaction.

Repeated submission/finalization remains idempotent. Unlock insertion uses conflict-safe persistence (`ON CONFLICT DO NOTHING` or an equivalent atomic operation), backed by the unique constraint, so concurrent evaluation does not abort an otherwise valid finalization transaction.

## Ranking

The ranking contains only students whose membership in the requested room is `ACTIVE`. Removed students keep their progress and unlock history but disappear from the ranking until re-entry reactivates their membership. Active students without a progress row remain ranked with zero XP, zero stars, level 1, and no completion time.

Ordering is fixed and not client-configurable:

1. `totalXp DESC`;
2. `totalBestStars DESC`;
3. earliest first finalized assignment completion, ascending;
4. `studentId ASC` as a deterministic technical tie-breaker.

When XP and stars are equal, students with a completion precede students without one. The earliest completion is derived in the ranking query from finalized attempts; it is not added to `room_student_progress` solely for this feature. Position is sequential according to the total ordering.

The repository uses PostgreSQL projections/window functions or equivalent dedicated queries so pagination and position calculation occur in the database. The service must not load the entire room ranking into memory.

## Student Name Anonymization

Every ranking entry, including the authenticated student's own entry, uses the same backend-produced display name:

- trim and normalize whitespace;
- use the first token as the displayed first name;
- append the first letter of the final token followed by a period when a surname exists;
- keep a single-token name unchanged.

Examples:

- `Ana Beatriz Souza` becomes `Ana S.`;
- `João Silva` becomes `João S.`;
- `Madonna` remains `Madonna`.

The response never exposes full peer names or email addresses.

## API Contracts

### Room ranking

`GET /student/rooms/{roomId}/ranking?page=0&size=20`

`page` defaults to `0`; `size` defaults to `20` and has a maximum of `100`. Ranking sort is fixed.

```json
{
  "content": [
    {
      "position": 1,
      "studentId": "uuid",
      "displayName": "Ana S.",
      "totalXp": 540,
      "totalStars": 18,
      "level": 6,
      "currentStudent": false
    }
  ],
  "self": {
    "position": 37,
    "studentId": "uuid",
    "displayName": "Luiz M.",
    "totalXp": 120,
    "totalStars": 7,
    "level": 2,
    "currentStudent": true
  },
  "page": 0,
  "size": 20,
  "totalElements": 48,
  "totalPages": 3
}
```

`self` is always returned and is repeated in `content` when the current student belongs to the requested page. This stable contract lets the frontend display the authenticated student's position without special paging requests.

### Room achievements

`GET /student/rooms/{roomId}/achievements`

```json
{
  "content": [
    {
      "code": "FIRST_PASS",
      "title": "Primeira aprovação",
      "description": "Aprove uma lição nesta sala.",
      "current": 1,
      "target": 1,
      "unlocked": true,
      "unlockedAt": "2026-08-29T20:00:00Z"
    }
  ]
}
```

The endpoint always returns all seven definitions in catalog order. Locked achievements have `unlocked = false` and `unlockedAt = null`. `current` is capped at `target` for presentation. `PERFECT_SCORE` reports progress as `0` or `1`; its persisted unlock determines the value after the unlocking transaction.

The catalog is not paginated because it is fixed and small.

## Authorization and Errors

Both endpoints require an authenticated `STUDENT`. The service verifies an `ACTIVE` membership for the requested room and scopes every query by both room and student where applicable.

- unauthenticated requests return `401` through Spring Security;
- incompatible roles return `403` through route security;
- a missing room or a room not actively accessible by the student returns `404`, preventing enumeration;
- invalid pagination returns the project's RFC 9457 validation response;
- no progress row is treated as zero progress, level 1, zero stars, and zero completed/passed assignments, not as an error.

Gamification data never crosses room or institution boundaries.

## Testing

Unit tests cover:

- all seven achievement criteria;
- XP boundaries at 99/100, 499/500, and 999/1,000;
- five- and ten-distinct-pass boundaries;
- `SUBMITTED` and `EXPIRED` official results;
- repeat finalization and duplicate-unlock idempotency;
- display-name anonymization, including whitespace and single-token names.

Repository tests with PostgreSQL/Testcontainers cover:

- XP, stars, earliest-completion, and UUID tie-breakers;
- students with no completion;
- exclusion and re-inclusion of removed/reactivated memberships;
- database pagination, positions, totals, and the self row;
- room isolation;
- the unlock uniqueness constraint.

Controller/API tests cover:

- response contracts and catalog order;
- `self` inside and outside the selected page;
- peer anonymization and absence of full names/emails;
- page defaults and maximum size validation;
- `401`, `403`, and concealed `404` behavior.

Finalization integration tests verify that attempt result, progress update, and achievement unlocks commit atomically and that a repeated operation does not create duplicate unlocks.

## Acceptance Criteria

- Ranking and achievements are always scoped to the selected room.
- Only active room members appear in ranking results.
- The student always receives their own current ranking position.
- Peer identity is anonymized by the backend.
- Ranking order and positions are deterministic.
- All seven achievements are returned with criteria, progress, unlock state, and unlock time.
- New unlocks use the official attempt finalization time and are persisted in the same transaction.
- Repeated results and repeated finalization do not create duplicate XP, pass counts, or unlocks.
- Both submitted and expired official results behave consistently.
- The complete backend test suite remains green with PostgreSQL/Testcontainers.
