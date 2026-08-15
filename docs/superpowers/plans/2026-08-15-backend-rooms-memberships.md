# Backend Rooms and Memberships Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the persisted domain and service layer for teacher-owned rooms and multi-room student memberships, without exposing protected HTTP endpoints before JWT authorization exists.

**Architecture:** Add `Room` and `RoomMembership` entities under the approved technical-layer package structure. Services enforce same-institution membership, unique regenerable join codes, removal/reactivation with history preservation, archiving, and room duplication. Controllers are explicitly deferred until Spring Security/JWT is implemented.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, PostgreSQL 18, Flyway, JUnit 5, Mockito, Maven Wrapper.

## Global Constraints

- Follow `backend/AGENTS.md` and the package flow `controller -> service -> repository -> entity`; this plan creates no controllers.
- Use UUID identifiers and UTC `Instant` timestamps.
- A room has exactly one teacher and belongs to that teacher's institution.
- `Grade` is `HIGH_SCHOOL_1`, `HIGH_SCHOOL_2`, or `HIGH_SCHOOL_3`.
- Passing score defaults to 50 and must be between 0 and 100.
- Join code has six uppercase alphanumeric characters, is unique, and can be regenerated.
- A student can belong to multiple rooms, but a membership pair `(room, student)` is unique.
- Students may join only rooms in their institution.
- Removing a student preserves the row/history; rejoining changes it back to active.
- Archived rooms reject joining and future mutation.
- No JWT, Spring Security, protected controller, lesson, attempt, XP, or report code in this plan.
- Every behavior follows RED → GREEN → full focused verification → small commit.

---

### Task 1: Room and membership schema/entities

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__create_room_and_membership_tables.sql`
- Create: `backend/src/main/java/com/ifsc/contacerta/model/Grade.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/model/MembershipStatus.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/Room.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/entity/RoomMembership.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/entity/RoomPersistenceTest.java`

**Interfaces:**
- Produces `Room(name, description, grade, topics, passingScorePercent, joinCode, teacher, institution)`.
- Produces `RoomMembership(room, student)` with status `ACTIVE`.

- [ ] Write a failing `@SpringBootTest @Transactional` persistence test that saves a teacher, room, and student membership, then asserts teacher/institution/grade/topics/default active status.
- [ ] Run `.\mvnw.cmd -Dtest=RoomPersistenceTest test`; expect compilation failure for missing room types.
- [ ] Add V2 tables `rooms`, `room_topics`, and `room_memberships` with foreign keys, unique join code, unique room/student pair, checks, timestamps, indexes, and optimistic versions.
- [ ] Implement JPA mappings with protected no-arg constructors and domain getters.
- [ ] Run focused test and `.\mvnw.cmd test`; expect all green.
- [ ] Commit: `feat: persiste salas e matriculas`.

### Task 2: Repositories and join-code generation

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/RoomRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/repository/RoomMembershipRepository.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/JoinCodeGenerator.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/repository/RoomRepositoryTest.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/JoinCodeGeneratorTest.java`

**Interfaces:**
- `RoomRepository.findByJoinCode(String)`, `existsByJoinCode(String)`, and teacher-owned pagination/query methods.
- `RoomMembershipRepository.findByRoomIdAndStudentId(UUID, UUID)` and active membership queries.
- `JoinCodeGenerator.generateUnique(): String` retries repository collisions and returns six characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`.

- [ ] Write RED tests for case-normalized join lookup, membership pair lookup, six-character generation, and collision retry.
- [ ] Implement repositories and generator using injected `SecureRandom`/testable random source.
- [ ] Run focused tests and full suite.
- [ ] Commit: `feat: adiciona repositorios de salas`.

### Task 3: Room creation, update, code regeneration, and archive

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/room/CreateRoomRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/room/UpdateRoomRequest.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/room/RoomResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mapper/RoomMapper.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/RoomService.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/RoomServiceTest.java`

**Interfaces:**
- `RoomService.create(UUID teacherId, CreateRoomRequest): RoomResponse`.
- `RoomService.update(UUID teacherId, UUID roomId, UpdateRoomRequest): RoomResponse`.
- `RoomService.regenerateCode(UUID teacherId, UUID roomId): RoomResponse`.
- `RoomService.archive(UUID teacherId, UUID roomId): void`.

- [ ] RED: creating a room derives institution from active teacher, defaults passing score to 50, and assigns unique code.
- [ ] GREEN: implement minimal DTOs, mapper, entity mutation methods, and service authorization by owner.
- [ ] RED/GREEN: reject non-teacher/inactive teacher, invalid score, wrong owner, and archived mutation using explicit `ApiException` codes.
- [ ] RED/GREEN: regenerate code and archive idempotently.
- [ ] Run full suite.
- [ ] Commit: `feat: adiciona servico de salas`.

### Task 4: Join, remove, and reactivate membership

**Files:**
- Create: `backend/src/main/java/com/ifsc/contacerta/dto/room/RoomMembershipResponse.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/mapper/RoomMembershipMapper.java`
- Create: `backend/src/main/java/com/ifsc/contacerta/service/RoomMembershipService.java`
- Test: `backend/src/test/java/com/ifsc/contacerta/service/RoomMembershipServiceTest.java`

**Interfaces:**
- `join(UUID studentId, String joinCode): RoomMembershipResponse`.
- `remove(UUID teacherId, UUID roomId, UUID studentId): void`.

- [ ] RED/GREEN: join validates active student, active room, same institution, and normalized code.
- [ ] RED/GREEN: repeated active join is idempotent; removed membership is reactivated with history retained.
- [ ] RED/GREEN: owner teacher removes membership; student cannot self-remove because no such service interface exists.
- [ ] RED/GREEN: archived room and cross-institution join return `422`/`403` domain errors.
- [ ] Run full suite.
- [ ] Commit: `feat: implementa matriculas em salas`.

### Task 5: Duplicate room and completion gate

**Files:**
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/RoomService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/entity/Room.java`
- Modify: `backend/src/test/java/com/ifsc/contacerta/service/RoomServiceTest.java`

**Interfaces:**
- `RoomService.duplicate(UUID teacherId, UUID roomId, String newName): RoomResponse`.

- [ ] RED: duplication copies description, grade, topics, score, and teacher/institution, but generates new id/code and no memberships.
- [ ] GREEN: implement only aggregate copy; lesson assignments do not exist yet and therefore are not copied in this plan.
- [ ] Run `.\mvnw.cmd clean test` and confirm zero failures.
- [ ] Confirm `git status --short` is clean after commit and no controller/security/JWT files were added.
- [ ] Commit: `feat: adiciona duplicacao de salas`.
