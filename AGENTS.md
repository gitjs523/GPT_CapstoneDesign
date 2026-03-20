# AGENTS.md

## Scope
- The following backend architecture and exception handling rules apply only to Java 21 Spring backend code.
- Do not apply these rules to frontend code, scripts, infrastructure code, or projects written in other languages unless explicitly requested.

## Backend architecture rules

### Package structure policy
- For Java 21 Spring backend code, organize packages by domain first.
- Do not use a top-level technical-layer-first package structure such as:
  - `controller`
  - `service`
  - `entity`
- Do not use a project-wide top-level structure such as:
  - `web`
  - `application`
  - `domain`
  - `infra`
    as the primary organization style.
- Prefer domain-first package organization, and then separate each domain by architectural role.

### Preferred package style
- Use domain-first package structures such as:
  - `user.web`
  - `user.application`
  - `user.domain`
  - `user.infra`
- Apply the same pattern consistently to other domains such as:
  - `auth.web`
  - `auth.application`
  - `auth.domain`
  - `auth.infra`
- Keep new code aligned with this domain-first architectural package convention.
- When generating or refactoring Java 21 Spring backend code, preserve this package structure.

### Architectural intent
- This project prefers domain-oriented package design because the domain boundary is considered the primary structural boundary.
- Within each domain, separate responsibilities into `web`, `application`, `domain`, and `infra`.
- Preserve domain cohesion and avoid scattering one domain’s code across project-wide technical packages.
- Favor a structure that can scale naturally toward larger systems and domain-oriented deployment boundaries.

### Exception policy
- For Java 21 Spring backend code, exception handling must be centralized under a global package.
- Do not create per-domain exception packages such as `user.exception`, `auth.exception`, `file.exception`, etc.
- Custom exceptions, exception handlers, error codes, and related response classes should be managed in a global exception structure.
- Preferred examples:
  - `global.exception`
  - `global.exception.handler`
  - `global.exception.code`
- Follow the centralized global exception management style used in this project.

### Error code and business exception convention
- For Java 21 Spring backend code, manage exception cases with a centralized `ErrorCode` enum.
- Prefer defining error metadata such as HTTP status and message in `ErrorCode`.
- When business errors occur, throw them through a `BusinessException`-type exception that wraps an `ErrorCode`.
- Prefer using a consistent exception flow such as:
  - define error in `ErrorCode`
  - throw `BusinessException(errorCode)`
  - handle it in a global exception handler
- Do not scatter ad-hoc exception messages or response definitions across domains.
- Reuse the existing `ErrorCode` / `BusinessException` style before introducing new exception patterns.

### Lombok convention
- Use Lombok selectively and explicitly.
- Avoid using `@AllArgsConstructor` indiscriminately to generate constructors.
- Avoid using `@Data` by default, especially when full setter-based mutability is not needed.
- Prefer `@RequiredArgsConstructor`, `@Getter`, and `@Builder` when they improve readability and make intent clearer.
- Choose Lombok annotations case by case based on class responsibility, readability, mutability, and design intent.
- Do not use Lombok in ways that hide important design intent or introduce unnecessary mutability.

### Spring coding conventions
- For Java 21 Spring backend code, use constructor injection by default.
- Keep request/response DTOs explicit and separated by purpose.
- Preserve clear boundaries inside each domain package between `web`, `application`, `domain`, and `infra`.
- Do not introduce a purely technical-layer-first package structure unless explicitly requested.

### Notes for agents
- This project prefers domain-first package separation over project-wide technical package separation in Java 21 Spring backend code.
- This project prefers centralized global exception management over domain-scoped exception management in Java 21 Spring backend code.
- This project prefers `ErrorCode`-driven exception handling with `BusinessException`-style usage in Java 21 Spring backend code.
- This project prefers selective Lombok usage such as `@RequiredArgsConstructor`, `@Getter`, and `@Builder` over broad annotations like `@AllArgsConstructor` and `@Data`.
- Before introducing a different structure, explain the reason explicitly.
- Do not change package organization conventions unless explicitly requested.