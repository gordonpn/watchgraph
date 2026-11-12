# Java quality checks

Run `task quality` or `./gradlew quality`. The standalone `Java quality` workflow runs the same Gradle `quality` aggregate on pull requests, pushes to `main`, and manual dispatch. `./gradlew check` also runs the source checks and rule fixtures. Reports are under `build/reports/checkstyle/`; CI uploads them even after a failed check.

## Scope and compatibility

The first gate uses Gradle's built-in Checkstyle plugin with **Checkstyle 14.4.0**, pinned in `build.gradle`, and the single shared configuration `config/checkstyle/checkstyle.xml`. Both application and test Java sources are checked. Generated build output and the intentionally invalid rule fixtures are not application/test source sets.

Checkstyle runs explicitly on **JDK 21**, independently of the application's **Java 25** toolchain. Its [official compatibility documentation](https://checkstyle.org/) states that 13.x/14.x require Java 21 or later and that it parses all Java 25 language features. The checked-in [Gradle 8.12.1 compatibility matrix](https://docs.gradle.org/8.12.1/userguide/compatibility.html#java_runtime) supports running Gradle on JDK 21; it does **not** promise Java 25 runtime/toolchain support. This change preserves the existing daemon-21/application-25 setup, verifies it in practice, and does not claim or introduce a Gradle compatibility upgrade. CI supplies the detected application JDK through the verified `-Porg.gradle.java.installations.paths` project-property override, rather than assuming the checked-in macOS discovery path applies on Linux. The Checkstyle [javaLauncher property](https://docs.gradle.org/8.12.1/dsl/org.gradle.api.plugins.quality.Checkstyle.html#org.gradle.api.plugins.quality.Checkstyle:javaLauncher) selects the analysis runtime.

SpotBugs was evaluated rather than enabled by default. Its [4.9.8 changelog](https://github.com/spotbugs/spotbugs/blob/4.9.8/CHANGELOG.md#497---2025-10-14) records JDK 25 build support in 4.9.7, and the [6.4.6 Gradle plugin documentation](https://github.com/spotbugs/spotbugs-gradle-plugin/blob/6.4.6/README.md) requires Gradle 8.2+ for its illustrated setup. These versions are plausible candidates, not proof that every detector works on this project's Java 25/Lombok bytecode. Onboarding a bytecode detector set and assessing legacy findings is deferred to its own change; no claim of a clean SpotBugs scan is made. Existing CodeQL remains unchanged. No SonarCloud or dependency scanning plugin is added. `dependencyCheckAnalyze` is not a built-in Gradle task.

Sources accessed 2026-11-12. Version and syntax support statements are distinguished from the local verification results below.

## Initial rules and legacy scope

| Rule | Prevents |
| --- | --- |
| `EmptyStatement` | An accidental semicolon becoming an empty statement, such as `if (ready);` |
| `EqualsHashCode` | An explicit `equals` override without `hashCode`, violating the hash collection contract |
| `FallThrough` | Unmarked fall-through into the next switch case |
| `OuterTypeFilename` | A top-level type whose name does not match its source filename |

This is a deliberately small correctness-focused rule set. It imposes no formatting, import order, line-length, comment or naming migration. Existing code must pass the same rules as new code: there is no legacy exclusion list, suppression file, ignored source set or relaxed failure threshold. Adding rules requires reviewing baseline findings and providing fixtures. This source-only gate does not detect null dereferences, resource leaks, Lombok-generated equality issues or other bytecode bugs.

## Verification fixtures

`qualityRulesTest` runs the real pinned Checkstyle CLI twice, using the production configuration and JDK 21 launcher. The clean fixture includes a record, record-pattern switch and a matched equality/hash pair. Each violating fixture isolates one enabled rule; a filename mismatch is deliberately not compilable and stays outside Java source sets.

The CLI also uses a fixture-only `commons-logging:1.4.0` configuration, matching [Checkstyle 14.4.0's declared dependency](https://github.com/checkstyle/checkstyle/blob/checkstyle-14.4.0/pom.xml); it does not enter the application or test runtime. The resolved Gradle Checkstyle classpath alone lacks this CLI entry-point dependency.

The verifier requires a zero exit code and no errors for the clean fixture. For violations it requires a nonzero exit code matching the four findings, and exactly one finding of the expected rule for each expected filename. A missing rule, unexpected parser failure, missing fixture, extra finding or clean-file regression fails the verification task. CLI exit codes are captured solely for these assertions; application/test checks retain Gradle's normal failure behavior.

Expected rejection reports in `build/reports/checkstyle/fixtures/violations.xml` are test evidence, not a production baseline suppression. `quality` includes `checkstyleMain`, `checkstyleTest` and `qualityRulesTest`, so fixture tests cannot replace checking the actual source tree.
