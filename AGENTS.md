# Agent Instructions

This file is addressed to AI coding agents (Copilot, Cursor, Codeium, Claude Code, etc.).

## Mandatory Project Context Reads

- Before planning or implementing any code change, read all other project-level Markdown files to gather context.
- At minimum, read `README.md`, `ARCHITECTURE.md`, and `TODO.md` (in addition to this `AGENTS.md`) at the start of a task.
- Additional reference files: `REFACTORING.md` (code quality audit), `docs/recommendation_expansion_research.md` (feature ideas).
- If additional `.md` files are added later, read those too before making changes.

## Build Environment

- **Java 25** via Gradle Toolchains (configured in `build.gradle`).
- **Gradle daemon** runs on **JDK 21** (`org.gradle.java.home` in `gradle.properties`), while the toolchain compiles and runs the application on **JDK 25** (`org.gradle.java.installations.paths`).
- **Spring Boot 3.5.15** (ASM support for Java 25), **Lombok 1.18.46** (Java 25/26 compiler support).
- **Gradle commands** (`./gradlew test`, `./gradlew bootRun`, etc.) require `--enable-native-access=ALL-UNNAMED` for SQLite JNI — already configured in `build.gradle`.
- Standard tasks can be run directly from the root folder; `gradle.properties` handles JDK installation paths on macOS.
- **Taskfile** available for convenience: `task build`, `task run`, `task start`, `task test`.

## Goals

- Implement an anime recommendation CLI that:
  - Uses Spring Boot for runtime.
  - Supports Spring Shell CLI interface.
  - Integrates with MyAnimeList for authentication, metadata, and recommendations.
- Keep the design modular and explicit, with clear separation between:
  - Interface layer (CLI commands).
  - Service/business logic.
  - Infrastructure (HTTP clients, persistence, scheduling).

## Constraints

- Use **Java 25** and **Spring Boot 3.x**.
- Keep existing JPA/Flyway/SQLite setup unless explicitly asked to change it.
- Do **not** generate a frontend in this repository.
- Keep external calls behind small, focused services/clients.
- Avoid duplicating business logic across different command classes.
- No HTTP API, no Python service split, no microservice decomposition.

## Design Decisions

These are established patterns in the codebase — follow them, don't fight them.

### Series Grouping (Franchise Consolidation)
- MAL's `related_anime` relations form an undirected graph. Run connected-components BFS to partition distinct franchises.
- Intermediate related titles act as bridge nodes connecting parts that don't link directly.
- **Representative title**: the entry with the *highest popularity* (lowest popularity rank number) in the entire connected component, including non-seasonal bridge nodes.
- **Rating calculation**: **straight arithmetic average** of scores across all entries in the component. Do not weight by review count — early seasons dominate by volume, later seasons have fewer but higher ratings due to viewer attrition. Straight average treats each season as equal.
- This pattern is used in both `seasonal` and `recommendation` commands.

### Recommendation Occurrence Weighting
- Completed shows in the same franchise are grouped. All MAL-suggested similar titles from all shows in the group are pooled.
- Each occurrence is scaled by `avgGroupUserScore / 7.0` (using `1.0` if the show was unrated).
- Temporal decay (optional): `w = 2^(-t / decayMonths)` where `t` is elapsed months since completion.
- Freshness boost (optional): `b = exp(-freshnessBoost × yearsSinceRelease)` applied at the candidate level.
- When both temporal parameters are enabled: `Final Score = Base Score × Taste Decay × Freshness Boost`.

### API Request Retry Strategy
- **Fail-fast (no retry)**: 404, 401, 403 — permanent states.
- **Retryable**: 429 rate limits, 5xx server errors, network `IOException`.
- Implemented exclusively via `MalRetryInterceptor` (Spring `ClientHttpRequestInterceptor`) on the `apiKeyClient` bean; services do not run outer retry loops.
- Attempt limits count total HTTP attempts; nonpositive limits retry indefinitely until interrupted. Backoff is capped at 15 seconds. Interrupted requests stop before another HTTP attempt and retain the interrupt flag.
- Malformed, HTML, and null payloads fail immediately without retry. Permanent HTTP errors and interruption do not use expired-cache fallback.
- Configured in `application.properties`: `mal.retry.max-attempts`, `mal.retry.initial-backoff-ms`, `mal.retry.backoff-multiplier`.

### Caching Strategy
- SQLite-backed raw JSON response caching (`anime_cache` and `generic_cache` tables).
- TTL: 30 days for past seasons, 1 day for current/future seasons and user lists, 7 days for anime details.
- **Fail-safe fallback**: expired cache is returned when the API is unreachable rather than throwing.

### Error Propagation
- **Permanent errors (404)**: Wrapped in `MalResourceNotFoundException`, caught in detail-fetching loops to skip the item cleanly.
- **Transient errors** (HTML responses, 429, 5xx, network): rethrown immediately, aborting the operation to guarantee recommendations are never built on incomplete data.

### DTO/Entity Separation
- `TokenResponse` is a pure external client response DTO (package `models.dto`) with Jackson annotations, decoupled from JPA.
- `TokenEntity` is a dedicated database entity (package `entity`) mapped to the SQLite `tokens` table.
- Services perform explicit mapping — API contract changes must not corrupt the database schema.

## Implementation Patterns

### Interactive CLI Prompting
- Use Spring Shell's `ComponentFlow.Builder` for interactive prompts — do **not** use manual `LineReader.readLine()`.
- The builder is auto-configured by `ComponentFlowAutoConfiguration`. Do not define a custom `ComponentFlow.Builder` bean.
- To reset for a fresh flow, call `builder.clone().reset()` — this clears component registrations while preserving `Terminal` and `ResourceLoader`.
- Interactive prompts are built and executed **only** for command options passed as `null`, ensuring non-interactive scripts and unit tests run without blocking.
- Conditional multi-step flows use `.next(...)` for routing (e.g., preset selector → custom value input).

### TableFormatter
- Table layout formatting lives in `TableFormatter.java`, not in command classes.
- Uses Unicode box-drawing characters (`│`, `─`, `┼`) with JLine 3 `AttributedStringBuilder` and `AttributedStyle`.
- Border styling: bright black/grey. High-scoring entries (>8.0): green + bold.

### Asynchronous Status Spinner
- `TerminalSpinner.java` implements `AutoCloseable` — use in try-with-resources blocks.
- Background daemon thread cycles braille characters (`⠋`, `⠙`, `⠹`...) at 80ms intervals.
- Cleans up after itself via carriage returns and erase-line instructions on `close()`.

### Dashboard (Alternate Buffer)
- The `dashboard` command switches to JLine's alternate screen buffer (`\033[?1049h`), hides the cursor (`\033[?25l`), and enters raw character input mode.
- Exit cleanly: restore cursor, return to standard buffer (`\033[?1049l` + `\033[?25h`).

## Coding Expectations

- Keep changes **incremental and backward-compatible**.
- Prefer introducing **new classes** over large rewrites in-place.
- Keep **methods small and composable**.
- Add/adjust tests when introducing significant behavior changes.
- Preserve existing command names unless migration explicitly requires changes.
- Keep **commands focused on presentation**; all business logic lives in services.
- Use **explicit domain models** for complex data structures.
- Handle errors explicitly with **meaningful error messages**.
- Keep **class responsibilities focused** (command / service / repository separation).
- Prefer **constructor injection** and immutable dependencies.

## Out of Scope (unless explicitly asked)

- Python/FastAPI services.
- Frontend/UI generation.
- Microservice decomposition.
- HTTP REST API.
