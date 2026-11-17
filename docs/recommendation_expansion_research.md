# Recommendation Engine Expansion & Research Plan

> **Status:** Research and feature proposals, except explicitly implemented baseline behavior confirmed in [current state](CURRENT_STATE.md). Estimates and external API assumptions are not implementation evidence. Bayesian weighting, new providers, vector stores, and alternative UI libraries do not override current invariants or authorize architecture changes.

This document serves as a reference for expanding the anime recommendation engine's capabilities. It contains brainstorming details for probabilistic ranking models, CLI app expansions, and MyAnimeList API data integration opportunities.

> **Status key:** ✅ Implemented &nbsp;&nbsp; 🔨 In Progress &nbsp;&nbsp; 💡 Planned &nbsp;&nbsp; 🆕 New Idea

---

## 1. Probabilistic Concepts for Recommendations

Applying statistics and probability can shift the recommendation logic from a basic heuristic calculator to a predictive model. Below are four key concepts that can be introduced:

### A. Bayesian Average for Candidate Ratings (Bayesian Shrinkage)
*   **The Problem:** Candidate ratings on MyAnimeList can be highly volatile for obscure shows (e.g., a show rated `10.0` by only 1 voter will outrank a classic rated `9.0` by 100,000 voters).
*   **The Probabilistic Formula:**
    $$R_{\text{Bayes}} = \frac{v \cdot R + c \cdot C}{v + c}$$
    *   $v$: The number of users who rated the show (`num_scoring_users` from MAL API)
    *   $R$: The raw average rating of the show (`mean` from MAL API)
    *   $c$: A smoothing constant representing the threshold number of votes (e.g., the average vote count across the database)
    *   $C$: The global average rating across all anime in the database
*   **Educational Value:** Demonstrates how to use a *prior probability distribution* (the global mean) to correct for small sample sizes.

### B. Naive Bayes Preference Classifier
*   **The Problem:** We currently suggest similar items, but we do not predict how likely a user is to enjoy a candidate show based on its attributes.
*   **The Probabilistic Formula:** Using Bayes' theorem to calculate the probability that the user likes a candidate show given its attributes (e.g., genres, studios):
    $$P(\text{Like} \mid \text{Attributes}) \propto P(\text{Like}) \cdot \prod_{a \in \text{Attributes}} P(\text{Attribute}_a \mid \text{Like})$$
    *   Calculate $P(\text{Genre}_g \mid \text{Like})$ using the proportion of completed shows in that genre.
    *   Calculate $P(\text{Genre}_g \mid \text{Dislike})$ using the proportion of dropped/low-rated shows in that genre.
*   **Educational Value:** Illustrates joint probabilities, independence assumptions, and classic classification algorithms.

### C. Joint Occurrence Probability (Collaborative Filtering)
*   **The Problem:** We treat all recommendation signals with equal weight prior to rating scaling.
*   **The Probabilistic Formula:** Calculate the conditional probability $P(\text{Candidate} \mid \text{Completed})$ based on global co-occurrence:
    $$P(\text{Show B} \mid \text{Show A}) = \frac{\text{Users who completed both } A \text{ and } B}{\text{Users who completed } A}$$
*   **Educational Value:** Explains joint events, conditional probabilities, and recommendation filtering matrices.

### D. Expected Utility and Drop Risk Modeling
*   **The Problem:** Recommended shows might match the user's high-level taste but belong to a genre/format that the user frequently drops.
*   **The Probabilistic Formula:** Treat the recommendation score as the *expected utility* of starting a show, discounted by the probability of dropping it:
    $$\text{Expected Score} = \text{Raw Score} \times (1 - P(\text{Drop} \mid \text{Media Type, Episode Count}))$$
*   **Educational Value:** Explains Decision Theory and expected value calculations under uncertainty.

---

## 2. CLI Application Feature Expansions

Here are several specific ways we can expand the command-line interface itself to increase usability and recommendation control:

1. **Niche vs. Mainstream Tuner (`--min-similarity`):**
   * **Concept:** MAL's recommendations contain an agreement count of how many users voted for the link. Filter out weak connections by specifying a minimum agreement threshold.
   * **CLI Command:** `recommendation --min-similarity 10` (strict, high-consensus classics) or `recommendation --min-similarity 1` (adventurous, niche/rare suggestions).
2. **Targeted Search (`--seed`):**
   * **Concept:** Generate recommendations starting from a single specific show (or franchise component) rather than scanning the entire completed watchlist.
   * **CLI Command:** `recommendation --seed "Steins;Gate"` or `recommendation --seed 9253` (ideal for finding similar shows right after finishing one).
3. **Advanced Genre & Studio Filters (`--genres` / `--exclude-genres`):**
   * **Concept:** Filter the candidate recommendations list by genre tags or animation studios.
   * **CLI Command:** `recommendation --genres Action,Sci-Fi --exclude-genres Romance`
4. **Format & Length Constraints (`--type` / `--max-episodes`):**
   * **Concept:** Limit recommendations based on the runtime format (e.g. movies only) or maximum series duration.
   * **CLI Command:** `recommendation --type movie` or `recommendation --max-episodes 13` (short-form shows).
5. **Release Era Selector (`--minYear` / `--maxYear`):** ✅
   * **Concept:** Filter suggestions by release decade to target retro/classic anime or focus only on recent releases.
   * **CLI Command:** `recommendation --minYear 2015 --maxYear 2025`
   * **Status:** Implemented in `RecommendationCommands` (Shell options) and `RecommendationFacade` lines 288–300. Candidates with unknown start years are skipped when a year filter is active.
6. **Interactive Detail Explainer (`recommendation-detail <id>`):**
   * **Concept:** A separate command that displays details for a recommended show (synopsis, studio, MAL score) and prints the specific user recommendation comments showing why it matches your watched history.
   * **CLI Command:** `recommendation-detail 30015`
7. **User List Analytics (`analytics` Command):**
   * **Concept:** Analyze the local SQLite database to print user statistics: favorite studios, favorite genres, score distribution histograms, and total watch time.
8. **Pagination and Interactive TUI (Text User Interface):**
   * **Concept:** Use a terminal interface library (like Lanterna or JLine widgets) to support interactive scrolling and collapsible explanation panels.

---

## 3. High-Value MyAnimeList API Data Opportunities

Looking at the MyAnimeList API v2 specs, we can leverage additional fields in our requests to enrich the SQLite cache (`anime_cache`) and improve recommendations:

| Field Name | Type | Recommended Use in App |
| :--- | :--- | :--- |
| `num_scoring_users` | Integer | Used as the vote count ($v$) to calculate the **Bayesian Average** rating. |
| `genres` | Array | Enables **Naive Bayes** taste classification and CLI genre filtering. |
| `studios` | Array | Adds studio preferences to recommendation signals and user list analytics. |
| `source` | String | Identifies source material (manga, light novel, original) to align recommendations with adaptation preferences. |
| `media_type` | String | Identifies format (tv, ova, movie, special, ona, music) to support format filtering. |
| `num_episodes` | Integer | Enables filtering recommendations by show length (e.g. short-form series vs long running shonen). |
| `start_date` / `end_date` | String | Identifies release eras, enabling decade/era-specific recommendations. |
| `rating` | String | Content rating (e.g., pg13, r, r+). Enables filtering out or highlighting mature titles. |
| `average_episode_duration`| Integer | Computes precise watch times in the `analytics` command. |
| `statistics` | Object | Detailed completion statuses globally (completed, planning, dropped count) to compute popularity trends. |

---

## 4. Additional Feature Ideas

### A. Temporal Recommendation Decay (Completion Date) ✅
* **Concept:** Recent shows in the user's completed list should influence recommendations more heavily than older shows, since taste evolves over time.
* **Focus:** Weighs by **when the user watched** a show, not when it was released.
* **Motivation:** User taste shifts over time; shows watched recently reflect current preferences better than shows watched years ago.
* **Implementation:** Assign decay weights based on completion date; older shows have less influence on the recommendation signal.
* **Formula:** `Signal Weight = Base Signal × 2^(-t / decayMonths)` where t is elapsed months since completion.
* **CLI Command:** `recommendation --decayMonths 12` (half-life of 12 months).
* **Status:** Implemented in `RecommendationFacade` lines 249–259. Completion dates sourced from `listStatus.updatedAt` (live API) or `UserAnimeListEntity.updatedAt` (local DB).
* **Educational Value:** Time-series weighting, exponential decay modeling, and preference evolution.

### A.1. Recency Weighting for Release Dates (Freshness Boost) ✅
* **Concept:** Recently **released** shows should rank higher in recommendations, independent of when the user watched related content.
* **Focus:** Weighs by **when a candidate show was released**, not when the user watched something.
* **Motivation:** Newer anime often have better production quality, reflect current cultural trends, and allow participation in real-time fandom discussions.
* **Implementation:** Boost the score of candidate recommendations based on their release date; older shows gradually decrease in relative ranking.
* **Formula:** `Release Boost = exp(-μ × yearsSinceRelease)` where μ is the freshness boost parameter and yearsSinceRelease = max(0, currentYear - startYear).
* **CLI Command:** `recommendation --freshnessBoost 0.5` (moderate boost for newer releases) or `--freshnessBoost 0` (disable, recommend by relevance only).
* **Status:** Implemented in `RecommendationFacade` lines 308–314. Applied at the candidate level after base score calculation.
* **Example:** Two equally-relevant shows, one from 2024 and one from 2015, will rank the 2024 show higher when freshness boost is enabled.
* **Educational Value:** Balancing relevance vs. discovery, temporal popularity signals, and content aging in ranking systems.

### A.2. Hybrid Temporal Weighting ✅
* **Concept:** Combine completion decay and release freshness to model both user taste evolution and content novelty.
* **Combined Formula:** 
  $$\text{Final Score} = \text{Base Relevance Score} \times \underbrace{2^{-t / \text{decayMonths}}}_{\text{Taste Decay}} \times \underbrace{\exp(-\mu \times \text{yearsSinceRelease})}_{\text{Freshness Boost}}$$
* **Status:** Implemented. Taste decay is applied at the occurrence signal level (facade lines 249–259); freshness boost is applied at the final candidate score level (facade lines 308–314). Both parameters default to null (disabled) and prompt interactively when omitted.
* **Behavior:** Recommendations will favor shows that are (1) recently completed by the user and (2) recently released, with independent control over each factor.

### B. Diversity Threshold
* **Concept:** Recommendations can become homogeneous if heavily weighted toward a single franchise or studio. Enforce variety constraints.
* **Implementation:** After ranking, apply diversity filters to ensure the top N results span different studios, genres, or release years.
* **CLI Command:** `recommendation --limit 20 --diversity-factor 0.7` (balance relevance with variety).

### C. User Preference Confidence Estimation
* **Concept:** Distinguish between "confirmed preferences" (genres/studios the user consistently completes) and "experimental tastes" (shows watched once or dropped).
* **Implementation:** Use completion ratio and score frequency to weight preference signals; high confidence = more influence.
* **Metrics:**
  - Preference stability score (variance in ratings for a given genre)
  - Genre affinity ranking (what the user actually completes vs. what they plan to watch)

### D. Reverse Recommendation (What Will Lead to Show X?)
* **Concept:** Given a show the user hasn't seen yet, recommend shows from their completed list that would naturally lead to that target.
* **CLI Command:** `reverse-recommendation --target "Steins;Gate 0"`
* **Implementation:** Use MAL's recommendation graph in reverse; find shows the user has completed that are frequently recommended toward the target.

### E. Drop Pattern Analysis & Early Warning
* **Concept:** Identify attributes (episode count, genre combinations, pacing) that correlate with the user's drops.
* **Implementation:** Analyze drop rate by genre, studio, source material, and runtime. Flag recommendations that match previous drop patterns.
* **CLI Command:** `recommendation --avoid-drop-risks` (filter out shows matching known drop patterns).
* **Output:** Include a "drop risk" score alongside each recommendation.

### F. Seasonal Recommendation Streak Tracking
* **Concept:** Users often watch seasonal releases in real-time. Track and suggest shows continuing from the current or upcoming season.
* **Implementation:** Cache seasonal anime lists; detect which current-season shows the user has started, and recommend continuations.
* **CLI Command:** `seasonal-recommendations --current` (recommend ongoing shows for the current season).

### G. Studio Discovery Mode
* **Concept:** Recommend all shows from a favorite studio that the user hasn't watched yet, ranked by MAL score.
* **Implementation:** Extract user's top studios from completed list, then fetch all anime by those studios and filter unwatched.
* **CLI Command:** `studio-recommendations --studio "Shaft"` or `studio-recommendations --auto-detect` (suggest from top 5 studios).

### H. Recommendation Similarity Scoring
* **Concept:** Show why two shows are linked (i.e., which attributes/relations they share: same franchise, shared tags, recommended together, shared voice actors).
* **Implementation:** Trace the recommendation path and surface shared attributes.
* **CLI Output:** `recommendation-detail 30015` could display: "Similar because: Franchise relation (Demon Slayer) + Action genre + Same voice actor (Tanjiro seiyuu)".

### I. Cold-Start Recommendations (New User Handling)
* **Concept:** For users with very few completed shows, leverage global popularity and genre-based signals instead of relying on sparse recommendation graphs.
* **Implementation:** Blend user-specific signals with global MAL rankings when a user has <5 completed shows.
* **Metrics:** Global MAL score, trending status (from MAL statistics), genre popularity within user's preferred genres.

### J. Recommendation Confidence & Uncertainty Quantification
* **Concept:** Not all recommendations are equally confident. Express recommendation strength as a confidence interval or percentile.
* **Implementation:** Track recommendation signal redundancy (how many independent sources voted for it) and use that to estimate confidence.
* **Output:** Show top 10 with confidence bands: `Vinland Saga (89% ± 5%)` vs. `Niche Gem (62% ± 18%)`.

### K. Watchlist Optimization (Smart Ordering)
* **Concept:** Given the user's planning list, reorder it based on recommendation signals and user preference compatibility.
* **Implementation:** Fetch the user's "Planning" list, rank by compatibility score, and suggest a watch order.
* **CLI Command:** `optimize-watchlist --list-status planning` (reorder the planning list by recommendation fit).

### L. Genre Blend Recommendations
* **Concept:** Recommend shows that blend multiple genres the user enjoys, rather than shows in a single familiar genre.
* **Implementation:** Identify high-scoring shows that combine 2+ of the user's top genres in novel ways.
* **CLI Command:** `recommendation --blend-genres` (prioritize cross-genre shows).

### M. Content Warning & Spoiler Flagging 💡
* **Concept:** Integrate content metadata (trigger warnings, spoiler indicators) to help users make informed choices.
* **Implementation:** Use MAL's content rating and themes; flag shows with mature/dark themes.
* **CLI Output:** Include optional flags in recommendation details: `[SPOILER: Ends a major arc]`, `[CONTENT: Gore/Violence]`.

### N. Content Rating Filter (`--rating` / `--exclude-rating`) 🆕
* **Concept:** Filter recommendations by MAL content rating (pg13, r, r+) to match user comfort level.
* **Why:** `AnimeDetails.rating` is already deserialized from the MAL API but never consumed by the recommendation pipeline. A simple conditional skip in the candidate loop is all that's needed.
* **CLI Command:** `recommendation --rating pg13` (safe-for-work only) or `recommendation --exclude-rating r+` (skip mature content).
* **Effort:** ~1 hour. Same pattern as the existing `--minYear`/`--maxYear` filters in `RecommendationFacade`.

### O. Studio Filter (`--studio`) 🆕
* **Concept:** Filter recommendations to shows from a specific animation studio.
* **Why:** `AnimeDetails.studios` (a `List<Studio>`) is fully mapped but unused. Users often develop studio loyalty (e.g., Kyoto Animation, Shaft, MAPPA, Ufotable).
* **CLI Command:** `recommendation --studio "Kyoto Animation"` or `recommendation --studio Shaft`
* **Effort:** ~1 hour. Add a conditional check in the candidate loop comparing `details.getStudios()` against the filter value.

### P. Anime Search Command (`anime-search`) 🆕
* **Concept:** Search for anime by title using the MAL API. Currently there is no way to discover anime IDs without leaving the CLI.
* **Why:** The MAL API has a `/anime?q=<query>` endpoint. `AnimeService` already has the RestClient, retry logic, and JSON parsing infrastructure. This is a new `SearchCommands` Shell component + a `searchAnime()` method on `AnimeService`.
* **CLI Command:** `anime-search "Steins;Gate"`, `anime-search --type tv "Attack on Titan"`, `anime-search --limit 10 "Monogatari"`
* **Effort:** ~3 hours. New method + new command class + tests.

### Q. Anime Detail Command (`anime-detail <id>`) 🆕
* **Concept:** Print the full `AnimeDetails` for a specific anime ID in a readable format.
* **Why:** `AnimeDetails` maps 30+ fields (synopsis, studios, genres, episodes, source material, related anime, etc.) but there's no command to view them. The data is already cached in `anime_cache`. This is purely a formatting exercise.
* **CLI Command:** `anime-detail 9253`, `anime-detail --json 9253`
* **Effort:** ~2 hours. New command + table formatter. No new API calls for cached entries.

### R. Watch Order Guide (`watch-order <franchise>`) 🆕
* **Concept:** For series with complex chronologies (Fate, Monogatari, Gundam, Fate/), generate a watch order by topologically sorting the franchise graph by relation type.
* **Why:** The facade's BFS connected-components algorithm already traverses MAL's `related_anime` graph. A `WatchOrderService` would sort the component by relation type (prequel → sequel → parent_story → alternative_setting) rather than just grouping.
* **CLI Command:** `watch-order "Monogatari Series"`, `watch-order --id 9253`
* **Effort:** ~3 days. New service + new command. Leverages existing `AnimeService.getAnimeById()` and the `related_anime` graph traversal pattern.

### S. Streaming Availability Check 🆕
* **Concept:** Cross-reference recommendations against streaming service catalogs (Crunchyroll, Netflix, HIDIVE, etc.).
* **Why:** The most common follow-up question after getting a recommendation is "where can I watch it?" MAL doesn't provide this data natively, so it would require a third-party dataset or API.
* **CLI Command:** `recommendation --available-on crunchyroll`, `anime-detail 9253 --streaming`
* **Effort:** ~1 week. Requires external data source integration. Best deferred until the core recommendation pipeline is fully refined.

### T. Anime ID Mapping & Cross-Reference Cache 🆕
* **Concept:** Maintain a local cross-reference table mapping MAL IDs ↔ AniList IDs to enable multi-source lookups and faster searches.
* **Why:** Already planned in `ARCHITECTURE.md` Section 10 and `TODO.md` Phase 5. The `anime_id_mapping` table would allow the `anime-search` command to query AniList's faster GraphQL API and then resolve to MAL IDs for full recommendation details.
* **Implementation:** New Flyway migration creating `anime_id_mapping` table, a `CrossReferenceService` with cache TTL, and integration into `anime-search`.
* **Effort:** Part of the existing AniList integration plan (Phase 5).
