# Recommendation input contract

`RecommendationFacade.getRecommendations` validates inputs before reading local
lists, caches, or calling MAL. All overloads share the same checks:

| Parameter | Accepted values |
| --- | --- |
| `userName` | Nonblank name, including the local-list alias `@me` |
| `limit` | Positive integer; no new upper cap |
| `minYear`, `maxYear` | Absent (`null`) or positive years; minimum must not exceed maximum |
| `decayMonths` | Absent, zero (disabled), or positive half-life in months |
| `freshnessBoost` | Absent, zero (disabled), or finite positive rate |
| `excludeWatchedFranchises`, `groupCandidates` | Existing nullable flags; null continues to mean false and true respectively |

`AnimeService.getAnimeById` accepts positive anime IDs, rejecting zero and negative
IDs before cache or HTTP access. Inputs are validated without trimming names,
clamping values, or imposing a release-year upper bound.

Invalid values raise `IllegalArgumentException` with the offending parameter and
its accepted range. For example, `limit must be positive` or
`freshnessBoost must be finite and nonnegative`. Spring Shell reports the error
through its existing command error handling; there are no new flags or prompts.

CLI defaults remain unchanged: omitted interactive values are resolved by the
existing prompts; the recommendation limit defaults to 10, year filters are
optional, and absent or zero temporal values disable the corresponding weighting.
The two-argument facade overload leaves all filters absent. Valid recommendation
scoring and franchise grouping are unchanged.

Local list synchronization skips missing or nonpositive anime IDs before repository
lookup or persistence. Recommendations also skip existing completed local rows with
invalid IDs before cache or detail lookups, so valid rows still contribute normally.
The public anime lookup contract remains strict.
