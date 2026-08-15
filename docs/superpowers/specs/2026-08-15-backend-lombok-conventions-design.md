# Backend Lombok Conventions Design

## Goal

Reduce mechanical Java code with Lombok while preserving explicit domain creation rules and safe JPA mappings.

## Conventions

- Use `@RequiredArgsConstructor` for Spring constructor injection in controllers, services, components, and exception handlers whose dependencies are `final`.
- Use `@Getter` for ordinary entity and exception accessors.
- Use `@NoArgsConstructor(access = AccessLevel.PROTECTED)` for the no-argument constructor required by JPA.
- Keep a constructor manual when it performs domain initialization or protects invariants, such as generating an identifier, copying a mutable collection, or setting an initial status.
- Do not use `@Data` on JPA entities. Generated `equals`, `hashCode`, `toString`, and setters can interact incorrectly with lazy relationships and mutable persistence state.
- Do not use `@AllArgsConstructor` on JPA entities because it would expose persistence-managed and audit fields as creation inputs.
- Continue using records for DTOs; Lombok adds no value to their existing generated API.

## Scope

Refactor existing backend services, components, controllers, handlers, exceptions, and entities where the annotations replace boilerplate without changing behavior. Add the convention to `backend/AGENTS.md`. No API contract, database schema, or business rule changes are included.

## Verification

- Search production Java sources for remaining mechanical constructors and getters.
- Ensure every remaining manual constructor contains domain initialization logic.
- Run the complete Maven test suite against PostgreSQL.
