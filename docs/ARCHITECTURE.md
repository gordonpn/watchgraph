# Architecture (Java Spring CLI)

This document separates the current baseline from proposed targets. Read [current state and precedence](CURRENT_STATE.md) and the [CLI compatibility policy](CLI_COMPATIBILITY.md); targets do not authorize changes to current invariants.
The application is focused on a **CLI-first** experience using Spring Shell.

## 1) Goals

- Stay in the **Java + Spring** ecosystem.
- Keep CLI workflows as the primary interface.
- Keep recommendation logic explicit and easy to tune.
- Avoid rewrites: migrate incrementally from current code.

## 2) Current Baseline (what already exists)

- Spring Boot app with Spring Shell commands (11 application CLI commands, including `dashboard`).
- MAL OAuth 2.0 PKCE login + token refresh + token persistence.
- SQLite + Flyway migrations (7 migrations) + JPA repositories (5 tables).
- Scheduled background list synchronization with upsert + stale-item cleanup.
- Multi-signal recommendation engine:
  - Series grouping via connected-components BFS over related_anime graph.
  - User rating scaling (avgGroupScore / 7.0).
  - Temporal decay (completion date half-life).
  - Freshness boost (release date decay).
  - Franchise-wide rating averaging for candidates.
  - Year range filtering.
- Generic API response caching with TTL + fail-safe fallback to expired cache.
- Retry interceptor with exponential backoff (configurable).
- Interactive prompts for nullable options on recommendation, seasonal, suggestions, and dashboard.
- Structured logging redirected to file.
- Domain exceptions (MalResourceNotFoundException, AnimeDataProcessingException).

## 3) Target Logical Architecture

## 3.1 Layers

1. **Interface layer**
   - CLI commands (`commands/`)

2. **Application layer**
   - Orchestration services (`service/`)
   - Use-case-focused services (search, recommendations, sync)

3. **Domain layer**
   - Recommendation ranking + filtering logic
   - Domain models

4. **Infrastructure layer**
   - MAL API clients (`RestClient` based)
   - Persistence (JPA repositories, SQLite)
   - Scheduling and background jobs

## 3.2 Design rule

No business logic lives directly in command classes.
Services handle all domain logic and are independently testable.

## 4) Package Direction (target)

```text
com.gordonpn.anime
├── commands/                  # Spring Shell entry points
│   ├── AuthenticationCommands
│   ├── ListCommands
│   ├── RecommendationCommands
│   ├── HealthCommands
│   ├── FlywayCommands
│   ├── SearchCommands        # Planned: anime-search
│   ├── DetailCommands         # Planned: anime-detail, recommendation-detail
│   └── AnalyticsCommands      # Planned: analytics, optimize-watchlist
├── service/                   # Orchestration services
│   ├── recommendation/
│   │   ├── RecommendationFacade
│   │   ├── RankingService
│   │   ├── FilteringService
│   │   ├── ExplanationService
│   │   ├── GraphBuilder       # Planned: extract from facade
│   │   ├── ComponentAnalyzer  # Planned: extract from facade
│   │   └── FranchiseRepSelector # Planned: extract from facade
│   ├── analytics/             # Planned: AnalyticsService, WatchlistOptimizer
│   ├── classifier/            # Planned: NaiveBayesClassifier, DropRiskAnalyzer
│   ├── auth/
│   ├── sync/
│   └── list/
├── client/                    # MAL and external API clients
│   ├── MalApiClient           # Planned: RestClient abstraction
│   └── AniListClient          # Planned: GraphQL client
├── repository/                # JPA repositories
├── entity/                    # JPA entities
├── models/                    # Domain models, DTOs, exceptions
├── config/
└── handler/
```

Notes:
- Keep existing package names during migration; move only when useful.
- CLI-focused structure, no controller layer needed.
- Planned services in `analytics/` and `classifier/` represent future capability areas.
- `MalApiClient` abstraction is the next infrastructure priority (see REFACTORING.md).

## 5) Recommendation Strategy

Current implementation (Phase 2 scoring):

1. **User List Gathering** — Completed and dropped anime from local SQLite (`@me`) or live MAL API.
2. **Series Grouping** — Connected-components BFS over `related_anime` graph partitions franchises. Representative title = highest popularity entry in component.
3. **Candidate Pooling** — All MAL-suggested similar titles from all shows in a franchise are pooled.
4. **Weighted Occurrence** — Each rec occurrence scaled by user rating multiplier (avgGroupScore / 7.0) and optional temporal decay (2^(-t / decayMonths)).
5. **Multi-Signal Scoring** — `final_score = w_occ × occurrence + w_rating × franchise_avg_rating + w_popularity × normalized_popularity`
6. **Freshness Boost** (optional) — `exp(-freshnessBoost × yearsSinceRelease)` at candidate level.
7. **Year Range Filtering** — `--minYear` / `--maxYear` constrain release era.

Weights are externalized in properties:

- `recommendation.weights.occurrence` (default 1.0)
- `recommendation.weights.rating` (default 0.5)
- `recommendation.weights.popularity` (default 0.0001)

Target scoring extensions (Phase 8+):

- Bayesian Average Rating — Replace raw `mean` with Bayesian shrinkage using `numScoringUsers`.
- Naive Bayes Preference Classifier — `P(Like | genres, studios, format)` from user history.
- Drop Risk Scoring — Discount recommendations matching known drop patterns.
- Diversity Enforcement — Post-ranking reordering for genre/studio variety.
- Genre Blend — Prioritize shows combining 2+ of the user's top genres.

Future signals (Phase 6):

- Vector similarity (Lucene KNN) as optional scoring signal from embedded synopses.

## 6) Data & Persistence

SQLite + Flyway (7 migration files; applied state depends on the local database).

Existing tables:
- `tokens` — OAuth access/refresh tokens (V1)
- `sync_status` — Last-synced timestamps per list type (V2)
- `user_anime_list` — Local mirror of user's MAL lists, UNIQUE on anime_id (V3)
- `anime_cache` — Cached MAL anime details JSON, 7-day TTL (V3)
- `generic_cache` — Raw MAL API JSON payloads with configurable TTL (V4)

Planned additions:
- `anime_id_mapping` — Cross-reference table mapping MAL IDs ↔ AniList IDs (Phase 5)
- `recommendation_snapshot` — Optional offline debugging/audit of recommendation runs

## 7) Operational Requirements

- All secrets from environment variables or profile-specific external config.
- Never commit real API credentials to source control.
- Structured logs for command execution.
- Graceful shutdown for scheduler and datasource.

## 8) Implementation Checklist

### Phase A — Service Refactor ✅ (COMPLETE)

- [x] Introduce `RecommendationFacade` for orchestration.
- [x] Extract scoring into `RankingService`.
- [x] Extract filtering into `FilteringService`.
- [x] Add explanation payload from `ExplanationService`.

Acceptance criteria:
- ✅ Recommendation logic is testable without shell context.
- ✅ Weight tuning can be done by config changes.

### Phase B — Reliability & security hardening ✅ (MOSTLY COMPLETE)

- [x] Move MAL credentials to environment variables.
- [x] Rotate compromised/revealed credentials.
- [x] Add command structured logging (redirected to file).
- [x] Add unit tests for major services and key CLI command paths (partial — coverage being expanded).

Acceptance criteria:
- ✅ No secrets in tracked files.
- 🔨 Build + tests pass (more test coverage needed).

### Phase C — Optional advanced relevance 🔨 (PARTIALLY COMPLETE)

- [x] Add local cached anime metadata table/service (`anime_cache` + `generic_cache`).
- [x] Add hybrid ranking signals (rating/popularity/temporal-decay/freshness-boost).
- [ ] Optional: evaluate Java-native vector search path (OpenSearch/pgvector/Lucene KNN).

Acceptance criteria:
- Ranking signals are implemented; measurable quality improvement has not been established by this source inventory.
- Preserve CLI compatibility according to the documented policy; this is a requirement, not a fresh compatibility test result.

### Phase D — Decomposition & Refactoring 💡

- [ ] Extract `AnimeGraphBuilder` from `RecommendationFacade` (BFS graph construction).
- [ ] Extract `ComponentAnalyzer` from `RecommendationFacade` (connected components).
- [ ] Extract `FranchiseRepSelector` from `RecommendationFacade` (representative title selection).
- [ ] Create `MalApiClient` abstraction over RestClient (see REFACTORING.md Issue #14).
- [ ] Create `CacheService` with generic `getOrFetch()` (see REFACTORING.md Issue #2).
- [x] Create `TableFormatter` service for CLI output consistency (`service/recommendation/explanation`).
- [ ] Consolidate configuration into `@ConfigurationProperties` classes.

### Phase E — Quick-Win CLI Commands 💡 (see TODO.md Phase 7)

- [ ] `anime-search` — Title search via MAL `/anime?q=` endpoint.
- [ ] `anime-detail <id>` — Full detail view for a given anime ID.
- [ ] `analytics` — User statistics from local SQLite.
- [ ] `--genres`, `--type`, `--studio`, `--rating` filters on `recommendation`.
- [ ] `--min-agreement` filter on `recommendation`.

### Phase F — Ranking Quality Refinements 💡 (see TODO.md Phase 8)

- [ ] Bayesian Average Rating (using `numScoringUsers`).
- [ ] `--seed` mode for single-anime recommendations.
- [ ] `--diversity-factor` post-ranking enforcement.
- [ ] `optimize-watchlist` command.
- [ ] `recommendation-detail <id>` for signal decomposition.

### Phase G — Probabilistic Models 💡 (see TODO.md Phase 9)

- [ ] Naive Bayes Preference Classifier.
- [ ] Drop Risk Scoring.
- [ ] Cold-Start Mode.
- [ ] `watch-order <franchise>` command.
- [ ] Reverse Recommendation.

## 9) Non-Goals

- No HTTP API.
- No Python service split.
- No frontend in this repository.
- No microservice decomposition.

## 10) Multi-Source Anime Lookup (AniList + MyAnimeList Cross-Referencing)

Proposed, not implemented: cross-referencing anime across multiple databases. Provider/query/mapping designs remain subject to contract validation and explicit implementation scope:

- **MyAnimeList (primary)**: Used for user authentication, list management, and primary recommendation source.
- **AniList (secondary)**: Provides GraphQL-based queries for supplementary anime metadata and ID cross-referencing.

### Strategy

1. User searches for an anime by title (via CLI).
2. First lookup in AniList (GraphQL, typically faster for large datasets).
3. Use AniList ID to find corresponding MyAnimeList ID via `anime_id_mapping` cache.
4. Retrieve full recommendation chain from MyAnimeList.

This dual-source approach improves search accuracy and availability (fallback to alternate source if one API is unavailable).

## 11) Advanced: Vector Search (vec2vec) for Similarity Ranking

**Phase C+ exploration:** Evaluate embedding-based recommendation refinement:

- **Java-native options**:
  - **Lucene KNN**: Built-in approximate nearest neighbor search.
  - **OpenSearch**: Distributed vector search engine.
  - **pgvector**: PostgreSQL vector extension (if migrating from SQLite).

- **Use case**: Instead of purely occurrence-based ranking, embed anime descriptions/metadata and use vector similarity to identify recommendations that are semantically similar to user preferences, not just directly recommended.

- **Gradual rollout**: Introduce as optional scoring signal (remains backward-compatible with current occurrence-based ranking).

## 12) Migration Notes

- Keep changes incremental and backward-compatible.
- Prefer adding new CLI commands over rewriting existing ones.
- Use feature flags/properties for behavior switches where practical.
