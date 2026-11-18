# Repository Automation

Workflows in this repository are adapted from the automated quality gates and review infrastructure in `gordonpn/shunt-shim` and `gordonpn/snaptally`.

## Workflows Overview

| Workflow | File | Triggers | Required Secrets | Purpose |
|---|---|---|---|---|
| CI: Build & Test | `ci-build-test.yml` | Push `main`, PR, manual | None | Compiles and executes tests across the Java 25 toolchain, archiving test reports. |
| Coverage | `coverage.yml` | Push `main`, PR | `CODECOV_TOKEN` (Optional) | Executes tests with JaCoCo and uploads XML coverage reports to Codecov via OIDC or token. |
| CodeQL | `codeql.yml` | Push `main`, PR, Monday cron | None | Analyzes compiled Java classes for vulnerabilities via CodeQL manual build mode. |
| Reviewdog | `reviewdog.yml` | PR | None | Validates GitHub Actions workflow syntax using actionlint and annotates PR diffs. |
| DeepSeek Review | `deepseek-review.yml` | PR `opened`, `reopened`, `synchronize`, `ready_for_review` | `DEEPSEEK_API_KEY` | Performs skeptical correctness review focusing on runtime contracts and edge cases. |
| PR-Agent | `pr-agent.yml` | PR `opened`, `reopened`, `synchronize`, `ready_for_review` | `DEEPSEEK_API_KEY` | Performs skeptical architecture review. |

## Workflow Details

### 1. Build and Coverage Toolchains
- **Runtime Environment:** Compiles and tests against Temurin JDK 25 via Gradle toolchains. The Gradle daemon runs on JDK 21.
- **JaCoCo Reporting:** Configured via `jacocoTestReport` in `build.gradle` and finalized by the `test` task.
- **Codecov Upload:** Runs with least-privilege permissions (`contents: read`, `id-token: write`) and authenticates via GitHub OIDC (`use_oidc: ${{ secrets.CODECOV_TOKEN == '' }}`). If `CODECOV_TOKEN` is configured, it is passed directly.

### 2. CodeQL Static Analysis
- Configured for `java-kotlin` with `build-mode: manual`.
- Executes `task build` (`./gradlew clean bootJar`) to allow CodeQL to trace class generation.
- Permissions include `actions: read` and `security-events: write`.

### 3. Reviewdog & Actionlint
- Runs `actionlint` via `reviewdog/action-actionlint@v1` on all pull requests, including fork pull requests.
- Emits review comments directly onto GitHub PRs for any workflow syntax or type issues.

### 4. AI Code and Architecture Reviewers
Both reviewers run on opening, reopening, marking ready, or pushing to an eligible PR.
Eligible PRs are non-draft, originate in this repository, and have a human author
and event sender. Forks, bots, and Dependabot are excluded before the jobs can
access the repository secret.

Each job checks the configured DeepSeek endpoint and authenticates to its
`/models` route before invoking its reviewer. Missing credentials, connection
errors, non-200 responses, and reviewer errors fail the job. The credential check
discards provider response bodies. DeepSeek Review uses its native error exit,
and PR-Agent uses a release that propagates recorded review failures.

PR-Agent runs from PR events so the workflow can check origin and author from
the event payload. Comment events omit that provenance and no longer invoke the
reviewer. Each push reviews the resulting PR head, subject to Actions concurrency.

Run `task test-reviewers` to lint both workflows and test the credential check
against simulated HTTP and transport failures. After provisioning a valid
`DEEPSEEK_API_KEY` repository secret, verify both hosted jobs publish a review
and repeat after pushing a new head to the same eligible PR.
