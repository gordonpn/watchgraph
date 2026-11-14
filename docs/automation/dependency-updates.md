# Dependency updates

Dependabot checks Gradle manifests (including the wrapper) and GitHub Actions
references every Monday at 09:00 America/New_York, configured in
[`.github/dependabot.yml`](../../.github/dependabot.yml). Each ecosystem allows
up to five open version-update PRs. Minor and patch version updates are grouped
per ecosystem; major updates remain separate for deliberate compatibility review.
Spring Boot 4 and later are excluded to preserve the project's Spring Boot 3.x
constraint. No private registry credentials or additional service is required.

Dependabot PRs target the default branch and run the existing pull-request build,
test, coverage, and analysis workflows. Maintainers review test results, release
notes, compatibility, and vulnerability fixes before merging. No auto-merge
workflow is introduced. Existing bot exclusions in AI review workflows remain
in effect; they do not exclude build and test checks.

Version updates follow the weekly schedule. Dependabot security updates and
vulnerability alerts are separate repository settings: maintainers must enable
those features in GitHub's security settings if desired. This file does not
activate those settings or guarantee that all vulnerable transitive dependencies
can be updated automatically. Vulnerability response remains review-based.

The configuration was checked against the
[Dependabot v2 JSON schema](https://json.schemastore.org/dependabot-2.0.json).
GitHub performs the actual dependency discovery and creates updates after this
configuration reaches the default branch; that scheduled service cannot be
exercised by a local configuration check.
