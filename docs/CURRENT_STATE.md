# Current State and Documentation Precedence

This source inventory is pinned to main commit `6b4ade2371cf3da2009aa06821f6d60c0cee69f7` (2026-11-13 reconciliation). Unmerged pull requests are excluded. Counts below describe checked-in source, not runtime verification.

## Authority and status

[AGENTS.md](../AGENTS.md) records required invariants: Java 25, Spring Boot 3.x, Spring Shell, JPA/Flyway/SQLite, straight arithmetic franchise averaging, permanent/transient error classification, and no frontend, HTTP API, Python service, or microservice split. Explicit task requirements and current invariants take precedence over older plans and speculative designs. Source, tests, build/task configuration, and checked-in workflows establish what is implemented; a gap between source and an invariant is work to track, not permission to change the invariant.

[ARCHITECTURE.md](ARCHITECTURE.md) separates the baseline from targets. [TODO.md](TODO.md) is a roadmap. [REFACTORING.md](REFACTORING.md) is a June audit whose old line numbers and estimates are historical. [implementation_plan.md](implementation_plan.md), [task.md](task.md), and [walkthrough.md](walkthrough.md) preserve execution history; their completed checks are historical reports, not fresh verification. In particular, the old first-degree franchise-rating plan does not override connected-component arithmetic averaging. Provider designs and expansion research are proposals, not approved integrations or verified external API contracts. Vector/database alternatives do not authorize replacing SQLite.

[NEXT.md](../NEXT.md) is a dated execution queue, not a feature inventory or proof of current issue/PR state. [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md) contains workflow proposals and illustrative outputs; [AUTOMATION.md](AUTOMATION.md) describes checked-in workflows. A configured workflow does not prove a passing run or enabled branch protection. The [CLI compatibility policy](CLI_COMPATIBILITY.md) governs future changes without claiming release automation exists.

## Reproducible source inventory

At the pinned baseline:

| Inventory | Count | Evidence / reproduction from repository root |
|---|---:|---|
| Tracked Markdown | 15 | `git ls-files '*.md'` (14 project documents plus the reviewer prompt) |
| Main Java files | 74 | `git ls-files 'src/main/java/*.java'` |
| Test Java files | 17 | `git ls-files 'src/test/java/*.java'` |
| Test methods | 52 | `rg -n '@Test\b' src/test/java` (no parameterized tests at baseline) |
| Application shell commands | 11 | `rg -n '@ShellMethod' src/main/java` (excludes Spring Shell built-ins) |
| Flyway migration files | 7 | `git ls-files 'src/main/resources/db/migration/*.sql'` (does not prove applied status) |
| Entity tables | 5 | `tokens`, `sync_status`, `user_anime_list`, `anime_cache`, `generic_cache` |
| Taskfile tasks | 4 | [Taskfile.yml](../Taskfile.yml): `build`, `run`, `start`, `test` |
| Workflow files | 6 | [workflow directory](../.github/workflows/) |

The 11 commands are `login`, `refresh`, `health`, `list`, `sync`, `recommendation`, `seasonal`, `suggestions`, `dashboard`, `flyway-info`, and `flyway-repair`. CLI examples use the registered camelCase flags (`--userName`, `--numYears`, `--minYear`, `--maxYear`, `--excludeCompleted`, `--groupCandidates`, `--decayMonths`, `--freshnessBoost`), not Java parameter spelling.

## Completed work and remaining gaps

- [TableFormatter](../src/main/java/com/gordonpn/anime/service/recommendation/explanation/TableFormatter.java) and [its tests](../src/test/java/com/gordonpn/anime/service/recommendation/explanation/TableFormatterTests.java) already exist. Historical requests to introduce a formatter are completed; additional output work must identify a concrete gap.
- [Domain exceptions](../src/main/java/com/gordonpn/anime/models/exception/) already exist. `AnimeService` and `UserListService` wrap JSON parsing in `AnimeDataProcessingException`; command JSON formatting does too. This does not prove all exception paths are uniform or input validation is complete.
- Utilities already live in [util](../src/main/java/com/gordonpn/anime/util/), exceptions in `models/exception`, OAuth DTOs in [models/dto](../src/main/java/com/gordonpn/anime/models/dto/), persistence in [entity](../src/main/java/com/gordonpn/anime/entity/), and callback handling in [handler](../src/main/java/com/gordonpn/anime/handler/). Ranking, filtering, explanation, and facade services still share `service/recommendation`; further subpackage moves remain proposals.
- Tests already cover facade (9 methods), ranking (2), filtering (1), explanation (4), formatter (3), recommendation commands (6), list commands (2), health (2), retry interceptor (5), and other services/application paths. Test-file counts cannot establish line or branch coverage; the audit's estimate of 0.5% coverage is withdrawn.
- [MalRetryInterceptor](../src/main/java/com/gordonpn/anime/config/MalRetryInterceptor.java) implements the required classification, but broad retry loops in [AnimeService](../src/main/java/com/gordonpn/anime/service/AnimeService.java) and [UserListService](../src/main/java/com/gordonpn/anime/service/UserListService.java) remain (#20). Expired-cache fallback is conditional on reaching the fallback branch; it is not an unconditional offline guarantee under infinite retries.
- [BackgroundFetchService](../src/main/java/com/gordonpn/anime/service/BackgroundFetchService.java) does not yet skip startup synchronization based on freshness (#19). Shared grouping (#23), seasonal extraction (#24), MAL HTTP abstraction, generic cache service, and configuration consolidation remain pending at this baseline.
- DTO fields for genres/studios/content rating do not prove those fields are requested or cached. [AnimeService](../src/main/java/com/gordonpn/anime/service/AnimeService.java) detail fields are `id,title,recommendations,mean,popularity,related_anime,alternative_titles,start_date`; richer discovery metadata is pending (#39).
- The wrapper is Gradle 8.12.1; application/toolchain Java is 25 and daemon Java is 21. Repository configuration and successful runs must not be described as official Gradle compatibility certification.

## Verification boundary

This documentation change does not rerun Java tests, call live providers, verify credentials, execute the dashboard, validate released binaries, or inspect hosted branch protection. Read-only packaged command help was checked with dummy credentials and disposable storage to verify actual flag spelling. Local file targets and Markdown anchors are checked separately for all tracked Markdown, including the reviewer prompt and new documentation. Remote source links are citations; their availability is not certified by that local check. Documentation CI/lint/link gating remains separate work (#35).
