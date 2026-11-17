# Scheduler and SQLite shutdown verification

Spring's existing lifecycle handles the verified retry-sleep shutdown scenario. No application shutdown wrapper or retry interceptor change is required.

`ShutdownLifecycleTests` launches the packaged `RecommendationApplication.main` in a separate Java 25 process. It covers both typing `exit` into Spring Shell and sending SIGTERM while the real scheduled `BackgroundFetchService` is in an unbounded MAL retry wait. The first wait is 60 seconds, so completing shutdown before that wait expires demonstrates interruption rather than natural retry completion.

The regression budget is **30 seconds after the exit command or SIGTERM**. The configured `spring.lifecycle.timeout-per-shutdown-phase=20s` allows the scheduler's active task to finish before destruction interrupts it. Tests leave that production setting intact and allow ten seconds for destruction, observation, and process teardown. This budget covers the controlled retry-sleep scenario, rather than arbitrary blocked external I/O or SIGKILL.

## Isolation and observations

Each child process uses a temporary working directory and SQLite database. Test-only configuration supplies a future-dated dummy token, replaces the MAL HTTP transport with a controlled 503 response, and blocks OAuth transport. It retains the production services, interceptor, scheduled executor, JPA, Flyway migrations, and application main method. It never synchronizes a real account or reaches an external HTTP server.

The test records and asserts:

- Exactly one HTTP attempt, followed by retry sleep; no resumed external request during cancellation.
- The existing retry handler observes the thread's interrupt flag as set.
- The scheduled executor terminates, the entity manager factory closes, and every connection opened through the application's datasource closes.
- The child process exits within the 30-second budget: code 0 for shell exit, code 143 for SIGTERM on the Linux test platform.
- The disposable SQLite database accepts an exclusive transaction after exit.

SQLiteDataSource is unpooled and has no datasource-level close operation. The meaningful resource assertions are closure of its JDBC connections and the JPA entity manager factory, rather than calling a nonexistent datasource `close()`.

Resource observation runs after scheduler and entity-manager destruction. The test observer waits at most five seconds for the executor's asynchronous termination; it does not stop the executor, cancel tasks, change shutdown settings, or close database resources itself.

## Reproduction

The ordinary `test` task includes the two process checks. A separate `lifecycleBootJar` packages the normal compiled application and runtime dependencies for these checks, avoiding production AOT generation and its startup credential validation. The test fixture is packaged separately and loaded through Spring Boot's PropertiesLauncher; it is not part of the application artifact.

```sh
./gradlew test --tests '*ShutdownLifecycleTests'
./gradlew test
```

In the managed workspace, run the full task with Java 25 toolchain discovery and explicitly unset parent MAL credentials:

```sh
source /workspace/.setup/watchgraph/env.sh
env -u MAL_CLIENT_ID -u MAL_CLIENT_SECRET ./gradlew \
  -Porg.gradle.java.installations.paths="$JDK25_HOME" \
  --no-daemon --max-workers=2 test
```

On 2026-11-12, the focused packaged-process run passed both cases: shell exit completed in **20,114 ms**, and SIGTERM in **20,070 ms**. Both recorded the resource and interruption assertions above. The task graph for `lifecycleBootJar --dry-run` contained only `compileJava`, `processResources`, `classes`, and `lifecycleBootJar`; production `processAot` is not a test dependency.
