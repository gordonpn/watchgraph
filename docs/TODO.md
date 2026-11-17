# TODO

> **Status:** Roadmap, not approval of every design or proof of runtime behavior. See [current state and precedence](CURRENT_STATE.md); mapped DTO fields do not imply fetched metadata. Preserve SQLite and straight arithmetic franchise averaging until an explicit approved task changes an invariant.

A rough task list for the project, suitable for human or agent-driven implementation.

Scope: Java Spring CLI-focused application.

> **Status key:** ✅ Done &nbsp;&nbsp; 🔨 In Progress &nbsp;&nbsp; 💡 Planned

## Phase 0: Baseline cleanup

- [x] Move MAL credentials to environment-based config. ✅
- [x] Rotate any previously committed MAL credentials. ✅
- [x] Verify app starts cleanly with local profile and SQLite. ✅

## Phase 1: Service refactor

- [x] Introduce `RecommendationFacade` for orchestration. ✅
- [x] Extract ranking policy into `RankingService`. ✅
- [x] Extract inclusion/exclusion rules into `FilteringService`. ✅
- [x] Add `ExplanationService` for recommendation reason text. ✅
- [x] Externalize score weights into application properties. ✅

## Phase 2: Data quality and sync improvements

- [x] Upsert strategy for `user_anime_list` to avoid duplicates. ✅ (UNIQUE constraint + save)
- [x] Fetch and persist richer anime metadata cache. ✅ (`anime_cache` table)
- [x] Add cache invalidation/TTL policy for external API responses. ✅ (`generic_cache` with TTL)
- [ ] Check sync status at startup and skip unnecessary fetches.

## Phase 3: Reliability, testing, and delivery

- [x] Add command structured logging. ✅ (redirected to file at debug level)
- [x] Add exception handling and better error messages. ✅ (domain exceptions, fail-fast strategy)
- [ ] Add service-level tests for recommendation and filtering logic. 🔨 (partial — facade, ranking, filtering tests exist)
- [ ] Add command-level smoke tests for critical CLI paths. 🔨 (partial — health, list, recommendation command tests exist)

## Phase 4: Optional advanced relevance

- [x] Add popularity/rating signal blending in final score. ✅ (`RankingService` with configurable weights)
- [x] Add optional metadata-driven filters (year). ✅ (`--minYear` / `--maxYear`)
- [x] Add temporal decay and freshness boost signals. ✅ (`--decayMonths`, `--freshnessBoost`)
- [ ] Add genre, studio, media type, and content rating filters. 💡
- [ ] Evaluate Java-native vector search path (Lucene/OpenSearch/pgvector). 💡
- [ ] Keep CLI behavior stable while improving ranking internals.

## Phase 5: Multi-source anime lookup (AniList cross-referencing)

- [ ] Integrate AniList GraphQL client.
- [ ] Add `anime_id_mapping` table for MyAnimeList ↔ AniList ID cross-reference.
- [ ] Implement anime search via AniList (fallback to MyAnimeList).
- [ ] Add cross-reference caching and TTL management.
- [ ] Add CLI command for multi-source anime search.

## Phase 6: Vector search and semantic similarity (vec2vec)

- [ ] Evaluate Java-native vector search libraries (Lucene KNN, OpenSearch, pgvector).
- [ ] Design embedding pipeline (anime metadata → vectors).
- [ ] Prototype vector similarity ranking as optional scoring signal.
- [ ] Add configuration flag to enable/disable vector scoring.
- [ ] Measure quality improvements vs. occurrence-based ranking.
- [ ] Maintain backward compatibility with existing CLI behavior.

## Phase 7: Quick-Win CLI Commands (leveraging already-fetched data) 🆕

These features use MAL API fields already mapped in `AnimeDetails` but never surfaced to the user.

- [ ] `anime-search` — Search for anime by title via MAL `/anime?q=` endpoint (`SearchCommands` + `AnimeService.searchAnime()`).
- [ ] `anime-detail <id>` — Print full `AnimeDetails` (synopsis, studios, genres, episodes, source) for a given anime ID.
- [ ] `analytics` — Mine local SQLite for user stats: favorite genres, studios, score histogram, total watch time, drop patterns (`AnalyticsCommands` + `AnalyticsService`).
- [ ] `--genres` / `--exclude-genres` filter on `recommendation` command.
- [ ] `--type` (tv/movie/ova) / `--max-episodes` filter on `recommendation` command.
- [ ] `--studio` filter on `recommendation` command.
- [ ] `--rating` / `--exclude-rating` filter on `recommendation` command (pg13, r, r+).
- [ ] `--min-agreement` filter on `recommendation` command (niche vs. mainstream tuner using `numRecommendations`).

## Phase 8: Recommendation Quality Refinements 🆕

- [ ] Bayesian Average Rating — Replace raw `mean` with Bayesian shrinkage using `numScoringUsers` (already mapped, unused).
- [ ] `--seed` mode — Generate recommendations starting from a single anime instead of full completed list.
- [ ] `--diversity-factor` — Post-ranking diversity enforcement across studios, genres, years.
- [ ] `optimize-watchlist` — Rank the user's "plan to watch" list by recommendation compatibility score.
- [ ] `recommendation-detail <id>` — Deep-dive on why a specific show was recommended (signal decomposition).

## Phase 9: Probabilistic & Advanced Models 🆕

- [ ] Naive Bayes Preference Classifier — `P(Like | genres, studios, format)` from completed vs. dropped history.
- [ ] Drop Risk Scoring — Identify attributes correlated with user's drops; flag risky recommendations.
- [ ] Cold-Start Mode — Blend global rankings when user has <5 completed shows.
- [ ] `watch-order <franchise>` — Topological sort of franchise graph for complex series (Fate, Monogatari, Gundam).
- [ ] Genre Blend Recommendations — Prioritize shows combining 2+ of the user's top genres in novel ways.
- [ ] Reverse Recommendation — Given a target show, find completed shows that would lead to it.

## Phase 10: External Integrations (stretch) 🆕

- [ ] Streaming Availability — Cross-reference recommendations with Crunchyroll/Netflix/HIDIVE catalogs (requires 3rd-party dataset).
- [ ] Manga Recommendations — Extend pipeline to MAL's manga API.
- [ ] Airing Notifications — Poll MAL for new episodes of followed seasonal shows.
