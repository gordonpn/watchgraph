# Candidate stack benchmark (#133)

This isolated experiment supports #132. It does not select a stack, change the
production build, or propose an Accepted ADR. Java 25, Kotlin 2.3.20 + Picocli
4.7.7, and Python 3.12 + Pydantic 2.12.4 implement the same narrow contract.

## Reproduce

Requirements: JDK 25, Python 3.12, uv, Node.js (Pyright), curl, bash, sha256sum
(`shasum -a 256` can replace sha256sum on macOS). Set `JDK25_HOME` first.
From the repository root:

```sh
benchmarks/stack/setup.sh
cd benchmarks/stack
.tools/venv/bin/python -m unittest discover -s python -p 'test_*.py'
.tools/venv/bin/python python/audit.py
.tools/venv/bin/python python/measure.py
```

Setup fetches pinned compiler/runtime jars over verified HTTPS and validates the
committed SHA-256 manifest. Python dependencies, including transitives, are pinned
with hashes. Tools/build outputs stay in ignored directories. No MAL credentials,
network access, Spring, Gradle, database, or production services are needed at run time.
`setup.sh` is repeatable; delete `.tools` and `build` for a clean dependency setup.
Kotlin's compiler emits upstream Jansi/Unsafe warnings on JDK 25; application
compilation still uses `-Werror`. These are compiler-runtime warnings, not ignored
source diagnostics.

## Shared deterministic contract

All candidates independently generate exactly 1,000 items, integer IDs 0–999,
using these formulas (all integer `%` operands are nonnegative):

| Field | Formula |
|---|---|
| rating (0–10 normalization) | `(id * 17 % 101) / 10.0` |
| votes | `id * 31 % 10001` |
| popularity rank | `id * 13 % 10000 + 1` |
| user score (0 means unrated) | `id % 11` |
| elapsed months | `id % 61` |
| occurrences | `id % 9 + 1` |

Undirected edges connect `i` to `i+1` unless `i % 7 == 6`; BFS finds 143 components,
including a final six-item component. Component label is its smallest ID. Every
item remains in the output so all 1,000 calculations are compared; this experiment
labels franchises rather than consolidating them into representatives.

Arithmetic order is the source order, using binary64 throughout:

1. Bayesian shrinkage: `(votes * rating + 100 * 7.0) / (votes + 100)`.
2. Taste multiplier: `user_score / 7.0`, or `1.0` when unrated.
3. Decayed occurrence: `occurrences * taste * pow(2.0, -months / 12.0)`.
4. Popularity normalization: `10000.0 / (10000.0 + popularity) * 10.0`.
5. Score: `occurrence + 0.5 * shrunk_rating + 0.0001 * popularity_score`.
6. Canonical integer micro-score: `floor(score * 1000000.0 + 0.5)`.

Step 5 and popularity normalization are extracted from `RankingService` default
weights; shrinkage is an explicitly experimental extension requested by #133.
No claim is made that this fixture replicates the entire production pipeline.
Raw libm floating point bits may vary across runtimes. The public benchmark output
is the canonical integer score, not raw floating point: byte equality means all
UTF-8 CSV bytes `id,component,micro_score\n` match after sorting score descending,
then ID ascending. It does not assert raw binary64 identity on other hosts.
The committed output is a reproducible regression artifact; subsequent runs reject
any change against that golden file. Tests also cover the
prior with zero votes, unrated fallback, exact half-life, strict input validation,
component partition, ranking order and percentile convention.

## Measurement and interpretation

[Metrics](results/metrics.md), [raw 30 trials](results/trials.csv), and
[type-check results](results/types.md), and [tool/host versions](results/environment.txt)
are actual Linux measurements from this
managed development host. macOS is **unrun**; no cross-platform performance claim
or maintainer ergonomic decision follows from these numbers.

Each of 10 trials per candidate spawns a fresh process through `/bin/sh`; timing
starts before spawning and ends at the first stdout byte. All candidates compute
and sort before printing, so this measures time to the first recommendation.
Total completion latency is retained separately in the raw CSV. Trials interleave
Java/Kotlin/Python; there are no discarded warmups. OS file caches are not flushed:
this is cold process startup, not a cold filesystem or JVM compilation benchmark.
Peak child RSS comes from `wait4`, normalized to MiB (Linux KiB, macOS bytes).
Runtime initialization, validation and CLI parsing are included. Java is a plain
JDK baseline; Kotlin includes Picocli dispatch; Python includes Pydantic imports
and validation. Framework overhead is intentional and these are not equivalent
framework feature sets. Shared cloud CPU load can materially affect results.

Percentiles use nearest rank `ceil(p*n)`; with n=10 **p95 and p99 both equal the
maximum** and are weak estimates of tail behavior. RSS shows median and maximum.
Payload bytes include app artifacts plus runtime dependencies, excluding JDK,
Python interpreter/stdlib, compiler/type-checker tools, caches and package metadata.
Python payload includes Pydantic and its runtime transitive packages. The Python
native extension footprint is platform-specific. This is a payload comparison,
not a standalone installer comparison.

Type timings are one fresh complete compile/check per candidate, followed by one
int-to-string assignment probe. Java/Kotlin timings include code generation;
Pyright does not emit code. This is tool invocation cost, not an isolated typing
algorithm comparison. The saved diagnostics show all three reject the assignment
with file, line, incompatible source and target types; this one probe is evidence,
not a comprehensive diagnostic precision study. Java/Kotlin use explicit records/
data classes and typed collections; Python uses immutable typed models and Pyright
strict over implementation, harness and tests. There is no `Any`, untyped dictionary,
raw collection, `type: ignore`, `Suppress`, cast escape or disabled diagnostic in
benchmark source. Third-party internals are outside this source audit.

Toolchain complexity: Java needs only javac/jar; Kotlin adds a pinned compiler and
its transitive tooling jars plus two runtime jars; Python needs a virtualenv,
Pydantic with a platform native wheel, Node.js/Pyright, and locked Python packages.
This experiment supplies evidence for a later maintainer-reviewed ADR; IDE
experience, persistence migration and ML interoperability remain outside #133.
