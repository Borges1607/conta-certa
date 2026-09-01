# Backend Authorization and Institutional Isolation Design

## Objective

Harden authorization across the backend so authenticated users cannot discover or access resources owned by another teacher, institution, student, or room membership. Preserve the existing layered architecture and stable HTTP error contracts without introducing a generic authorization framework.

## Authorization Policy

- Administrators remain global only in administrative use cases.
- Teachers and students must belong to an institution.
- A teacher can access only resources they own, even when another teacher belongs to the same institution.
- Institutional membership is an additional boundary and does not grant resource sharing between teachers.
- A student can access room content only through an active membership in a room from the student's institution.
- Resource identifiers supplied by clients never establish ownership. The authenticated user's identifier is the source of the caller's identity.

## HTTP Error Semantics

- Return `401 Unauthorized` when the token or session is invalid, expired, or revoked.
- Return `403 Forbidden` when the authenticated account has the wrong role or lacks functional permission for the operation.
- Return `404 Not Found` when a resource does not exist or is outside the authenticated user's ownership, institution, or active membership scope.
- Return `409 Conflict` or `422 Unprocessable Content` only for business rules evaluated after access to the resource has been established.
- Do not reveal whether an inaccessible cross-tenant resource exists.
- Joining a room from another institution returns the same `404 ROOM_NOT_FOUND` contract as an invalid join code.
- Replace accidental persistence exceptions such as `NoSuchElementException` with stable `ApiException` responses.

## Architecture

Authentication continues to validate the access token, session, active account, and claimed role on every request. Controllers extract the authenticated user and delegate to services. Services enforce domain authorization through explicit repository queries that include the required ownership or membership scope.

The preferred query patterns are:

- teacher-owned resource: resource identifier plus owner teacher identifier;
- student resource: resource identifier plus student identifier and active room membership;
- nested resource: all identifiers required to prove that the objects belong to the same authorized chain;
- institutional operation: authenticated user's institution identifier plus the target identifier;
- administrative operation: explicit administrative service path, without reusing it as an operational bypass.

Direct `findById` remains acceptable when loading the authenticated user itself or in an explicitly administrative flow. Protected domain resources must use scoped repository methods. Small private service methods may express composed rules, but the design will not add global Hibernate filters or a centralized, domain-aware authorization framework.

## Protected Resource Chains

The implementation must preserve and validate these relationships before applying business rules:

- teacher to room;
- teacher to lesson, question, video, material, and stored file;
- teacher to lesson or media assignment through the owned room;
- student to room through an active membership;
- student to published lesson or media through the authorized room assignment;
- student to attempt, answer, and question snapshot through the attempt owner;
- teacher to extra-attempt grant through the owned room assignment and the room's student membership;
- teacher report filters to owned rooms and lessons, and students enrolled in the filtered room;
- student progress, ranking, and achievements to the requesting student's active room membership.

For every nested operation, identifiers must resolve inside a single authorized chain. Independently valid identifiers from different rooms, teachers, students, or institutions must result in `404`.

## Request Flow

1. Spring Security authenticates the request and validates the persisted session and active user.
2. The controller passes the authenticated user's identifier to the service. Owner identifiers from request bodies or query parameters are not trusted.
3. The service queries the resource with the complete ownership, institution, or membership scope.
4. A missing scoped result produces the resource's stable `404` response.
5. Role restrictions produce `403` responses.
6. Only after authorization succeeds does the service evaluate publication state, archival state, attempt limits, idempotency, ordering, and other business rules.

## Implementation Scope

The hardening covers:

- rooms and room memberships;
- lessons, questions, and lesson assignments;
- videos, materials, stored files, and media assignments;
- attempts, attempt answers, question snapshots, and extra-attempt grants;
- student progress, gamification, ranking, and teacher reports.

The change may add or replace explicit Spring Data repository methods and focused service validation helpers. It must preserve controller-to-service-to-repository dependency flow and must not expose JPA entities through API contracts.

## Out of Scope

- database schema changes or Flyway migrations;
- resource sharing between teachers;
- a general-purpose authorization framework;
- Hibernate tenant filters;
- authentication redesign;
- unrelated business-rule or API refactoring.

## Testing Strategy

Implementation will be test-driven and include regression coverage at the narrowest effective layer:

- repository tests proving scoped queries do not return a resource for a different owner, institution, student, or room;
- service tests for another teacher, another institution, a student without membership, and a removed membership;
- nested-identifier tests proving resources from different authorized chains cannot be combined;
- controller security tests covering the relevant `401`, `403`, and `404` contracts;
- room join tests proving an invalid code and a cross-institution code both return `404 ROOM_NOT_FOUND`;
- positive tests for each hardened path to ensure legitimate access remains available;
- full `./mvnw verify` before completion.

No formal coverage percentage is required, but each corrected authorization weakness must have a regression test.

## Acceptance Criteria

- A teacher cannot read or mutate another teacher's resources, including within the same institution.
- A teacher cannot combine owned and unowned identifiers in a nested operation.
- A student cannot access room resources without an active membership in that room and institution.
- A student cannot access another student's attempts, answers, private progress, or achievements. Ranking rows remain visible only through the existing ranking contract and only to students with an active membership in that room.
- Cross-tenant and cross-owner resource access returns an indistinguishable `404` response.
- Wrong-role access returns `403`; invalid authentication returns `401`.
- Administrative global access remains limited to administrative use cases.
- Existing legitimate teacher and student flows continue to pass.
- The complete Maven verification suite passes.
