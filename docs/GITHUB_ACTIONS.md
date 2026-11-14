# GitHub Actions Workflows Guide

> **Status:** Workflow proposal guide with historical examples, not a workflow inventory. Read [AUTOMATION.md](AUTOMATION.md) for the six checked-in workflows and [current state](CURRENT_STATE.md) for verification limits. Only build/test, coverage, CodeQL, actionlint/reviewdog, DeepSeek, and PR-Agent are checked in at the audited baseline. Code-quality, dependency scanning, commit validation, documentation gating, releases/semantic-release, and Docker sections remain proposals. Example test counts, coverage, durations, release outputs, and branch-protection claims below are illustrative/historical, not verified results or enabled settings.

Analysis completed: 2026-06-19 14:25 UTC

## Executive Summary

This repository uses GitHub Actions for continuous integration and automated quality gates. The core CI build and test pipeline is active in `.github/workflows/ci-build-test.yml`.

Additional workflows for code quality, security analysis, and reviewer automation are detailed below.

---

## Current State

| Item | Status | Details |
|------|--------|---------|
| CI/CD pipelines | Active | `.github/workflows/ci-build-test.yml` active on PRs and main branch pushes |
| Build system | Ready | Gradle with Java 25 toolchain |
| Tests | Ready | JUnit 5 + Spring Test |
| Task automation | Ready | Taskfile.yml for local dev |
| Version control | Ready | Git + conventional commits |

---

## Recommended Workflows

### 1. CI: Build and Test (Active)

**Purpose**: Verify code compiles and all tests pass on every push/PR

**Workflow name**: `ci-build-test.yml`

**Triggers**:
- Push to `main` branch
- Every pull request
- Manual dispatch

**Jobs**:
- Setup Java 25 toolchain (from gradle.properties)
- Run `./gradlew clean test`
- Upload test results/coverage
- Report failures

**Why needed**:
- Catch breakages before merging
- Ensure JDK 25 compatibility across environments (local system uses JDK 26)
- Verify Gradle builds consistently in CI
- Gate PRs: make this a branch protection requirement

**Estimated runtime**: 4-6 minutes

**Example output**:
```
✓ Build successful (45s)
✓ Unit tests passed (180 tests, 120s)
✓ Integration tests passed (12 tests, 60s)
```

---

### 2. 🟡 Code Quality & Linting (HIGH PRIORITY)

**Purpose**: Enforce code quality standards on every PR

**Workflow name**: `code-quality.yml`

**Triggers**:
- Pull requests
- Push to main

**Jobs**:

#### a) Checkstyle
- Enforce Java code style conventions
- Config: `checkstyle.xml` (create if needed)
- Checks: naming, imports, whitespace, comments

#### b) SpotBugs
- Detect potential bugs in Java code
- Scans for common mistakes (null checks, resource leaks, etc.)
- Gradle plugin: `com.github.spotbugs`

#### c) SonarQube (optional)
- Static code analysis
- Code coverage metrics
- Technical debt tracking
- Can integrate with SonarCloud for free (public repos)

**Why needed**:
- Consistent code style across codebase
- Early bug detection before code review
- Supports REFACTORING.md goal: code quality standards
- Prevents accumulation of technical debt

**Estimated runtime**: 3-5 minutes

**Example output**:
```
✓ Checkstyle: 0 violations
✓ SpotBugs: 0 high-priority issues
✓ Code coverage: 72%
```

---

### 3. 🟡 Dependency Scanning (HIGH PRIORITY)

**Purpose**: Detect and manage vulnerable dependencies

**Workflow options**:

#### a) GitHub Dependabot (Recommended - Free & Automatic)
- **Setup**: Create `.github/dependabot.yml`
- **Auto-creates PRs** for new versions and security updates
- No workflow needed; GitHub handles it
- Already free for public repos

#### b) OWASP Dependency Check
- Gradle plugin: `org.owasp.dependencycheck`
- Scans for known vulnerabilities in dependency tree
- Workflow triggers on schedule (daily/weekly) or PR

#### c) Gradle dependency-check integration (proposed)
- Requires an added plugin such as OWASP Dependency Check; not built into Gradle
- `./gradlew dependencyCheckAnalyze` is unavailable until that plugin is configured

**Why needed**:
- Spring Boot ecosystem has frequent security patches
- Catch vulnerable dependencies early
- Automatic updates reduce manual work
- Phase D (reliability/security) requirement

**Estimated runtime**: Automatic (Dependabot) or 2-3 minutes (check)

**Example output**:
```
✓ No known vulnerabilities found
[Optional] Dependabot PR: Bump spring-boot from 3.5.14 to 3.5.15
```

---

### 4. 🟢 Commit Message Validation (MEDIUM PRIORITY)

**Purpose**: Enforce Conventional Commits format on all PRs

**Workflow name**: `commit-validation.yml`

**Triggers**:
- Pull requests (checks all commits in PR)

**Tool options**:
- `commitlint`: Industry standard tool
- GitHub Action: `compilerla/conventional-pre-commit@v2` or `wagoid/commitlint-github-action`

**Configuration** (`.commitlintrc.json`):
```json
{
  "extends": ["@commitlint/config-conventional"],
  "rules": {
    "type-enum": [2, "always", [
      "feat", "fix", "docs", "style", "refactor", "test", "chore", "ci"
    ]],
    "type-case": [2, "always", "lowercase"],
    "subject-case": [2, "always", "lowercase"]
  }
}
```

**Why needed**:
- Aligns with your custom instructions (atomic commits + conventional commits)
- Enforces team consistency
- Enables automated changelog generation in future
- Prevents messy commit history

**Estimated runtime**: < 1 minute

**Example output**:
```
✓ Commit "feat: add AniList support" matches format
✓ Commit "fix: null pointer in cache" matches format
✗ Commit "WIP stuff" does NOT match format → FAIL
```

---

### 5. 🟢 Documentation Validation (MEDIUM PRIORITY)

**Purpose**: Check markdown files and maintain documentation quality

**Workflow name**: `docs-validation.yml`

**Triggers**:
- PRs that modify `.md` files
- Optional: push to main

**Jobs**:

#### a) Markdownlint
- Check markdown syntax and style
- Config: `.markdownlintrc`
- Catches: trailing spaces, line length, header format, etc.

#### b) Link Checker
- Verify all URLs in markdown are valid/reachable
- Tool: `gaurav-nelson/github-action-markdown-link-check`
- Prevents broken documentation links

#### c) Schema Validation (optional)
- Validate YAML in docs (e.g., configuration examples)

**Why needed**:
- Catch typos in ARCHITECTURE.md, REFACTORING.md, README.md
- Verify documentation links don't break
- Maintain professional documentation quality
- Prevent stale/outdated docs from being committed

**Estimated runtime**: < 2 minutes

**Example output**:
```
✓ REFACTORING.md: No markdown errors
✓ All links in README.md are valid
✗ ARCHITECTURE.md has trailing spaces on line 42
```

---

### 6. 🟢 Build Artifact & Release (MEDIUM PRIORITY)

**Purpose**: Automate release builds and publish artifacts

**Workflow name**: `release.yml`

**Triggers**:
- Git tags matching `v*` (e.g., `v1.0.0`, `v2.1.3`)

**Jobs**:
- Compile with `./gradlew clean bootJar`
- Create GitHub Release
- Upload JAR artifact
- (Optional) Deploy to Maven Central / GitHub Packages

**Why needed**:
- Automated release process (no manual steps)
- Users can download pre-built JARs from releases
- Reproducible builds for each version
- Tag-based workflow is standard for Java projects

**Estimated runtime**: 5-8 minutes

**Example workflow**:
```bash
# Developer creates a release:
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0

# GitHub Actions automatically:
# 1. Builds JAR: anime-1.0.0.jar
# 2. Creates Release on GitHub
# 3. Uploads JAR + checksum
# 4. Users can now download from releases page
```

**Example output**:
```
✓ Built anime-1.0.0-SNAPSHOT.jar (12 MB)
✓ Created GitHub Release v1.0.0
✓ Uploaded artifact
```

---

### 7. 🔵 Docker Image Build (LOW PRIORITY - Future)

**Purpose**: Build and push Docker images for easy containerization

**Workflow name**: `docker-build.yml`

**Triggers**:
- Push to `main` (build as `latest`)
- Git tags (build as versioned image)

**Prerequisites**:
- Create `Dockerfile` in repo root
- Docker Hub or GitHub Container Registry account (optional)

**Jobs**:
- Build Docker image
- Run security scan (Trivy)
- Push to registry

**Why needed**:
- Easy deployment without needing Java installed
- Consistent runtime environment
- Useful for Phase D (reliability/hardening)
- Later useful for containerized deployments

**Estimated runtime**: 3-5 minutes per build

**Not urgent** — defer until Phase D/E when deployment architecture is defined

---

## Implementation Roadmap

### Phase 1: Essential CI/CD (Week 1)
**Effort**: ~5 hours | **Timeline**: 1 day

1. **CI: Build & Test** (Implemented in PR #110)
   - Created `.github/workflows/ci-build-test.yml`
   - Configured with JDK 25 and JDK 21 Gradle daemon toolchains
   - Runs `task test`

2. **Code Quality** (1-2 hours)
   - Create `.github/workflows/code-quality.yml`
   - Add Checkstyle config: `checkstyle.xml`
   - Add SpotBugs to build.gradle

3. **Dependency Scanning** (1 hour)
   - Create `.github/dependabot.yml` (free, automatic)
   - Optional: Add OWASP dependency check workflow

### Phase 2: Quality Gates & Release (Week 2)
**Effort**: ~5 hours | **Timeline**: 1 day

4. **Commit Message Validation** (30 minutes)
   - Create `.github/workflows/commit-validation.yml`
   - Add `.commitlintrc.json`
   - Install commitlint dependency

5. **Documentation Validation** (1 hour)
   - Create `.github/workflows/docs-validation.yml`
   - Add `.markdownlintrc`
   - Test on existing .md files

6. **Release/Artifacts** (2 hours)
   - Create `.github/workflows/release.yml`
   - Test with a trial tag: `git tag v0.0.1-test`

### Phase 3: Containerization (Later)
**Effort**: ~3 hours | **Timeline**: 1 day (after Phase D)

7. **Docker Image Build** (2-3 hours)
   - Create `Dockerfile`
   - Create `.github/workflows/docker-build.yml`
   - Set up registry credentials

---

## Directory Structure

After implementation, your `.github` directory will look like:

```
.github/
├── workflows/
│   ├── ci-build-test.yml           # Build & test on every push/PR
│   ├── code-quality.yml            # Linting & analysis
│   ├── dependency-scan.yml         # OWASP dependency check (optional)
│   ├── commit-validation.yml       # Enforce conventional commits
│   ├── docs-validation.yml         # Markdown & link validation
│   ├── release.yml                 # Release builds on tags
│   └── docker-build.yml            # Docker images (Phase 3)
│
├── dependabot.yml                  # Auto-updates for dependencies
└── CODEOWNERS                       # Optional: auto-assign reviewers
```

Top-level config files:

```
repo root/
├── .commitlintrc.json              # Commitlint configuration
├── checkstyle.xml                  # Code style rules
├── .markdownlintrc                 # Markdown linting rules
└── Dockerfile                      # Docker image definition (Phase 3)
```

---

## Integration with Project Goals

### Aligns with REFACTORING.md
- **Code Quality workflow** → Helps enforce issues #6-18 (clean code standards)
- **CI workflow** → Catches regressions during refactoring
- **Commit validation** → Ensures atomic, conventional commits per recommendation
- **Documentation validation** → Keeps REFACTORING.md, ARCHITECTURE.md in sync

### Aligns with ARCHITECTURE.md
- **Phase D (Reliability/Security)** → Dependency scanning + release workflow
- **Phase E (Advanced)** → Docker workflow for containerized deployment
- **General** → Branch protection requires CI passes before merging

### Aligns with your custom instructions
- **Conventional Commits** → Commitlint enforces your preference
- **Atomic commits** → Commit validation encourages small, focused changes
- **Test coverage** → CI validates tests pass before merging

---

## Branch Protection Rules

After implementing CI, recommend setting these GitHub branch protection rules on `main`:

```
Require status checks to pass before merging:
  ✓ build (CI: Build & Test)
  ✓ quality (Code Quality)
  ✓ conventional-commits (Commit Message Validation)
  
Require branches to be up to date before merging:
  ✓ Yes

Require code reviews:
  ✓ 1 review (optional, depending on team size)

Require approval of the latest reviewable push:
  ✓ Yes
```

If configured and enforced, the proposed rules would require:
- ✅ Passed tests
- ✅ Met code quality standards
- ✅ Conventional commit format
- ✅ Up-to-date with main

---

## Implementation Timeline

**Total effort**: ~10-12 hours (~1-2 days)

| Phase | Workflows | Time | Priority |
|-------|-----------|------|----------|
| **Week 1** | CI, Quality, Dependabot | 5 hours | 🔴 CRITICAL |
| **Week 2** | Commits, Docs, Release | 5 hours | 🟡 HIGH |
| **Later** | Docker | 3 hours | 🔵 LOW |

---

## Quick Start Checklist

### Day 1: Essential CI
- [x] Create `.github/workflows/` directory
- [x] Add `ci-build-test.yml` workflow
- [x] Test on a branch with a broken test to verify it fails
- [ ] Verify hosted branch protection rule for CI (not established by repository files)
- [ ] Add `checkstyle.xml` + SpotBugs to code-quality workflow
- [ ] Create `.github/dependabot.yml` for automatic updates

### Day 2: Quality & Release
- [ ] Add `commit-validation.yml` + `.commitlintrc.json`
- [ ] Add `docs-validation.yml` + `.markdownlintrc`
- [ ] Create `release.yml` workflow
- [ ] Test release workflow with a trial tag

### Future: Containerization
- [ ] Create `Dockerfile`
- [ ] Add `docker-build.yml` workflow
- [ ] Set up Docker registry credentials (if pushing images)

---

## Key Benefits

| Benefit | Workflows | Impact |
|---------|-----------|--------|
| **Catch bugs early** | CI | Prevent broken code from merging |
| **Code standards** | Code Quality | Consistent style across team |
| **Security** | Dependabot, OWASP | Patch vulnerabilities faster |
| **Release automation** | Release | No manual build steps |
| **Deployment ready** | Docker | Easy containerized deployment |
| **Consistency** | Commit validation | Professional commit history |
| **Documentation quality** | Docs validation | Keep docs in sync with code |

---

## Next Steps

1. **Prioritize**: Start with CI: Build & Test (blocks other work)
2. **Implement incrementally**: One workflow per session if needed
3. **Test workflows**: Trigger with test branches before finalizing
4. **Set branch rules**: After CI is stable, enable branch protection
5. **Document**: Update README.md with CI status badge
6. **Iterate**: Adjust workflows based on team feedback

---

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Commitlint Documentation](https://commitlint.js.org/)
- [Markdownlint](https://github.com/igorshubovych/markdownlint-cli)
- [SpotBugs Gradle Plugin](https://plugins.gradle.org/plugin/com.github.spotbugs)
- [GitHub Dependabot](https://docs.github.com/en/code-security/dependabot)
- [Docker Best Practices](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/)
