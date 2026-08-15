# Backend Lombok Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace mechanical constructors and getters in the backend with Lombok while retaining explicit domain constructors that enforce initialization rules.

**Architecture:** Spring dependencies remain immutable `final` fields and receive Lombok-generated required-argument constructors. JPA entities receive generated getters and protected no-argument constructors, while their public domain constructors remain explicit.

**Tech Stack:** Java 21, Spring Boot 4.1, Lombok, JPA, JUnit 5, Maven Wrapper.

## Global Constraints

- Do not use `@Data`, `@Setter`, or `@AllArgsConstructor` on JPA entities.
- Do not change API contracts, persistence mappings, or business behavior.
- Keep manual constructors that generate UUIDs, defensively copy collections, or establish initial domain state.
- Use explicit imports and no wildcard imports.

---

### Task 1: Refactor Lombok boilerplate

**Files:**
- Modify: `backend/AGENTS.md`
- Modify: `backend/src/main/java/com/ifsc/contacerta/controller/InstitutionOptionController.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/exception/ApiException.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/exception/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/InstitutionService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/JoinCodeGenerator.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/service/RoomService.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/entity/Institution.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/entity/User.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/entity/Room.java`
- Modify: `backend/src/main/java/com/ifsc/contacerta/entity/RoomMembership.java`

**Interfaces:**
- Consumes: existing Lombok dependency and annotation processor configuration from `backend/pom.xml`.
- Produces: the same Java constructors and getters currently consumed by Spring, JPA, mappers, services, and tests.

- [ ] Add the approved Lombok rules to `backend/AGENTS.md`.
- [ ] Apply `@RequiredArgsConstructor` to Spring classes with final injected dependencies and delete their manual injection constructors.
- [ ] Apply `@Getter` to `ApiException`, keeping its semantic constructor because it also initializes `RuntimeException`.
- [ ] Apply `@Getter` and `@NoArgsConstructor(access = AccessLevel.PROTECTED)` to JPA entities and delete only mechanical accessors/no-argument constructors.
- [ ] Keep the defensive `Room.getContentTopics()` implementation and domain constructors with initialization logic.
- [ ] Run `rg` checks for redundant mechanical constructors/getters and forbidden wildcard imports.
- [ ] Run `.\mvnw.cmd test`; expect all tests to pass.
- [ ] Commit with `refactor: usa lombok no boilerplate do backend`.
