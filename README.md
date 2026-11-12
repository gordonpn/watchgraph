# Anime Recommendation CLI

A **Java + Spring Boot + Spring Shell** CLI application that generates personalized anime recommendations by aggregating signals from your [MyAnimeList (MAL)](https://myanimelist.net/) completed titles.

The app authenticates via MAL OAuth 2.0 (PKCE), persists local state in **SQLite**, manages schema with **Flyway** migrations, and ranks candidates using a multi-signal scoring engine with temporal decay, freshness boost, and franchise-aware rating averaging.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 25 (Gradle toolchain) |
| Runtime | Spring Boot 3.5.15 |
| Interface | Spring Shell 3.3.3 (CLI) |
| Persistence | Spring Data JPA + SQLite (Hibernate community dialects) |
| Migrations | Flyway |
| HTTP Client | Spring `RestClient` with retry interceptor |
| Auth | MyAnimeList OAuth 2.0 + PKCE |
| Build | Gradle 8.12+ (daemon on JDK 21, toolchain JDK 25) |
| Native Image | GraalVM Native Build Tools (plugin configured) |
| Boilerplate | Lombok 1.18.46 |

## CLI Commands

| Command | Description |
|---------|-------------|
| `login` | Authenticate with MAL via browser OAuth callback |
| `refresh` | Refresh the OAuth access token |
| `health` | Check database connectivity and MAL auth status |
| `list --userName @me` | Fetch completed anime list (local DB or live API) |
| `sync` | Synchronize all user lists from MAL to local SQLite |
| `recommendation --userName @me` | Generate multi-signal personalized recommendations |
| `seasonal --numYears 5` | Top-rated seasonal anime, grouped by franchise |
| `suggestions` | MAL's native suggestion feed |
| `flyway-info` | Show Flyway migration status |
| `flyway-repair` | Repair Flyway migration checksums |

All commands support **interactive prompting** — if you omit flags, the shell will ask for values with sensible defaults.

## Recommendation Engine

The `recommendation` command runs a multi-stage pipeline:

1. **User List Gathering** — Completed and dropped anime are fetched from local SQLite (`@me`) or live MAL API.
2. **Series Grouping** — Completed titles are partitioned into franchises via connected-components BFS over MAL's `related_anime` graph. A representative title is chosen by highest popularity.
3. **Candidate Pooling** — All MAL-suggested similar titles from every show in a franchise are pooled together.
4. **Weighted Occurrence** — Each recommendation occurrence is scaled by:
   - **User rating multiplier** = `avgGroupUserScore / 7.0` (or 1.0 if unrated)
   - **Temporal decay** (optional) = `2^(-t / decayMonths)` based on completion date
5. **Multi-Signal Scoring** — `final_score = w_occ × occurrence + w_rating × franchise_avg_rating + w_popularity × normalized_popularity`
6. **Freshness Boost** (optional) — `e^(-freshnessBoost × yearsSinceRelease)` applied at the candidate level
7. **Year Range Filtering** — Optional `--minYear` / `--maxYear` to constrain release era

Weights are configurable in `application.properties`:
- `recommendation.weights.occurrence` (default 1.0)
- `recommendation.weights.rating` (default 0.5)
- `recommendation.weights.popularity` (default 0.0001)

## Seasonal Recommendations

The `seasonal` command scans past years of MAL seasonal data, builds a franchise graph across all seasonal entries, and consolidates scores into franchise-level rankings. The representative title is the most popular entry in the entire connected component (including bridge nodes not in the seasonal results). Scores use a **straight arithmetic average** across seasons — treating each season as an equal unit of quality, avoiding the bias that would come from weighting by review count (later seasons have fewer but higher ratings due to viewer attrition).

## Data & Persistence

**SQLite** database (`anime.db`) with Flyway-managed schema:

| Table | Purpose |
|-------|---------|
| `tokens` | OAuth access/refresh tokens |
| `sync_status` | Last-synced timestamps per list type |
| `user_anime_list` | Local mirror of user's MAL lists |
| `anime_cache` | Cached anime details with 7-day TTL |
| `generic_cache` | Raw MAL API JSON payloads with configurable TTL |

**Caching strategy:**
- Past-season anime data: 30-day TTL
- Current/future season data: 1-day TTL
- User lists: 1-day TTL
- Anime details: 7-day TTL
- **Fail-safe fallback**: expired cache is returned when the API is unreachable

**API retry**: Configurable exponential backoff via `MalRetryInterceptor`. Permanent errors (401, 403, 404) fail fast; transient errors (429, 5xx, network drops) are retried.

## Prerequisites

- **JDK 25** installed (e.g. `/opt/homebrew/opt/openjdk@25` on macOS)
- **JDK 21** for the Gradle daemon (the Gradle wrapper itself requires an older JDK)
- MAL API credentials (see Configuration below)

## Configuration

Set MAL credentials via environment variables or a `.env` file (loaded automatically at startup):

```sh
MAL_CLIENT_ID=your_client_id
MAL_CLIENT_SECRET=your_client_secret
```

Additional config in `application.properties`:
- `mal.callback.port` — Local OAuth callback port (default 8081)
- `mal.redirect.uri` — OAuth redirect URI
- `mal.retry.max-attempts` — API retry attempts (-1 for infinite, default)
- `mal.retry.initial-backoff-ms` — Initial retry delay (default 500)
- `mal.retry.backoff-multiplier` — Backoff multiplier (default 2.0)
- `recommendation.weights.*` — Ranking signal weights

> **Security**: Never commit real credentials. The `.env` file is gitignored.

## Run

**Build and run:**
```sh
./gradlew clean bootJar && java -jar build/libs/anime-0.0.1-SNAPSHOT.jar
```

**Dev run (no JAR packaging):**
```sh
./gradlew bootRun
```

**Via Taskfile:**
```sh
task start    # build + run
task test     # run tests
```

## Testing

```sh
./gradlew test
```

Tests use JUnit 5 + Spring Boot Test + Spring Shell Test. Test coverage is actively being improved — current focus areas include the recommendation facade, ranking service, and filtering service.

## Project Structure

```
src/main/java/com/gordonpn/anime/
├── commands/                     # Spring Shell CLI entry points
├── service/                      # Business logic services
│   └── recommendation/           # Recommendation engine sub-services
│       ├── RecommendationFacade  # Orchestration
│       ├── RankingService        # Multi-signal scoring
│       ├── FilteringService      # Inclusion/exclusion rules
│       └── ExplanationService    # Human-readable reason text
├── config/                       # Spring beans, RestClient, interceptors
├── entity/                       # JPA entities
├── repository/                   # Spring Data JPA repositories
├── models/                       # MAL API DTOs, domain models
│   ├── dto/                      # Internal DTOs
│   └── exception/                # Domain exceptions
├── handler/                      # OAuth2 callback HTTP handler
└── util/                         # PKCE, browser, random state utilities
```

## Documentation

| File | Purpose |
|------|---------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Target architecture and refactoring roadmap |
| [REFACTORING.md](REFACTORING.md) | Detailed code quality audit and prioritized fixes |
| [AGENTS.md](AGENTS.md) | AI agent instructions, design decisions, implementation patterns |
| [TODO.md](TODO.md) | Phased task list for implementation |
| [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md) | CI/CD workflow recommendations |
| [docs/](docs/) | Implementation plans and walkthroughs |

## Project Status

Active development — the CLI interface is functional and the recommendation engine is being refined. Current priorities:

- Decomposing the `RecommendationFacade` into smaller focused services
- Adding test coverage for core recommendation logic
- Consolidating configuration management
- Creating a `MalApiClient` HTTP abstraction layer

Contributions should follow the Java-first architecture in [ARCHITECTURE.md](ARCHITECTURE.md). No HTTP API, no frontend, no Python splits — this is a CLI-only project.
