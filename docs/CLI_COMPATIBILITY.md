# CLI Compatibility and Versioning Policy

## Contract

Treat command names, accepted option spellings/types, positional arguments, defaults (including interactive defaults), exit/error behavior, and JSON output field names/types/meaning as the CLI public contract. Preserve existing invocations and script-readable JSON within a stable major version. Human-readable table layout may evolve, but changes that prevent normal use or alter documented semantics require compatibility review. This policy does not add an HTTP API or duplicate versioned commands.

New optional capabilities must preserve existing defaults. When replacing a command or flag, retain the existing spelling as a deprecated alias during a documented migration window where feasible. Document the replacement, old/new examples, affected versions, and the removal release in release notes and help. Deprecation warnings must not corrupt JSON stdout. Remove the old interface only in the announced breaking release. A default change that alters results, prompts, or unattended invocation is a breaking change even if the option name stays the same.

For JSON, consumers should tolerate additional fields. Adding an optional field is compatible only if existing values, types, and semantics remain unchanged. Removing/renaming a field, changing number/string/nullability or top-level shape, or silently changing score meaning is breaking. Review representative old invocations and JSON fixtures before release; rank-quality changes must preserve documented scoring invariants unless explicitly approved and communicated.

## SemVer and release artifacts

Use Semantic Versioning (`MAJOR.MINOR.PATCH`) and Conventional Commits: `fix:` for compatible bug fixes (patch), `feat:` for compatible additions (minor), and `!` or a `BREAKING CHANGE:` footer for incompatible changes (major after 1.0). `docs:` alone must not imply a runtime release. Before 1.0, explicitly announce breaking changes in the next minor release; do not hide them in patch releases. These are policy expectations for the project's semantic-release discipline, not evidence of an installed release pipeline.

At the audited [baseline](CURRENT_STATE.md), no semantic-release configuration or release workflow is checked in. [build.gradle](../build.gradle) sets `version = '0.0.1-SNAPSHOT'`; [Taskfile.yml](../Taskfile.yml) runs `anime-0.0.1-SNAPSHOT.jar`. A `v1.2.3` tag alone does not update Gradle's version or rename the JAR. A future release process must synchronize tag `vX.Y.Z`, Gradle version `X.Y.Z`, published JAR `anime-X.Y.Z.jar`, and release notes/checksums, and update the Taskfile's fixed path or make it version-aware before claiming automatic releases. Snapshot builds are development artifacts, not stable releases. The tag-triggered release example in [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md) remains a proposal.

## Compatibility examples

These are hypothetical review cases, not shipped features:

| Change | Compatible treatment | Incompatible treatment |
|---|---|---|
| Add an optional filter | `feat:` minor; old invocation and default results unchanged | Apply the filter by default without migration; breaking |
| Rename `--minYear` | Keep `--minYear` working as deprecated alias; document replacement | Remove old spelling in a patch; disallowed |
| Change default grouping or limit | Introduce opt-in behavior first; document eventual breaking release | Silently change defaults because signatures match; breaking |
| Extend JSON result | Add optional field while preserving existing fields/types/semantics; minor | Rename `occurrence`, return a string instead of number, or wrap array in object; breaking |
| Fix a bug | Restore documented behavior with regression evidence; patch | Reweight franchise averages under a `fix:` label without explicit invariant approval; disallowed |
| Publish `v1.2.3` | Build and label the JAR `1.2.3` with matching release notes | Ship `anime-0.0.1-SNAPSHOT.jar` as if the tag updated it; inconsistent |

Review confirms positive cases preserve old callers; negative cases require an announced breaking release or are rejected when they violate project invariants. No release job or binary compatibility test was run. Read-only packaged CLI help was checked with dummy credentials and disposable storage; no provider calls or recommendation execution were needed.
