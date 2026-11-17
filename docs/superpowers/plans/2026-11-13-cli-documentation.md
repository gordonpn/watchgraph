# CLI Documentation Reconciliation Implementation Plan

> **For agentic workers:** Use native inline execution for this documentation-only task; parent review precedes commit/push.

**Goal:** Satisfy #29 and supporting #107 against main `6b4ade2371cf3da2009aa06821f6d60c0cee69f7`.
**Architecture:** Preserve execution history, add explicit current-state precedence, repair active local links, and document CLI compatibility without implementing releases or new architecture.
**Tech stack:** Markdown; existing Java 25/Spring Boot 3.x/Spring Shell/JPA/Flyway/SQLite unchanged.
**Spec:** GitHub issues #29 and #107; parent-provided acceptance contract.

## Tasks

- [x] Read all 14 project Markdown files and the tracked reviewer prompt; inspect source/tests, Taskfile, build/version settings, and all six checked-in workflows.
- [x] Repair README and mandatory-read paths; reconcile command/test/source counts, formatter/exceptions/package moves, and flag spellings.
- [x] Mark audits/plans/walkthroughs/research as historical/proposed while preserving execution history and current invariant precedence.
- [x] Add CLI contract/SemVer policy with compatible and incompatible examples; distinguish snapshot JAR from future release automation.
- [x] Run complete local Markdown file/anchor checks and `git diff --check`; record exact coverage and limitations below.
- [x] Obtain independent parent review before commit/push.
- [ ] Submit one PR closing only #29 and #107.

## Review focus

Check containing-file-relative paths, historical developer `file://` links, actual registered option spellings, stale audit counts/coverage assumptions, and workflow proposals mistaken for installed automation. Java tests are not rerun because application/build/test configuration is unchanged. #35's ongoing documentation CI/linter gate stays separate.

## Verification evidence

Parent review corrected inferred kebab-case options after actual packaged `help list`, `help recommendation`, and `help seasonal` showed Spring Shell 3.3.3 retains camelCase parameter names. All three help commands exited successfully with dummy credentials, disposable storage, and a dead local HTTP proxy; no live provider or recommendation operation was used.

The bounded local Markdown checker examined 18 Markdown files, 71 local targets (including one heading fragment), and excluded 35 remote citations; zero local errors. `git diff --check` passes. No Java test suite or release job was rerun for the documentation-only change. The checker was a one-off local verification artifact, not a new CI gate; #35 remains separate.
