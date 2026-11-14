# Anime output baselines

These synthetic fixtures protect existing behavior before the Watchgraph migration.
No responses were downloaded from private user accounts. IDs, titles, dates, and
user scores are invented; no credentials, tokens, or personal data are included.

`AnimeGoldenOutputTests` invokes the command methods with the real recommendation
facade, ranking, filtering, explanation, formatter, and configured Jackson mapper.
Only list, anime lookup, and repository boundaries are mocked. An unexpected anime
lookup fails immediately; no Spring context, HTTP client, OAuth, or database starts.
The tests run with the normal `task test` / Gradle `test` suite and existing CI.

Inputs are `profile.json`, `completed.json`, `dropped.json`, and
`seasonal-input.json`. The completed graph is 1—3—2; the candidate graph is
10—12—11. Bridge 12 is absent from the recommendation pool and seasonal input,
but is the most popular franchise member. These protect connected components,
bridge representative selection, arithmetic ratings, pooled weighted occurrences,
completed/dropped exclusions, ordering, explanation text, and JSON/table formatting.
Separate outputs cover grouped, ungrouped, unrated, empty, and temporal profiles.

Time is fixed at 2026-01-01T12:00 for completion decay and the release year is
fixed at 2026. Locale is US and restored afterward. Only the current-time line in
health diagnostics is normalized to `<instant>`; the rest is compared exactly.
Table fixtures retain ANSI colors, Unicode borders, whitespace, and newlines.
`list` currently returns an `AnimeList` DTO; `list.json` captures that DTO through
the existing mapper for testing, without adding a production JSON output option.
Health has text baselines for a missing token and an unavailable database.

To review a failure, compare the expected fixture with the assertion diff. Update
an expected file only after reviewing and accepting an intentional behavior change;
never regenerate fixtures merely to make a refactor pass. JSON scores are compared
as exact output strings, without rounding or tolerance. Keep all future inputs
synthetic and credential-free.
