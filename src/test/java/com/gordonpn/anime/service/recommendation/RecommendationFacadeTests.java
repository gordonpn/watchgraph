package com.gordonpn.anime.service.recommendation;

import com.gordonpn.anime.entity.UserAnimeListEntity;
import com.gordonpn.anime.models.*;
import com.gordonpn.anime.models.dto.RecommendationDetailedResult;
import com.gordonpn.anime.models.exception.MalResourceNotFoundException;
import com.gordonpn.anime.models.userlist.Status;
import com.gordonpn.anime.repository.AnimeCacheRepository;
import com.gordonpn.anime.repository.UserAnimeListRepository;
import com.gordonpn.anime.service.AnimeService;
import com.gordonpn.anime.service.UserListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RecommendationFacadeTests {

    @Mock private UserAnimeListRepository userAnimeListRepository;
    @Mock private AnimeCacheRepository animeCacheRepository;
    @Mock private UserListService userListService;
    @Mock private AnimeService animeService;
    @Mock private FilteringService filteringService;
    @Mock private RankingService rankingService;
    @Mock private ExplanationService explanationService;

    private RecommendationFacade recommendationFacade;

    @BeforeEach
    public void setUp() {
        recommendationFacade = new RecommendationFacade(
                userAnimeListRepository,
                animeCacheRepository,
                userListService,
                animeService,
                filteringService,
                rankingService,
                explanationService
        );
    }

    private UserAnimeListEntity localEntry(Integer animeId) {
        UserAnimeListEntity entity = new UserAnimeListEntity();
        entity.setAnimeId(animeId);
        entity.setStatus(ListStatusType.COMPLETED);
        return entity;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testGetRecommendationsLocal(boolean includeInvalidIds) throws Exception {
        // Prepare local list for @me
        UserAnimeListEntity completedEntity = new UserAnimeListEntity();
        completedEntity.setAnimeId(101);
        completedEntity.setStatus(ListStatusType.COMPLETED);

        when(userAnimeListRepository.findByStatus(ListStatusType.COMPLETED))
                .thenReturn(includeInvalidIds
                        ? java.util.Arrays.asList(localEntry(0), localEntry(-1), localEntry(null), completedEntity)
                        : List.of(completedEntity));
        when(userAnimeListRepository.findByStatus(ListStatusType.DROPPED))
                .thenReturn(Collections.emptyList());

        // Match the public detail service's strict invalid-ID contract.
        lenient().doThrow(new IllegalArgumentException("animeId must be positive"))
                .when(animeService).getAnimeById(intThat(id -> id <= 0));

        // Mock details lookup for completed anime
        AnimeDetails completedDetails = new AnimeDetails();
        completedDetails.setId(101);
        completedDetails.setTitle("Completed Show");
        
        Recommendation rec = new Recommendation();
        rec.setNumRecommendations(5);
        Node recNode = new Node();
        recNode.setId(202);
        recNode.setTitle("Recommended Show");
        rec.setNode(recNode);
        completedDetails.setRecommendations(List.of(rec));

        when(animeService.getAnimeById(101)).thenReturn(completedDetails);

        // Mock filtering
        when(filteringService.shouldInclude(eq(202), anySet(), anySet())).thenReturn(true);

        // Mock details lookup for recommended candidate
        AnimeDetails recDetails = new AnimeDetails();
        recDetails.setId(202);
        recDetails.setTitle("Recommended Show");
        recDetails.setMean(8.5);
        recDetails.setPopularity(500);

        when(animeService.getAnimeById(202)).thenReturn(recDetails);

        // Mock ranking & explanation
        when(rankingService.calculateScore(anyDouble(), any(), any())).thenReturn(9.5);
        when(explanationService.generateExplanation(anyDouble(), anyList())).thenReturn("Because you liked Completed Show");

        // Execute
        List<RecommendationDetailedResult> results = recommendationFacade.getRecommendations("@me", 10);

        assertNotNull(results);
        assertEquals(1, results.size());
        verify(animeService, never()).getAnimeById(intThat(id -> id <= 0));
        verify(animeCacheRepository, never()).findById(argThat(id -> id == null || id <= 0));
        assertEquals(9.5, results.get(0).getScore());
        assertEquals("Because you liked Completed Show", results.get(0).getExplanation());
    }

    @Test
    public void testCompletedGroupingAndScoring() throws Exception {
        // Setup two completed shows: 101 (rated 9) and 102 (rated 7)
        // They are related (part of same component)
        UserAnimeListEntity c1 = new UserAnimeListEntity();
        c1.setAnimeId(101);
        c1.setStatus(ListStatusType.COMPLETED);
        c1.setUserScore(9);

        UserAnimeListEntity c2 = new UserAnimeListEntity();
        c2.setAnimeId(102);
        c2.setStatus(ListStatusType.COMPLETED);
        c2.setUserScore(7);

        when(userAnimeListRepository.findByStatus(ListStatusType.COMPLETED))
                .thenReturn(List.of(c1, c2));
        when(userAnimeListRepository.findByStatus(ListStatusType.DROPPED))
                .thenReturn(Collections.emptyList());

        // Mock details lookup for 101 (AOT Part 1)
        AnimeDetails details1 = new AnimeDetails();
        details1.setId(101);
        details1.setTitle("AOT Part 1");
        details1.setPopularity(200);
        
        RelatedAnime relTo102 = new RelatedAnime();
        Node n102 = new Node();
        n102.setId(102);
        relTo102.setNode(n102);
        details1.setRelatedAnime(List.of(relTo102));

        Recommendation rec = new Recommendation();
        rec.setNumRecommendations(4);
        Node recNode = new Node();
        recNode.setId(303);
        recNode.setTitle("Recommended Show");
        rec.setNode(recNode);
        details1.setRecommendations(List.of(rec));

        // Mock details lookup for 102 (AOT Part 2)
        AnimeDetails details2 = new AnimeDetails();
        details2.setId(102);
        details2.setTitle("AOT Part 2");
        details2.setPopularity(100); // More popular than part 1

        RelatedAnime relTo101 = new RelatedAnime();
        Node n101 = new Node();
        n101.setId(101);
        relTo101.setNode(n101);
        details2.setRelatedAnime(List.of(relTo101));
        details2.setRecommendations(Collections.emptyList());

        when(animeService.getAnimeById(101)).thenReturn(details1);
        when(animeService.getAnimeById(102)).thenReturn(details2);

        // Mock filtering
        when(filteringService.shouldInclude(eq(303), anySet(), anySet())).thenReturn(true);

        // Mock details lookup for recommended candidate 303
        AnimeDetails recDetails = new AnimeDetails();
        recDetails.setId(303);
        recDetails.setTitle("Recommended Show");
        recDetails.setMean(8.0);
        recDetails.setPopularity(500);

        // 303 has a related prequel 304 with rating 9.0
        RelatedAnime relTo304 = new RelatedAnime();
        relTo304.setRelationType("prequel");
        Node n304 = new Node();
        n304.setId(304);
        relTo304.setNode(n304);
        recDetails.setRelatedAnime(List.of(relTo304));

        AnimeDetails prequelDetails = new AnimeDetails();
        prequelDetails.setId(304);
        prequelDetails.setMean(9.0);

        when(animeService.getAnimeById(303)).thenReturn(recDetails);
        when(animeService.getAnimeById(304)).thenReturn(prequelDetails);

        when(rankingService.calculateScore(anyDouble(), any(), any())).thenReturn(12.0);
        when(explanationService.generateExplanation(anyDouble(), anyList())).thenReturn("Explained!");

        // Execute
        List<RecommendationDetailedResult> results = recommendationFacade.getRecommendations("@me", 10);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Recommended Show", results.get(0).getTitle());
        assertEquals(8.5, results.get(0).getMeanRating(), 0.01); // franchise-wide average: (8.0 + 9.0) / 2 = 8.5
        assertEquals(4.5714, results.get(0).getOccurrence(), 0.01); // weighted occurrences: 4 * (8.0 / 7.0) = 4.5714
        assertEquals(12.0, results.get(0).getScore());
    }

    @Test
    public void testYearFiltering() throws Exception {
        // Setup a completed show: 101
        UserAnimeListEntity completedEntity = new UserAnimeListEntity();
        completedEntity.setAnimeId(101);
        completedEntity.setStatus(ListStatusType.COMPLETED);

        when(userAnimeListRepository.findByStatus(ListStatusType.COMPLETED))
                .thenReturn(List.of(completedEntity));
        when(userAnimeListRepository.findByStatus(ListStatusType.DROPPED))
                .thenReturn(Collections.emptyList());

        AnimeDetails completedDetails = new AnimeDetails();
        completedDetails.setId(101);
        
        // 101 has 3 recommendations: 201, 202, 203
        Recommendation rec1 = new Recommendation();
        rec1.setNumRecommendations(1);
        Node n1 = new Node();
        n1.setId(201);
        rec1.setNode(n1);

        Recommendation rec2 = new Recommendation();
        rec2.setNumRecommendations(1);
        Node n2 = new Node();
        n2.setId(202);
        rec2.setNode(n2);

        Recommendation rec3 = new Recommendation();
        rec3.setNumRecommendations(1);
        Node n3 = new Node();
        n3.setId(203);
        rec3.setNode(n3);

        completedDetails.setRecommendations(List.of(rec1, rec2, rec3));
        when(animeService.getAnimeById(101)).thenReturn(completedDetails);

        // Mock shouldInclude
        when(filteringService.shouldInclude(eq(201), anySet(), anySet())).thenReturn(true);
        when(filteringService.shouldInclude(eq(202), anySet(), anySet())).thenReturn(true);
        when(filteringService.shouldInclude(eq(203), anySet(), anySet())).thenReturn(true);

        // Mock details lookups:
        // 201: year 1999 (too old)
        AnimeDetails d1 = new AnimeDetails();
        d1.setId(201);
        d1.setStartDate("1999-04-01");
        
        // 202: year 2005 (in range)
        AnimeDetails d2 = new AnimeDetails();
        d2.setId(202);
        d2.setTitle("Good Retro Show");
        d2.setStartDate("2005-10-06");
        d2.setMean(8.0);
        d2.setPopularity(200);

        // 203: year 2010 (too new)
        AnimeDetails d3 = new AnimeDetails();
        d3.setId(203);
        d3.setStartDate("2010-01-08");

        when(animeService.getAnimeById(201)).thenReturn(d1);
        when(animeService.getAnimeById(202)).thenReturn(d2);
        when(animeService.getAnimeById(203)).thenReturn(d3);

        // Mock ranking & explanation for 202 (which is the only one in range)
        when(rankingService.calculateScore(anyDouble(), any(), any())).thenReturn(8.0);
        when(explanationService.generateExplanation(anyDouble(), anyList())).thenReturn("Explained!");

        // Execute with minYear=2000, maxYear=2006
        List<RecommendationDetailedResult> results = recommendationFacade.getRecommendations("@me", 10, 2000, 2006);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Good Retro Show", results.get(0).getTitle());
    }

    @Test
    public void testTransientErrorAbortsExecution() throws Exception {
        UserAnimeListEntity completedEntity = new UserAnimeListEntity();
        completedEntity.setAnimeId(101);
        completedEntity.setStatus(ListStatusType.COMPLETED);

        when(userAnimeListRepository.findByStatus(ListStatusType.COMPLETED))
                .thenReturn(List.of(completedEntity));
        when(userAnimeListRepository.findByStatus(ListStatusType.DROPPED))
                .thenReturn(Collections.emptyList());

        // Mock details lookup to throw transient RuntimeException
        when(animeService.getAnimeById(101)).thenThrow(new RuntimeException("Transient connection issue"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            recommendationFacade.getRecommendations("@me", 10);
        });
    }

    @Test
    public void testMalResourceNotFoundExceptionIsSkipped() throws Exception {
        UserAnimeListEntity completedEntity = new UserAnimeListEntity();
        completedEntity.setAnimeId(101);
        completedEntity.setStatus(ListStatusType.COMPLETED);

        when(userAnimeListRepository.findByStatus(ListStatusType.COMPLETED))
                .thenReturn(List.of(completedEntity));
        when(userAnimeListRepository.findByStatus(ListStatusType.DROPPED))
                .thenReturn(Collections.emptyList());

        when(animeService.getAnimeById(101)).thenThrow(new MalResourceNotFoundException("Anime not found"));

        List<RecommendationDetailedResult> results = recommendationFacade.getRecommendations("@me", 10);
        assertNotNull(results);
        assertEquals(0, results.size());
    }

    @Test
    public void testTemporalDecayWeighting() throws Exception {
        // Setup two completed shows: 101 (completed now) and 102 (completed 365 days / ~12 months ago)
        UserAnimeListEntity c1 = new UserAnimeListEntity();
        c1.setAnimeId(101);
        c1.setStatus(ListStatusType.COMPLETED);
        c1.setUserScore(7);
        c1.setUpdatedAt(java.time.LocalDateTime.now());

        UserAnimeListEntity c2 = new UserAnimeListEntity();
        c2.setAnimeId(102);
        c2.setStatus(ListStatusType.COMPLETED);
        c2.setUserScore(7);
        c2.setUpdatedAt(java.time.LocalDateTime.now().minusDays(365));

        when(userAnimeListRepository.findByStatus(ListStatusType.COMPLETED))
                .thenReturn(List.of(c1, c2));
        when(userAnimeListRepository.findByStatus(ListStatusType.DROPPED))
                .thenReturn(Collections.emptyList());

        // Completed Show 101 details (recommends candidate 201)
        AnimeDetails details1 = new AnimeDetails();
        details1.setId(101);
        details1.setTitle("Recent Show");
        
        Recommendation rec1 = new Recommendation();
        rec1.setNumRecommendations(2);
        Node recNode1 = new Node();
        recNode1.setId(201);
        recNode1.setTitle("Recommended Show 1");
        rec1.setNode(recNode1);
        details1.setRecommendations(List.of(rec1));

        // Completed Show 102 details (recommends candidate 202)
        AnimeDetails details2 = new AnimeDetails();
        details2.setId(102);
        details2.setTitle("Old Show");
        
        Recommendation rec2 = new Recommendation();
        rec2.setNumRecommendations(2);
        Node recNode2 = new Node();
        recNode2.setId(202);
        recNode2.setTitle("Recommended Show 2");
        rec2.setNode(recNode2);
        details2.setRecommendations(List.of(rec2));

        when(animeService.getAnimeById(101)).thenReturn(details1);
        when(animeService.getAnimeById(102)).thenReturn(details2);

        // Mock filtering
        when(filteringService.shouldInclude(eq(201), anySet(), anySet())).thenReturn(true);
        when(filteringService.shouldInclude(eq(202), anySet(), anySet())).thenReturn(true);

        // Mock candidate details lookup
        AnimeDetails recDetails1 = new AnimeDetails();
        recDetails1.setId(201);
        recDetails1.setTitle("Recommended Show 1");
        recDetails1.setMean(8.0);
        recDetails1.setPopularity(100);

        AnimeDetails recDetails2 = new AnimeDetails();
        recDetails2.setId(202);
        recDetails2.setTitle("Recommended Show 2");
        recDetails2.setMean(8.0);
        recDetails2.setPopularity(100);

        when(animeService.getAnimeById(201)).thenReturn(recDetails1);
        when(animeService.getAnimeById(202)).thenReturn(recDetails2);

        // Capture occurrence counts passed to rankingService
        // Show 1 (decay weight ~ 1.0) -> occurrence ~ 2.0
        // Show 2 (decay weight ~ 0.5) -> occurrence ~ 1.0
        // Let's stub rankingService to return occurrence as score
        when(rankingService.calculateScore(anyDouble(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(explanationService.generateExplanation(anyDouble(), anyList())).thenReturn("Explained!");

        // Execute with decayMonths = 12
        List<RecommendationDetailedResult> results = recommendationFacade.getRecommendations("@me", 10, null, null, 12);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Recommended Show 1", results.get(0).getTitle());
        assertTrue(results.get(0).getScore() > 1.8 && results.get(0).getScore() <= 2.0); // score should be ~2.0
        assertEquals("Recommended Show 2", results.get(1).getTitle());
        assertTrue(results.get(1).getScore() > 0.8 && results.get(1).getScore() < 1.2); // score should be ~1.0
    }

    @Test
    public void testFreshnessBoostAndHybridTemporalWeighting() throws Exception {
        // Setup two completed shows: 101 (completed now) and 102 (completed 365 days / ~12 months ago)
        UserAnimeListEntity c1 = new UserAnimeListEntity();
        c1.setAnimeId(101);
        c1.setStatus(ListStatusType.COMPLETED);
        c1.setUserScore(7);
        c1.setUpdatedAt(java.time.LocalDateTime.now());

        UserAnimeListEntity c2 = new UserAnimeListEntity();
        c2.setAnimeId(102);
        c2.setStatus(ListStatusType.COMPLETED);
        c2.setUserScore(7);
        c2.setUpdatedAt(java.time.LocalDateTime.now().minusDays(365));

        when(userAnimeListRepository.findByStatus(ListStatusType.COMPLETED))
                .thenReturn(List.of(c1, c2));
        when(userAnimeListRepository.findByStatus(ListStatusType.DROPPED))
                .thenReturn(Collections.emptyList());

        // Completed Show 101 recommends candidate 201 (New candidate)
        AnimeDetails details1 = new AnimeDetails();
        details1.setId(101);
        details1.setTitle("Completed Recent");
        
        Recommendation rec1 = new Recommendation();
        rec1.setNumRecommendations(2);
        Node recNode1 = new Node();
        recNode1.setId(201);
        recNode1.setTitle("New Recommended");
        rec1.setNode(recNode1);
        details1.setRecommendations(List.of(rec1));

        // Completed Show 102 recommends candidate 202 (Old candidate)
        AnimeDetails details2 = new AnimeDetails();
        details2.setId(102);
        details2.setTitle("Completed Old");
        
        Recommendation rec2 = new Recommendation();
        rec2.setNumRecommendations(2);
        Node recNode2 = new Node();
        recNode2.setId(202);
        recNode2.setTitle("Old Recommended");
        rec2.setNode(recNode2);
        details2.setRecommendations(List.of(rec2));

        when(animeService.getAnimeById(101)).thenReturn(details1);
        when(animeService.getAnimeById(102)).thenReturn(details2);

        // Mock filtering
        when(filteringService.shouldInclude(eq(201), anySet(), anySet())).thenReturn(true);
        when(filteringService.shouldInclude(eq(202), anySet(), anySet())).thenReturn(true);

        // Candidate 201 (New show, e.g. 2025 release)
        AnimeDetails recDetails1 = new AnimeDetails();
        recDetails1.setId(201);
        recDetails1.setTitle("New Recommended");
        recDetails1.setStartDate("2025-01-01");
        recDetails1.setMean(8.0);
        recDetails1.setPopularity(100);

        // Candidate 202 (Old show, e.g. 2015 release)
        AnimeDetails recDetails2 = new AnimeDetails();
        recDetails2.setId(202);
        recDetails2.setTitle("Old Recommended");
        recDetails2.setStartDate("2015-01-01");
        recDetails2.setMean(8.0);
        recDetails2.setPopularity(100);

        when(animeService.getAnimeById(201)).thenReturn(recDetails1);
        when(animeService.getAnimeById(202)).thenReturn(recDetails2);

        // Mock rankingService to return occurrence as score
        when(rankingService.calculateScore(anyDouble(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(explanationService.generateExplanation(anyDouble(), anyList())).thenReturn("Explained!");

        // Execute hybrid test: decayMonths = 12 (halves old show's occurrence to 1.0, recent show remains 2.0)
        // and freshnessBoost = 0.2
        // Show 1 (2025) is 1 year old -> boost = exp(-0.2 * 1.0) = 0.8187 -> final score = 2.0 * 0.8187 = 1.637
        // Show 2 (2015) is 11 years old -> boost = exp(-0.2 * 11.0) = 0.1108 -> final score = 1.0 * 0.1108 = 0.111
        List<RecommendationDetailedResult> results = recommendationFacade.getRecommendations("@me", 10, null, null, 12, 0.2);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("New Recommended", results.get(0).getTitle());
        assertTrue(results.get(0).getScore() > 1.5 && results.get(0).getScore() < 1.7);
        assertEquals("Old Recommended", results.get(1).getTitle());
        assertTrue(results.get(1).getScore() > 0.1 && results.get(1).getScore() < 0.12);
    }

    @Test
    public void testExcludeCompletedFranchises() throws Exception {
        // Setup completed show 101
        UserAnimeListEntity c1 = new UserAnimeListEntity();
        c1.setAnimeId(101);
        c1.setStatus(ListStatusType.COMPLETED);
        c1.setUserScore(7);

        when(userAnimeListRepository.findByStatus(ListStatusType.COMPLETED))
                .thenReturn(List.of(c1));
        when(userAnimeListRepository.findByStatus(ListStatusType.DROPPED))
                .thenReturn(Collections.emptyList());

        // Completed Show 101 details (recommends candidate 102)
        AnimeDetails details1 = new AnimeDetails();
        details1.setId(101);
        details1.setTitle("AOT Part 1");
        
        // 101 is related to 102 (completed franchise relation graph)
        RelatedAnime rel = new RelatedAnime();
        Node relNode = new Node();
        relNode.setId(102);
        rel.setNode(relNode);
        details1.setRelatedAnime(List.of(rel));

        Recommendation rec = new Recommendation();
        rec.setNumRecommendations(3);
        Node recNode = new Node();
        recNode.setId(102);
        recNode.setTitle("AOT Part 2");
        rec.setNode(recNode);
        details1.setRecommendations(List.of(rec));

        when(animeService.getAnimeById(101)).thenReturn(details1);

        // When excludeCompleted is true, 102 is excluded because it's related to 101
        List<RecommendationDetailedResult> resultsExcluded = recommendationFacade.getRecommendations(
                "@me", 10, null, null, null, null, true, false);
        assertEquals(0, resultsExcluded.size());

        // When excludeCompleted is false, 102 is included
        when(filteringService.shouldInclude(eq(102), anySet(), anySet())).thenReturn(true);
        AnimeDetails candidateDetails = new AnimeDetails();
        candidateDetails.setId(102);
        candidateDetails.setTitle("AOT Part 2");
        candidateDetails.setMean(8.5);
        candidateDetails.setPopularity(200);
        when(animeService.getAnimeById(102)).thenReturn(candidateDetails);

        when(rankingService.calculateScore(anyDouble(), any(), any())).thenReturn(8.5);
        when(explanationService.generateExplanation(anyDouble(), anyList())).thenReturn("Explained!");

        List<RecommendationDetailedResult> resultsIncluded = recommendationFacade.getRecommendations(
                "@me", 10, null, null, null, null, false, false);
        assertEquals(1, resultsIncluded.size());
        assertEquals("AOT Part 2", resultsIncluded.get(0).getTitle());
    }

    @Test
    public void testCandidateFranchiseGrouping() throws Exception {
        // Setup completed show 101
        UserAnimeListEntity c1 = new UserAnimeListEntity();
        c1.setAnimeId(101);
        c1.setStatus(ListStatusType.COMPLETED);
        c1.setUserScore(7);

        when(userAnimeListRepository.findByStatus(ListStatusType.COMPLETED))
                .thenReturn(List.of(c1));
        when(userAnimeListRepository.findByStatus(ListStatusType.DROPPED))
                .thenReturn(Collections.emptyList());

        AnimeDetails details1 = new AnimeDetails();
        details1.setId(101);
        details1.setTitle("Completed Show");

        // Completed Show 101 recommends candidate 201 and candidate 202
        Recommendation rec1 = new Recommendation();
        rec1.setNumRecommendations(3);
        Node recNode1 = new Node();
        recNode1.setId(201);
        recNode1.setTitle("Season 1");
        rec1.setNode(recNode1);

        Recommendation rec2 = new Recommendation();
        rec2.setNumRecommendations(3);
        Node recNode2 = new Node();
        recNode2.setId(202);
        recNode2.setTitle("Season 2");
        rec2.setNode(recNode2);

        details1.setRecommendations(List.of(rec1, rec2));
        when(animeService.getAnimeById(101)).thenReturn(details1);

        when(filteringService.shouldInclude(eq(201), anySet(), anySet())).thenReturn(true);
        when(filteringService.shouldInclude(eq(202), anySet(), anySet())).thenReturn(true);

        // Candidate 201: Season 1 (more popular: popularity 100)
        AnimeDetails candidate1 = new AnimeDetails();
        candidate1.setId(201);
        candidate1.setTitle("Season 1");
        candidate1.setMean(8.0);
        candidate1.setPopularity(100);
        
        RelatedAnime relTo2 = new RelatedAnime();
        Node node2 = new Node();
        node2.setId(202);
        relTo2.setNode(node2);
        relTo2.setRelationType("sequel");
        candidate1.setRelatedAnime(List.of(relTo2));

        // Candidate 202: Season 2 (less popular: popularity 200)
        AnimeDetails candidate2 = new AnimeDetails();
        candidate2.setId(202);
        candidate2.setTitle("Season 2");
        candidate2.setMean(8.2);
        candidate2.setPopularity(200);

        RelatedAnime relTo1 = new RelatedAnime();
        Node node1 = new Node();
        node1.setId(201);
        relTo1.setNode(node1);
        relTo1.setRelationType("prequel");
        candidate2.setRelatedAnime(List.of(relTo1));

        when(animeService.getAnimeById(201)).thenReturn(candidate1);
        when(animeService.getAnimeById(202)).thenReturn(candidate2);

        // Mock ranking & explanations
        when(rankingService.calculateScore(anyDouble(), any(), any())).thenAnswer(invocation -> {
            // return occurrence as score
            return (double) invocation.getArgument(0);
        });
        when(explanationService.generateExplanation(anyDouble(), anyList())).thenReturn("Explained!");

        // Execute with groupCandidates = true
        List<RecommendationDetailedResult> results = recommendationFacade.getRecommendations(
                "@me", 10, null, null, null, null, false, true);

        // Should consolidate 201 and 202 into a single result under the more popular "Season 1" (ID: 201)
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(201, results.get(0).getId());
        assertEquals("Season 1", results.get(0).getTitle());
        // Consolidated occurrence should be 3.0 + 3.0 = 6.0
        assertEquals(6.0, results.get(0).getOccurrence(), 0.01);
        assertEquals(6.0, results.get(0).getScore(), 0.01);
    }
}
