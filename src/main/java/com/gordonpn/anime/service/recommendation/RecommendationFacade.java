package com.gordonpn.anime.service.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gordonpn.anime.entity.AnimeCacheEntity;
import com.gordonpn.anime.entity.UserAnimeListEntity;
import com.gordonpn.anime.models.AnimeDetails;
import com.gordonpn.anime.models.AnimeList;
import com.gordonpn.anime.models.Datum;
import com.gordonpn.anime.models.ListStatusType;
import com.gordonpn.anime.models.Node;
import com.gordonpn.anime.models.Recommendation;
import com.gordonpn.anime.models.RelatedAnime;
import com.gordonpn.anime.models.dto.RecommendationDetailedResult;
import com.gordonpn.anime.models.exception.MalResourceNotFoundException;
import com.gordonpn.anime.models.userlist.Status;
import com.gordonpn.anime.repository.AnimeCacheRepository;
import com.gordonpn.anime.repository.UserAnimeListRepository;
import com.gordonpn.anime.service.AnimeService;
import com.gordonpn.anime.service.UserListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationFacade {

    private final UserAnimeListRepository userAnimeListRepository;
    private final AnimeCacheRepository animeCacheRepository;
    private final UserListService userListService;
    private final AnimeService animeService;
    private final FilteringService filteringService;
    private final RankingService rankingService;
    private final ExplanationService explanationService;

    public List<RecommendationDetailedResult> getRecommendations(String userName, int limit) {
        return getRecommendations(userName, limit, null, null, null, null, false, true);
    }

    public List<RecommendationDetailedResult> getRecommendations(String userName, int limit, Integer minYear, Integer maxYear) {
        return getRecommendations(userName, limit, minYear, maxYear, null, null, false, true);
    }

    public List<RecommendationDetailedResult> getRecommendations(String userName, int limit, Integer minYear, Integer maxYear, Integer decayMonths) {
        return getRecommendations(userName, limit, minYear, maxYear, decayMonths, null, false, true);
    }

    public List<RecommendationDetailedResult> getRecommendations(String userName, int limit, Integer minYear, Integer maxYear, Integer decayMonths, Double freshnessBoost) {
        return getRecommendations(userName, limit, minYear, maxYear, decayMonths, freshnessBoost, false, true);
    }

    public List<RecommendationDetailedResult> getRecommendations(
            String userName, 
            int limit, 
            Integer minYear, 
            Integer maxYear, 
            Integer decayMonths, 
            Double freshnessBoost, 
            Boolean excludeWatchedFranchises, 
            Boolean groupCandidates) {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("userName must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (minYear != null && minYear <= 0) {
            throw new IllegalArgumentException("minYear must be positive when provided");
        }
        if (maxYear != null && maxYear <= 0) {
            throw new IllegalArgumentException("maxYear must be positive when provided");
        }
        if (minYear != null && maxYear != null && minYear > maxYear) {
            throw new IllegalArgumentException("minYear must not exceed maxYear");
        }
        if (decayMonths != null && decayMonths < 0) {
            throw new IllegalArgumentException("decayMonths must be nonnegative");
        }
        if (freshnessBoost != null && (!Double.isFinite(freshnessBoost) || freshnessBoost < 0)) {
            throw new IllegalArgumentException("freshnessBoost must be finite and nonnegative");
        }

        List<Integer> completedIds = new ArrayList<>();
        Map<Integer, String> completedIdToTitleMap = new HashMap<>();
        Map<Integer, Integer> completedIdToUserScoreMap = new HashMap<>();
        Map<Integer, java.time.LocalDateTime> completedIdToCompletionDateMap = new HashMap<>();
        Set<Integer> droppedIds = new HashSet<>();

        // 1. Gather completed & dropped lists
        if ("@me".equalsIgnoreCase(userName)) {
            log.info("Retrieving list from local SQLite database for @me");
            List<UserAnimeListEntity> completedLocal = userAnimeListRepository.findByStatus(ListStatusType.COMPLETED);
            List<UserAnimeListEntity> droppedLocal = userAnimeListRepository.findByStatus(ListStatusType.DROPPED);

            for (UserAnimeListEntity entity : completedLocal) {
                if (entity.getAnimeId() == null || entity.getAnimeId() <= 0) {
                    log.warn("Skipping local completed entry with invalid anime ID: {}", entity.getAnimeId());
                    continue;
                }
                completedIds.add(entity.getAnimeId());
                String title = animeCacheRepository.findById(entity.getAnimeId())
                        .map(AnimeCacheEntity::getTitle)
                        .orElse("Unknown Anime (ID: " + entity.getAnimeId() + ")");
                completedIdToTitleMap.put(entity.getAnimeId(), title);
                completedIdToUserScoreMap.put(entity.getAnimeId(), entity.getUserScore());
                completedIdToCompletionDateMap.put(entity.getAnimeId(), entity.getUpdatedAt());
            }
            for (UserAnimeListEntity entity : droppedLocal) {
                droppedIds.add(entity.getAnimeId());
            }
        } else {
            log.info("Fetching list live from MAL API for user: {}", userName);
            AnimeList completedList = userListService.getListByUserName(userName, Status.COMPLETED);
            AnimeList droppedList = userListService.getListByUserName(userName, Status.DROPPED);

            if (completedList != null && completedList.getData() != null) {
                for (Datum datum : completedList.getData()) {
                    Integer id = datum.getNode().getId();
                    completedIds.add(id);
                    completedIdToTitleMap.put(id, datum.getNode().getTitle());
                    if (datum.getListStatus() != null) {
                        completedIdToUserScoreMap.put(id, datum.getListStatus().getScore());
                        String updatedAtStr = datum.getListStatus().getUpdatedAt();
                        if (updatedAtStr != null && !updatedAtStr.isEmpty()) {
                            try {
                                completedIdToCompletionDateMap.put(id, java.time.OffsetDateTime.parse(updatedAtStr).toLocalDateTime());
                            } catch (Exception e) {
                                log.warn("Failed to parse completion date '{}' for anime ID {}: {}", updatedAtStr, id, e.getMessage());
                            }
                        }
                    }
                }
            }
            if (droppedList != null && droppedList.getData() != null) {
                for (Datum datum : droppedList.getData()) {
                    droppedIds.add(datum.getNode().getId());
                }
            }
        }

        // Build graph of relations among completed shows to group different seasons/OVAs
        Map<Integer, Set<Integer>> graph = new HashMap<>();
        Map<Integer, AnimeDetails> detailsMap = new HashMap<>();
        Set<Integer> completedIdsSet = new HashSet<>(completedIds);

        for (Integer completedId : completedIds) {
            try {
                AnimeDetails details = animeService.getAnimeById(completedId);
                detailsMap.put(completedId, details);
                graph.putIfAbsent(completedId, new HashSet<>());

                List<RelatedAnime> related = details.getRelatedAnime();
                if (related != null) {
                    for (RelatedAnime rel : related) {
                        if (rel.getNode() != null && rel.getNode().getId() != null) {
                            Integer relId = rel.getNode().getId();
                            graph.putIfAbsent(relId, new HashSet<>());
                            graph.get(completedId).add(relId);
                            graph.get(relId).add(completedId);
                        }
                    }
                }
            } catch (MalResourceNotFoundException e) {
                log.warn("Completed anime ID {} not found on MyAnimeList: {}", completedId, e.getMessage());
            } catch (Exception e) {
                log.error("Failed to fetch details/relations for completed anime ID: {}", completedId, e);
                throw e;
            }
        }

        // Run connected components BFS to partition distinct franchises
        Set<Integer> visited = new HashSet<>();
        List<Set<Integer>> components = new ArrayList<>();

        for (Integer node : graph.keySet()) {
            if (!visited.contains(node)) {
                Set<Integer> component = new HashSet<>();
                Queue<Integer> queue = new LinkedList<>();
                queue.add(node);
                visited.add(node);

                while (!queue.isEmpty()) {
                    Integer curr = queue.poll();
                    component.add(curr);
                    for (Integer neighbor : graph.getOrDefault(curr, Collections.emptySet())) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
                components.add(component);
            }
        }

        // Map completedId to component representative title and average user score
        Map<Integer, String> completedIdToGroupTitleMap = new HashMap<>();
        Map<Integer, Double> completedIdToGroupUserScoreMap = new HashMap<>();

        for (Set<Integer> component : components) {
            List<Integer> completedInComponent = new ArrayList<>();
            for (Integer id : component) {
                if (completedIdsSet.contains(id)) {
                    completedInComponent.add(id);
                }
            }
            if (completedInComponent.isEmpty()) {
                continue;
            }

            // Calculate average user score for completed shows in this component
            double sumUserScore = 0.0;
            int countUserScore = 0;
            for (Integer id : completedInComponent) {
                Integer score = completedIdToUserScoreMap.get(id);
                if (score != null && score > 0) {
                    sumUserScore += score;
                    countUserScore++;
                }
            }
            Double avgUserScore = countUserScore > 0 ? (sumUserScore / countUserScore) : null;

            // Find representative title (minimum popularity rank in the component among those with details)
            String representativeTitle = null;
            Integer minPopularity = Integer.MAX_VALUE;

            for (Integer id : component) {
                AnimeDetails det = detailsMap.get(id);
                if (det != null && det.getPopularity() != null && det.getPopularity() < minPopularity) {
                    minPopularity = det.getPopularity();
                    representativeTitle = det.getDisplayTitle();
                }
            }

            if (representativeTitle == null) {
                Integer firstId = completedInComponent.get(0);
                representativeTitle = completedIdToTitleMap.getOrDefault(firstId, "Unknown");
            }

            for (Integer id : completedInComponent) {
                completedIdToGroupTitleMap.put(id, representativeTitle);
                if (avgUserScore != null) {
                    completedIdToGroupUserScoreMap.put(id, avgUserScore);
                }
            }
        }

        // 2. Fetch completed anime details & extract candidate recommendations
        Map<Integer, Double> recOccurrenceMap = new HashMap<>();
        Map<Integer, List<String>> recommendationSourcesMap = new HashMap<>();

        for (Integer completedId : completedIds) {
            AnimeDetails details = detailsMap.get(completedId);
            if (details == null) {
                continue;
            }

            // User rating multiplier based on the group average score
            Double groupUserScore = completedIdToGroupUserScoreMap.get(completedId);
            double userRatingMultiplier = 1.0;
            if (groupUserScore != null && groupUserScore > 0) {
                userRatingMultiplier = groupUserScore / 7.0; // 7.0 is default average rating
            }

            String groupTitle = completedIdToGroupTitleMap.getOrDefault(completedId, completedIdToTitleMap.getOrDefault(completedId, "Unknown"));

            List<Recommendation> recommendations = details.getRecommendations();
            if (recommendations == null) {
                continue;
            }

            for (Recommendation recommendation : recommendations) {
                Node recNode = recommendation.getNode();
                if (recNode == null || recNode.getId() == null) {
                    continue;
                }
                Integer recNodeId = recNode.getId();

                if (filteringService.shouldInclude(recNodeId, completedIdsSet, droppedIds)) {
                    if (Boolean.TRUE.equals(excludeWatchedFranchises) && graph.containsKey(recNodeId)) {
                        continue;
                    }
                    int numRecs = recommendation.getNumRecommendations() != null ? recommendation.getNumRecommendations() : 1;
                    
                    // Apply temporal decay if configured
                    double decayWeight = 1.0;
                    if (decayMonths != null && decayMonths > 0) {
                        java.time.LocalDateTime completionDate = completedIdToCompletionDateMap.get(completedId);
                        if (completionDate != null) {
                            long days = java.time.temporal.ChronoUnit.DAYS.between(completionDate, java.time.LocalDateTime.now());
                            double tMonths = Math.max(0.0, days / 30.4375);
                            decayWeight = Math.pow(2.0, -tMonths / decayMonths);
                        } else {
                            decayWeight = 1.0; // default to no decay if date is missing
                        }
                    }

                    // Weighted recommendation count
                    double weightedRec = numRecs * userRatingMultiplier * decayWeight;
                    
                    recOccurrenceMap.merge(recNodeId, weightedRec, Double::sum);
                    
                    List<String> sources = recommendationSourcesMap.computeIfAbsent(recNodeId, _ -> new ArrayList<>());
                    if (!sources.contains(groupTitle)) {
                        sources.add(groupTitle);
                    }
                }
            }
        }

        // 3. Sort candidates by weighted recommendation count
        List<Integer> topCandidateIds = recOccurrenceMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .map(Map.Entry::getKey)
                .limit(Math.max(100, limit * 2))
                .collect(Collectors.toList());

        List<RecommendationDetailedResult> detailedResults = new ArrayList<>();
        Map<Integer, AnimeDetails> candidateDetailsMap = new HashMap<>();

        // 4. Fetch details for top candidates and rank them
        for (Integer candidateId : topCandidateIds) {
            try {
                AnimeDetails details = animeService.getAnimeById(candidateId);
                candidateDetailsMap.put(candidateId, details);

                // Filter by release era if minYear or maxYear is specified
                if (minYear != null || maxYear != null) {
                    Integer startYear = details.getStartYear();
                    if (startYear == null) {
                        continue; // Skip if year is unknown but range filter is active
                    }
                    if (minYear != null && startYear < minYear) {
                        continue;
                    }
                    if (maxYear != null && startYear > maxYear) {
                        continue;
                    }
                }

                double occurrence = recOccurrenceMap.getOrDefault(candidateId, 0.0);
                
                // Calculate average candidate rating across its franchise seasons/OVAs/etc.
                Double overallRating = calculateOverallFranchiseRating(details);
                
                double score = rankingService.calculateScore(occurrence, overallRating, details.getPopularity());
                if (freshnessBoost != null && freshnessBoost > 0) {
                    Integer startYear = details.getStartYear();
                    if (startYear != null) {
                        double yearsSinceRelease = Math.max(0.0, java.time.Year.now().getValue() - startYear);
                        double boostFactor = Math.exp(-freshnessBoost * yearsSinceRelease);
                        score = score * boostFactor;
                    }
                }
                String explanation = explanationService.generateExplanation(occurrence, recommendationSourcesMap.get(candidateId));

                detailedResults.add(RecommendationDetailedResult.builder()
                        .id(candidateId)
                        .title(details.getDisplayTitle())
                        .score(score)
                        .occurrence(occurrence)
                        .meanRating(overallRating)
                        .popularity(details.getPopularity())
                        .explanation(explanation)
                        .build());
            } catch (MalResourceNotFoundException e) {
                log.warn("Candidate anime ID {} not found on MyAnimeList, skipping: {}", candidateId, e.getMessage());
            } catch (Exception e) {
                log.error("Failed to rank candidate ID: {}", candidateId, e);
                throw e;
            }
        }

        if (groupCandidates == null || groupCandidates) {
            Map<Integer, Set<Integer>> candidateGraph = new HashMap<>();
            Map<Integer, RecommendationDetailedResult> candidateResultMap = new HashMap<>();

            for (RecommendationDetailedResult res : detailedResults) {
                Integer id = res.getId();
                candidateResultMap.put(id, res);
                candidateGraph.putIfAbsent(id, new HashSet<>());
                
                AnimeDetails details = candidateDetailsMap.get(id);
                if (details != null) {
                    List<RelatedAnime> related = details.getRelatedAnime();
                    if (related != null) {
                        for (RelatedAnime rel : related) {
                            if (rel.getNode() != null && rel.getNode().getId() != null) {
                                Integer relId = rel.getNode().getId();
                                candidateGraph.putIfAbsent(relId, new HashSet<>());
                                candidateGraph.get(id).add(relId);
                                candidateGraph.get(relId).add(id);
                            }
                        }
                    }
                }
            }

            Set<Integer> visitedCandidates = new HashSet<>();
            List<Set<Integer>> candidateComponents = new ArrayList<>();

            for (Integer node : candidateGraph.keySet()) {
                if (!visitedCandidates.contains(node)) {
                    Set<Integer> component = new HashSet<>();
                    Queue<Integer> queue = new LinkedList<>();
                    queue.add(node);
                    visitedCandidates.add(node);

                    while (!queue.isEmpty()) {
                        Integer curr = queue.poll();
                        component.add(curr);
                        for (Integer neighbor : candidateGraph.getOrDefault(curr, Collections.emptySet())) {
                            if (!visitedCandidates.contains(neighbor)) {
                                visitedCandidates.add(neighbor);
                                queue.add(neighbor);
                            }
                        }
                    }
                    candidateComponents.add(component);
                }
            }

            List<RecommendationDetailedResult> consolidatedResults = new ArrayList<>();

            for (Set<Integer> component : candidateComponents) {
                List<Integer> candidatesInComponent = new ArrayList<>();
                for (Integer id : component) {
                    if (candidateResultMap.containsKey(id)) {
                        candidatesInComponent.add(id);
                    }
                }

                if (candidatesInComponent.isEmpty()) {
                    continue;
                }

                AnimeDetails representativeDetails = null;
                for (Integer id : component) {
                    AnimeDetails d = null;
                    if (candidateDetailsMap.containsKey(id)) {
                        d = candidateDetailsMap.get(id);
                    } else {
                        try {
                            d = animeService.getAnimeById(id);
                        } catch (Exception e) {
                            log.debug("Skipping relation graph bridge detail fetch failure for candidate ID: {}", id);
                        }
                    }
                    if (d == null) continue;
                    if (representativeDetails == null) {
                        representativeDetails = d;
                    } else {
                        Integer pRep = representativeDetails.getPopularity();
                        Integer pD = d.getPopularity();
                        if (pD != null && (pRep == null || pD < pRep)) {
                            representativeDetails = d;
                        }
                    }
                }

                Integer repId = representativeDetails != null ? representativeDetails.getId() : candidatesInComponent.get(0);
                String repTitle = representativeDetails != null ? representativeDetails.getDisplayTitle() : "Unknown Series";
                Integer repPopularity = representativeDetails != null ? representativeDetails.getPopularity() : null;

                double totalOccurrence = 0.0;
                List<String> pooledSources = new ArrayList<>();
                Double repRating = representativeDetails != null ? representativeDetails.getMean() : null;

                for (Integer id : candidatesInComponent) {
                    RecommendationDetailedResult res = candidateResultMap.get(id);
                    if (res != null) {
                        totalOccurrence += res.getOccurrence();
                    }
                    List<String> sources = recommendationSourcesMap.get(id);
                    if (sources != null) {
                        for (String src : sources) {
                            if (!pooledSources.contains(src)) {
                                pooledSources.add(src);
                            }
                        }
                    }
                }

                if (repRating == null) {
                    double sumRating = 0.0;
                    int countRating = 0;
                    for (Integer id : candidatesInComponent) {
                        RecommendationDetailedResult res = candidateResultMap.get(id);
                        if (res != null && res.getMeanRating() != null) {
                            sumRating += res.getMeanRating();
                            countRating++;
                        }
                    }
                    repRating = countRating > 0 ? (sumRating / countRating) : null;
                }

                Double overallFranchiseRating = calculateOverallFranchiseRating(representativeDetails);
                if (overallFranchiseRating == null) {
                    overallFranchiseRating = repRating;
                }

                double consolidatedScore = rankingService.calculateScore(totalOccurrence, overallFranchiseRating, repPopularity);
                if (freshnessBoost != null && freshnessBoost > 0) {
                    if (representativeDetails != null) {
                        Integer startYear = representativeDetails.getStartYear();
                        if (startYear != null) {
                            double yearsSinceRelease = Math.max(0.0, java.time.Year.now().getValue() - startYear);
                            double boostFactor = Math.exp(-freshnessBoost * yearsSinceRelease);
                            consolidatedScore = consolidatedScore * boostFactor;
                        }
                    }
                }

                String consolidatedExplanation = explanationService.generateExplanation(totalOccurrence, pooledSources);

                consolidatedResults.add(RecommendationDetailedResult.builder()
                        .id(repId)
                        .title(repTitle)
                        .score(consolidatedScore)
                        .occurrence(totalOccurrence)
                        .meanRating(overallFranchiseRating)
                        .popularity(repPopularity)
                        .explanation(consolidatedExplanation)
                        .build());
            }

            detailedResults = consolidatedResults;
        }

        // 5. Final sorting by score descending and return
        return detailedResults.stream()
                .sorted((r1, r2) -> Double.compare(r2.getScore(), r1.getScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private Double calculateOverallFranchiseRating(AnimeDetails candidateDetails) {
        if (candidateDetails == null) {
            return null;
        }

        List<RelatedAnime> related = candidateDetails.getRelatedAnime();
        if (related == null || related.isEmpty()) {
            return candidateDetails.getMean();
        }

        List<Double> ratings = new ArrayList<>();
        if (candidateDetails.getMean() != null && candidateDetails.getMean() > 0) {
            ratings.add(candidateDetails.getMean());
        }

        int lookupLimit = 10;
        int lookupCount = 0;

        for (RelatedAnime rel : related) {
            if (rel.getNode() != null && rel.getNode().getId() != null) {
                if (lookupCount >= lookupLimit) {
                    break;
                }

                String relType = rel.getRelationType();
                boolean isFranchiseRelation = relType == null ||
                        relType.equalsIgnoreCase("prequel") ||
                        relType.equalsIgnoreCase("sequel") ||
                        relType.equalsIgnoreCase("parent_story") ||
                        relType.equalsIgnoreCase("full_story") ||
                        relType.equalsIgnoreCase("alternative_setting") ||
                        relType.equalsIgnoreCase("alternative_version");

                if (!isFranchiseRelation) {
                    continue;
                }

                try {
                    AnimeDetails relDetails = animeService.getAnimeById(rel.getNode().getId());
                    if (relDetails != null && relDetails.getMean() != null && relDetails.getMean() > 0) {
                        ratings.add(relDetails.getMean());
                    }
                    lookupCount++;
                } catch (MalResourceNotFoundException e) {
                    log.warn("Failed to find related anime ID {} on MyAnimeList for franchise rating calculation: {}", rel.getNode().getId(), e.getMessage());
                } catch (Exception e) {
                    log.error("Failed to fetch related anime ID {} for franchise rating calculation: {}", rel.getNode().getId(), e.getMessage(), e);
                    throw e;
                }
            }
        }

        if (ratings.isEmpty()) {
            return null;
        }

        double sum = 0.0;
        for (Double r : ratings) {
            sum += r;
        }
        return sum / ratings.size();
    }
}
