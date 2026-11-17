# Refactoring Opportunities

> **Status:** Historical audit (June 2026). Findings, line numbers, counts, estimates, and proposed snippets below describe that scan, not current shipped status. Read the [current reconciliation](CURRENT_STATE.md) first: formatter extraction, domain exceptions, utility/DTO/entity moves, and core tests already exist. Old requests to create them are superseded; broader consistency/decomposition remains pending. The old coverage percentage is unsupported and withdrawn. CLI versioning is now covered by the [compatibility policy](CLI_COMPATIBILITY.md).

Comprehensive code scan analysis completed: 2026-06-19 14:00 UTC

## Quick Summary

The codebase has good architectural foundation (CLI commands, service layer, recommendation facade) but has refactoring opportunities across clean code, SOLID compliance, and maintainability.

**Estimated effort**: 15-20 days for full compliance

---

## HIGH SEVERITY ISSUES (5)

### 1. JsonProcessingException Propagation (9+ files)
**Problem**: Methods throw `JsonProcessingException` directly, violating encapsulation and forcing callers to handle Jackson implementation details.

**Files**: 
- `src/main/java/com/gordonpn/anime/service/RecommendationService.java:40`
- `src/main/java/com/gordonpn/anime/service/AnimeService.java:32,80`
- `src/main/java/com/gordonpn/anime/service/UserListService.java:31`
- `src/main/java/com/gordonpn/anime/commands/RecommendationCommands.java:50,86`
- Plus 5+ other files

**Impact**: 
- Pollutes API signatures with Jackson implementation details
- Makes code hard to test (requires handling Jackson exceptions)
- Violates the dependency inversion principle (high-level modules depend on low-level Jackson details)
- Breaks encapsulation of the service layer

**Suggested refactoring**:
```java
// Create domain exceptions:
public class AnimeDataProcessingException extends RuntimeException {
    public AnimeDataProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Update service layer:
public List<RecommendationDetailedResult> getRecommendations(String userName, int limit) {
    try {
        // ... existing logic
    } catch (JsonProcessingException e) {
        throw new AnimeDataProcessingException("Failed to process recommendation data", e);
    }
}
```

---

### 2. Cache Handling Duplication (60+ lines)
**Problem**: Identical cache-check-fetch-fallback logic repeated in:
- `src/main/java/com/gordonpn/anime/service/AnimeService.java:32-78,80-133`
- `src/main/java/com/gordonpn/anime/service/UserListService.java:31-82`

Pattern repeats 3 times with identical structure:
1. Check cache, return if valid (with TTL check)
2. Fetch from API
3. Update cache
4. Fallback to expired cache on error

**Impact**: 
- 60+ lines of duplicated logic across 3 files
- Bug fixes need to be applied in 3 places
- Hard to maintain TTL strategy consistently
- Violates DRY principle

**Suggested refactoring**:
```java
// Create generic cache service:
@Service
public class CacheService {
    public <T> T getOrFetch(
        String cacheKey, 
        Supplier<Optional<? extends CachedEntity>> cacheLoader,
        Supplier<String> fetcher,
        Function<String, T> deserializer,
        int ttlDays,
        GenericCacheRepository repo,
        Class<T> clazz
    ) throws JsonProcessingException {
        // Centralized caching logic
    }
}

// Usage in AnimeService:
public AnimeDetails getAnimeById(int animeId) {
    return cacheService.getOrFetch(
        "anime:" + animeId,
        () -> animeCacheRepository.findById(animeId),
        () -> fetchFromMalApi(animeId),
        json -> objectMapper.readValue(json, AnimeDetails.class),
        7,
        animeCacheRepository,
        AnimeDetails.class
    );
}
```

---

### 3. RecommendationFacade is God Object (330 lines)
**File**: `src/main/java/com/gordonpn/anime/service/recommendation/RecommendationFacade.java`

**Problem**: The class handles too many concerns:
- Fetching user lists (lines 33-75)
- Building relationship graphs (lines 77-127)
- Component traversal/BFS (lines 104-127)
- Representative selection logic (lines 129-179)
- Recommendation extraction (lines 181-226)
- Sorting and final ranking (lines 228-268)
- Franchise rating calculation (lines 270-328)

**Impact**: 
- Violates Single Responsibility Principle
- Hard to test individual concerns (e.g., ranking vs. filtering)
- Difficult to reuse logic (e.g., graph traversal)
- 330 lines is too large for maintainability

**Suggested refactoring**: Extract existing services further:

```java
// Create GraphBuilder service:
@Service
public class AnimeGraphBuilder {
    public Map<Integer, Set<Integer>> buildRelationshipGraph(List<Integer> animeIds) { ... }
}

// Create ComponentAnalyzer service:
@Service
public class ComponentAnalyzer {
    public List<Set<Integer>> findConnectedComponents(Map<Integer, Set<Integer>> graph) { ... }
}

// Create FranchiseRepresentative service:
@Service
public class FranchiseRepresentativeSelector {
    public String selectRepresentativeTitle(Set<Integer> component, Map<Integer, AnimeDetails> details) { ... }
}

// Updated RecommendationFacade becomes orchestrator:
@Service
@RequiredArgsConstructor
public class RecommendationFacade {
    private final AnimeGraphBuilder graphBuilder;
    private final ComponentAnalyzer componentAnalyzer;
    private final FranchiseRepresentativeSelector repSelector;
    private final RankingService rankingService;
    
    public List<RecommendationDetailedResult> getRecommendations(String userName, int limit) {
        // Clean orchestration without implementation details
    }
}
```

---

### 4. Exception Handling Inconsistency
**Problem**: Some methods return null, others throw, some log, some don't

**Impact**: Unpredictable error behavior, difficult debugging

**Fix**: Define exception handling strategy, use consistent error propagation

## MEDIUM SEVERITY ISSUES (7)

### 6. RecommendationCommands Has Business Logic
**Files**: `src/main/java/com/gordonpn/anime/commands/RecommendationCommands.java`, `src/main/java/com/gordonpn/anime/commands/ListCommands.java`

**Problem**: Command classes call services directly without clear orchestration, mixing CLI concerns with business logic.

**Impact**: Difficult to test commands independently

**Fix**: Use RecommendationFacade consistently for all coordination

---

### 7. No Response Format Consistency
**Problem**: Commands use hardcoded table formatting; no unified response model

**Files**: `src/main/java/com/gordonpn/anime/commands/RecommendationCommands.java:58-79,245-265`

**Impact**: Duplicated formatting logic, inconsistent user experience

**Fix**: Create `TableFormatter` service, standardize API response DTOs in `/dto/` package

---

### 8. Long Methods in RecommendationFacade
**Problem**: 
- Lines 33-75 (user list fetch) could be extracted
- Lines 129-179 (representative selection) could be extracted

**Impact**: Method too large for easy comprehension and testing

**Fix**: Extract to dedicated helper methods or services

---

### 9. Tight Coupling in HTTP Clients
**Problem**: RestClient calls embedded in services without clear abstraction

**Files**: `src/main/java/com/gordonpn/anime/service/AnimeService.java`, `src/main/java/com/gordonpn/anime/service/UserListService.java`

**Impact**: Hard to mock/test, difficult to switch HTTP clients, implementation details leak

**Fix**: Create `MalApiClient` wrapper service with consistent error handling

---

### 10. No Input Validation at Service Boundaries
**Problem**: Services accept parameters without validation (null checks, bounds)

**Impact**: Runtime errors instead of clear validation failures, hard to trace bugs

**Fix**: Add `@Validated` annotations, custom validators (e.g., `@NotBlank`, `@Positive`)

---

### 11. RankingService Weights Not Validated
**File**: `src/main/java/com/gordonpn/anime/service/recommendation/RankingService.java:9-16`

**Problem**: Configuration weights loaded but never checked for NaN, positive values, or reasonable ranges

**Impact**: Bad configuration could silently produce weird ranking results, no visibility into actual values

**Fix**: Add @PostConstruct validation + structured logging:

```java
@Service
@Validated
public class RankingService {
    
    @Value("${recommendation.weights.occurrence:1.0}")
    @Positive(message = "Weight occurrence must be positive")
    private double weightOccurrence;
    
    @PostConstruct
    public void validateWeights() {
        log.info("Ranking weights: occurrence={}, rating={}, popularity={}",
            weightOccurrence, weightRating, weightPopularity);
        if (Double.isNaN(weightOccurrence) || Double.isNaN(weightRating) || Double.isNaN(weightPopularity)) {
            throw new ConfigurationException("Invalid ranking weights: NaN detected");
        }
    }
}
```

---

### 12. Inconsistent Exception Handling
**Problem**: Some methods return null, others throw, some log, some don't

**Impact**: Unpredictable error behavior, difficult debugging

**Fix**: Define exception handling strategy, use GlobalExceptionHandler consistently

---

## LOW SEVERITY ISSUES (5)

### 5. Package Structure Under-organized
**File structure**: `src/main/java/com/gordonpn/anime/service/recommendation/`

**Problem**: All recommendation services in one directory:
- RecommendationFacade
- RankingService
- FilteringService
- ExplanationService

**Impact**: Could become cluttered as codebase grows

**Suggested refactoring**:
```
service/
├── recommendation/
│   ├── RecommendationFacade.java
│   ├── ranking/
│   │   ├── RankingService.java
│   │   └── RankingWeightsValidator.java
│   ├── filtering/
│   │   └── FilteringService.java
│   └── explanation/
│       └── ExplanationService.java
```

---

### 6. Hardcoded Magic Numbers and Constants
**Files affected**:
- `src/main/java/com/gordonpn/anime/service/UserListService.java:23` (MAX = 1000)
- `src/main/java/com/gordonpn/anime/service/recommendation/RankingService.java:24-25` (10000.0 normalization)
- `src/main/java/com/gordonpn/anime/service/recommendation/ExplanationService.java:18` (showLimit = 3)
- `src/main/java/com/gordonpn/anime/service/recommendation/RecommendationFacade.java:232,285` (100, 10)

**Problem**: Magic numbers scattered without clear purpose or configuration

**Impact**: Hard to understand intent, difficult to adjust behavior without code changes

**Suggested refactoring**:
```java
@Component
@ConfigurationProperties(prefix = "app.constants")
@Data
public class ApplicationConstants {
    private int maxListSize = 1000;
    private int franchiseGroupLimit = 10;
    private int explanationSourceShowLimit = 3;
    private double rankingNormalizationFactor = 10000.0;
}
```

---

### 7. No API Versioning Strategy
**Files affected**: Command handling

**Problem**: CLI behavior hardcoded but no versioning strategy for future breaking changes

**Impact**: When breaking changes needed, no clear path forward

**Suggested refactoring**: Define and document CLI versioning strategy in ARCHITECTURE.md

---

### 8. Command Logic Mixing
**Files**: `src/main/java/com/gordonpn/anime/commands/RecommendationCommands.java`, `src/main/java/com/gordonpn/anime/commands/ListCommands.java`

**Problem**: Command classes mix CLI concerns with business logic

**Impact**: Difficult to test commands independently, logic duplication across commands

**Fix**: Ensure all commands delegate to services cleanly

---

### 9. String Formatting Logic in Commands
**File**: `src/main/java/com/gordonpn/anime/commands/RecommendationCommands.java:58-79,245-265`

**Problem**: Table formatting logic hardcoded in command classes (lines of string concatenation, padding calculations)

**Impact**: Duplicated if multiple commands format similar tables, commands bloated with non-business logic

**Suggested refactoring**:
```java
@Service
public class TableFormatter {
    public String formatRecommendations(List<RecommendationDetailedResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-4s | %-40s | %-6s | ...\n", "Rank", "Title", "Score"));
        // Table formatting
        return sb.toString();
    }
    
    public String formatSeasonalAnime(List<SeasonalAnimeResult> results) {
        // Seasonal formatting
    }
}

// Usage in command:
@ShellMethod
public String recommendation(...) {
    List<RecommendationDetailedResult> results = recommendationFacade.getRecommendations(userName, limit);
    return json ? objectMapper.writeValueAsString(results) : tableFormatter.formatRecommendations(results);
}
```

---

---

## ADDITIONAL REFACTORING FINDINGS (June 2026)

### 10. Models Package Organization (30+ mixed concerns)
**File**: `src/main/java/com/gordonpn/anime/models/` (30+ files)

**Problem**: The `models` directory contains 30+ files with mixed concerns:
- Domain models (MAL data structures)
- Custom exceptions (MalResourceNotFoundException, AuthCodeParsingException)
- Utility classes (RandomState, PKCEUtil)

**Impact**:
- No clear separation of concerns at package level
- Difficult to navigate and understand the role of each class
- Violates single responsibility principle

**Suggested refactoring**:
```
models/
├── domain/
│   ├── AnimeDetails.java
│   ├── Recommendation.java
│   └── UserAnimeListEntry.java
├── mal/
│   ├── Node.java
│   ├── Datum.java
│   ├── AnimeList.java
│   └── TokenResponse.java
└── exception/
    ├── MalResourceNotFoundException.java
    └── AuthCodeParsingException.java

# Move utilities to util/ package:
util/
├── RandomState.java
├── PKCEUtil.java
├── Browser.java
└── CallbackHandler.java
```

---

### 11. RecommendationFacade Size Growing (396 lines, approaching cognitive load limit)
**File**: `src/main/java/com/gordonpn/anime/service/recommendation/RecommendationFacade.java`

**Problem**: Facade is now 396 lines (measured June 2026), which exceeds maintainability threshold.

**Impact**:
- Difficult to read and understand all responsibilities in one sitting
- Hard to add new features without breaking existing logic
- Slower unit test execution (all dependencies must be mocked)
- Higher risk of regression when modifying ranking/filtering logic

**Suggested refactoring** (extends Issue #2):
- Extract franchise grouping logic to `FranchiseGroupingService`
- Move recommendation filtering logic to dedicated `RecommendationFilterService`
- Move scoring calculation to `RecommendationScoringService`
- Keep facade as orchestrator, limit to 150 lines maximum

---

### 12. Missing Input Validation at Service Boundaries
**Problem**: Services accept parameters without validation (null checks, bounds)

**Impact**: Runtime errors instead of clear validation failures, hard to trace bugs

**Fix**: Add `@Validated` annotations and custom validators

---

### 13. RankingService Weights Not Validated
**File**: `src/main/java/com/gordonpn/anime/service/recommendation/RankingService.java:9-16`

**Problem**: Configuration weights loaded but never checked for NaN, positive values, or reasonable ranges

**Impact**: Bad configuration could silently produce weird ranking results, no visibility into actual values

**Fix**: Add @PostConstruct validation + structured logging:

```java
@Service
@Validated
public class RankingService {
    
    @Value("${recommendation.weights.occurrence:1.0}")
    @Positive(message = "Weight occurrence must be positive")
    private double weightOccurrence;
    
    @PostConstruct
    public void validateWeights() {
        log.info("Ranking weights: occurrence={}, rating={}, popularity={}",
            weightOccurrence, weightRating, weightPopularity);
        if (Double.isNaN(weightOccurrence) || Double.isNaN(weightRating) || Double.isNaN(weightPopularity)) {
            throw new ConfigurationException("Invalid ranking weights: NaN detected");
        }
    }
}
```

---

### 14. Missing HTTP Client Abstraction Layer (MAL API)
**Files**: `AnimeService.java`, `UserListService.java`, `RecommendationService.java`, `TokenExchangeService.java`

**Problem**: Multiple services directly use injected `RestClient` instances without abstraction.

**Impact**:
- Hard to change HTTP client implementation
- Inconsistent error handling across services
- Testing requires mocking RestClient in each service test

**Suggested refactoring**:
```java
// Create MalApiClient abstraction:
@Service
public class MalApiClient {
    private final RestClient apiKeyClient;
    private final String baseUrl = "https://api.myanimelist.net/v2";
    
    public <T> T getAnimeById(int animeId, Class<T> responseType) {
        return get("/anime/{id}", responseType, animeId);
    }
    
    public <T> List<T> getUserList(String userName, String status, Class<T> responseType) {
        return getList("/users/{userName}/animelist", responseType, userName);
    }
    
    private <T> T get(String endpoint, Class<T> responseType, Object... pathVars) {
        try {
            return apiKeyClient.get()
                .uri(baseUrl + endpoint, pathVars)
                .retrieve()
                .body(responseType);
        } catch (HttpClientErrorException e) {
            throw new MalApiException("API call failed", e);
        }
    }
}

// Services become simpler:
@Service
public class AnimeService {
    private final MalApiClient malApiClient;
    
    public AnimeDetails getAnimeById(int animeId) {
        return malApiClient.getAnimeById(animeId, AnimeDetails.class);
    }
}
```

---

### 15. Test Coverage Gap (16 tests for 3548 LOC)
**Problem**: Test directory contains only 16 test files, covering ~0.5% of codebase (estimated 3548 LOC).

**Critical gaps**:
- No tests for `RecommendationFacade` (core logic)
- No tests for recommendation ranking algorithm
- No tests for key CLI commands
- Manual testing likely carries all the risk

**Impact**:
- High regression risk on refactoring
- No confidence in ranking behavior changes
- Difficult to onboard new contributors (unclear expected behavior)

**Suggested refactoring** (multi-phase):
1. Add unit tests for `RankingService` (scoring logic)
2. Add unit tests for `FilteringService` (filter logic)
3. Add integration tests for `RecommendationFacade`
4. Add command-level tests for critical CLI paths

---

### 16. Configuration Management Fragmentation
**Problem**: Configuration spread across multiple locations:
- `application.properties` (Spring config)
- `AnimeClientConfig.java` (RestClient bean config)
- `RankingService.java` (@Value annotations)
- `MalRetryInterceptor.java` (retry config hardcoded)
- `BackgroundFetchService.java` (ONE_HOUR_IN_MILLIS constant)

**Impact**:
- Hard to understand all configuration options
- No centralized config schema
- Difficult to manage environment-specific configs

**Suggested refactoring**:
```yaml
# application.properties
app:
  constants:
    max-list-size: 1000
    franchise-group-limit: 10

mal:
  api:
    base-url: https://api.myanimelist.net/v2
    timeout-ms: 10000
  retry:
    max-attempts: 3
    initial-backoff-ms: 500
    backoff-multiplier: 2.0
  cache:
    anime-ttl-days: 30
    user-list-ttl-days: 1
    seasonal-ttl-days: 1

recommendation:
  ranking:
    weights:
      occurrence: 1.0
      rating: 0.5
      popularity: 0.3
```

---

## ARCHITECTURAL ALIGNMENT vs. ARCHITECTURE.MD

### ✅ Implemented Well
- ✓ CLI (Spring Shell) commands
- ✓ Service layer exists and properly organized
- ✓ Recommendation facade pattern
- ✓ Recommendation sub-services (Filtering, Ranking, Explanation)
- ✓ Background scheduling

### ⚠️ Partially Implemented / Missing
1. **Service Decomposition**: RecommendationFacade too large (needs decomposition)
   - Missing: FranchiseGroupingService, RecommendationFilterService, RecommendationScoringService
2. **Configuration**: Credentials not fully Spring-externalized
   - Should use `@ConfigurationProperties` throughout
3. **Error Handling**: Inconsistent exception handling and propagation
4. **HTTP Client**: RestClient used directly in services without abstraction

### ❌ Not Implemented
- HTTP Client abstraction layer (MalApiClient)
- Comprehensive test coverage for facade and services
- Structured logging with context
- Full input validation at service boundaries

---

## RECOMMENDED REFACTORING ORDER

Priority should be **High** issues first (blocks maintainability), then **Medium** (improves code quality), then **Low** (technical debt).

### **Phase 1: Exception Handling & Error Encapsulation** (2-3 days)
- Issue #1: Create domain exceptions
- Issue #4: Define exception handling strategy
- Impact: Improves testability and CLI error messages

### **Phase 2: Models Package Organization** (1-2 days)
- Issue #10: Reorganize models package
- Impact: Better code navigation and clear separation of concerns

### **Phase 3: HTTP Client Abstraction** (2-3 days)
- Issue #14: Create MalApiClient wrapper service
- Impact: Easier to test, consistent error handling, easier to change implementation

### **Phase 4: Decompose RecommendationFacade & Add Validation** (4-5 days)
- Issue #2: Extract GraphBuilder, ComponentAnalyzer, FranchiseRepresentativeSelector
- Issue #11: Extract scoring and filtering services
- Issue #12: Add input validation at service boundaries
- Impact: Improves testability, reduces cognitive load

### **Phase 5: Centralize Cache Logic & Configuration** (2-3 days)
- Issue #2: Create CacheService with generic getOrFetch()
- Issue #16: Consolidate configuration management
- Impact: Reduces duplication, consistent TTL strategy, centralized config

### **Phase 6: Test Coverage & Validation** (3-4 days)
- Issue #13: Validate RankingService weights
- Issue #15: Improve test coverage (unit + integration)
- Impact: Improves reliability and maintainability

### **Phase 7: Code Quality & Structure** (2-3 days)
- Issue #8: Consolidate command/service separation
- Issue #9: Extract TableFormatter service
- Issue #5-7: Ensure all magic numbers are in ApplicationConstants
- Impact: Improves navigability and consistency

**Total effort**: ~15-20 days for full architectural compliance + CLI-focused quality

---

## Next Steps

1. **Prioritize High issues first** (blocks maintainability)
2. **Each refactoring should be a single atomic commit** following conventional commits
3. **Update ARCHITECTURE.md as patterns emerge** (e.g., new services, new packages)
4. **Add unit/integration tests alongside refactoring work**
5. **Run linters/formatters after each phase** to maintain code quality
6. **Review against this document** periodically as new code is written
