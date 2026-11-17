# Implementation Decisions Log

This document records non-obvious engineering and implementation decisions made in WatchGraph, ordered newest first.

## 2026-11-14: Validate Conventional Commits across every commit in a pull request

- Decision: Validate every commit introduced in a PR's base-to-head range (including intermediate and merge commits) with a standalone Python standard-library validator and read-only workflow, enforcing allowed types, formatting, and attribution rules without adding Node dependencies.
- Rationale: Catch invalid intermediate commits that slip beneath a valid HEAD commit while avoiding a Node toolchain solely for commit-message checking.
- Rejected alternatives: Checking only the HEAD commit or adding a Node dependency tree (for example commitlint) for a single validation rule.
- Source: [#172 ci: validate conventional messages for every pr commit](https://github.com/gordonpn/watchgraph/pull/172), [#34 [Story] Validate Conventional Commits in pull requests](https://github.com/gordonpn/watchgraph/issues/34)

## 2026-11-14: Reviewed weekly Dependabot updates excluding Spring Boot 4+

- Decision: Configure weekly Dependabot update PRs for Gradle and GitHub Actions with grouped minor/patch updates, keeping major updates separate, disabling auto-merge, and excluding Spring Boot 4 and later.
- Rationale: Receive reviewable dependency and security updates while preserving the Spring Boot 3.x stack constraint and avoiding external bots or auto-merge.
- Rejected alternatives: Enabling auto-merge, upgrading to Spring Boot 4+, or adding external dependency update services without demonstrated need.
- Source: [#169 ci: enable reviewed dependency update automation](https://github.com/gordonpn/watchgraph/pull/169), [#33 [Story] Enable dependency update automation](https://github.com/gordonpn/watchgraph/issues/33)

## 2026-10-29: Reproducible Java 25 / Gradle CI with isolated local SQLite and JaCoCo coverage

- Decision: Run reproducible Gradle verification (`task test`) on Temurin JDK 25 in GitHub Actions isolated from developer credentials and local SQLite state, retain failing test reports as CI artifacts, and port JaCoCo coverage reporting alongside Reviewdog and AI review workflows.
- Rationale: Catch build and test regressions on Linux runners without requiring local Homebrew JDK paths or host credentials.
- Rejected alternatives: Relying on hardcoded local Homebrew JDK paths in CI.
- Source: [#113 ci: port GitHub Actions workflows from shunt-shim and snaptally](https://github.com/gordonpn/watchgraph/pull/113), [#110 ci: add reproducible build and test workflow](https://github.com/gordonpn/watchgraph/pull/110), [#111](https://github.com/gordonpn/watchgraph/issues/111), [#31](https://github.com/gordonpn/watchgraph/issues/31)

## 2026-10-20: Distribute as versioned executable JAR instead of container image

- Decision: Keep versioned executable JAR releases as the canonical distribution format and close container distribution as not planned.
- Rationale: Running an interactive CLI via containers introduces unnecessary volume and port-forwarding complexity for local SQLite persistence and OAuth callbacks.
- Rejected alternatives: Packaging the CLI application as a Docker container image.
- Source: [#37 [Story] Assess whether container distribution serves CLI users](https://github.com/gordonpn/watchgraph/issues/37)

## 2026-10-20: Keep watchgraph scoped to SQLite-backed anime recommendations on an on-demand CLI

- Decision: Reject AniDB enrichment, vector-based semantic ranking (OpenSearch/pgvector/ONNX), streaming availability datasets, manga recommendations, and background episode notifications, retaining the local SQLite + MyAnimeList/AniList on-demand CLI architecture.
- Rationale: Avoid rate-limit ban risks and mapping complexity from AniDB, external vector databases or heavy ML runtimes incompatible with a local Java CLI, commercial/brittle streaming scrapers, scope drift into manga, and long-running background daemons incompatible with an on-demand CLI.
- Rejected alternatives: Integrating AniDB feeds/mappings, adding semantic vector search infrastructure, scraping streaming availability, expanding into manga recommendations, and adding a background notification daemon.
- Source: [#17](https://github.com/gordonpn/watchgraph/issues/17), [#18](https://github.com/gordonpn/watchgraph/issues/18), [#70](https://github.com/gordonpn/watchgraph/issues/70), [#71](https://github.com/gordonpn/watchgraph/issues/71), [#72](https://github.com/gordonpn/watchgraph/issues/72), [#73](https://github.com/gordonpn/watchgraph/issues/73), [#74](https://github.com/gordonpn/watchgraph/issues/74)
