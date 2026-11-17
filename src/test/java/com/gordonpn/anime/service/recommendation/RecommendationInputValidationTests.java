package com.gordonpn.anime.service.recommendation;

import com.gordonpn.anime.models.ListStatusType;
import com.gordonpn.anime.repository.AnimeCacheRepository;
import com.gordonpn.anime.repository.UserAnimeListRepository;
import com.gordonpn.anime.service.AnimeService;
import com.gordonpn.anime.service.UserListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationInputValidationTests {
    @Mock UserAnimeListRepository lists;
    @Mock AnimeCacheRepository cache;
    @Mock UserListService userLists;
    @Mock AnimeService anime;
    @Mock FilteringService filtering;
    @Mock RankingService ranking;
    @Mock ExplanationService explanation;
    RecommendationFacade facade;

    @BeforeEach
    void setUp() {
        facade = new RecommendationFacade(lists, cache, userLists, anime, filtering, ranking, explanation);
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of(null, 1, null, null, null, null, "userName"),
                Arguments.of("", 1, null, null, null, null, "userName"),
                Arguments.of(" \t", 1, null, null, null, null, "userName"),
                Arguments.of("@me", 0, null, null, null, null, "limit"),
                Arguments.of("@me", -1, null, null, null, null, "limit"),
                Arguments.of("@me", 1, 2025, 2024, null, null, "minYear"),
                Arguments.of("@me", 1, 0, null, null, null, "minYear"),
                Arguments.of("@me", 1, -1, null, null, null, "minYear"),
                Arguments.of("@me", 1, null, 0, null, null, "maxYear"),
                Arguments.of("@me", 1, null, -1, null, null, "maxYear"),
                Arguments.of("@me", 1, null, null, -1, null, "decayMonths"),
                Arguments.of("@me", 1, null, null, null, -0.1, "freshnessBoost"),
                Arguments.of("@me", 1, null, null, null, Double.NaN, "freshnessBoost"),
                Arguments.of("@me", 1, null, null, null, Double.POSITIVE_INFINITY, "freshnessBoost"),
                Arguments.of("@me", 1, null, null, null, Double.NEGATIVE_INFINITY, "freshnessBoost"));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void rejectsBeforeAnyExternalWork(String userName, int limit, Integer minYear, Integer maxYear,
                                     Integer decayMonths, Double freshnessBoost, String parameter) {
        var error = assertThrows(IllegalArgumentException.class, () -> facade.getRecommendations(
                userName, limit, minYear, maxYear, decayMonths, freshnessBoost, null, null));
        assertTrue(error.getMessage().contains(parameter), error.getMessage());
        verifyNoInteractions(lists, cache, userLists, anime, filtering, ranking, explanation);
    }

    @Test
    void acceptsAbsentFiltersThroughEveryOverload() {
        assertTrue(facade.getRecommendations("@me", 1).isEmpty());
        assertTrue(facade.getRecommendations("@me", 1, null, null).isEmpty());
        assertTrue(facade.getRecommendations("@me", 1, null, null, null).isEmpty());
        assertTrue(facade.getRecommendations("@me", 1, null, null, null, null).isEmpty());
        assertTrue(facade.getRecommendations("@me", 1, null, null, null, null, null, null).isEmpty());
        verify(lists, times(5)).findByStatus(ListStatusType.COMPLETED);
    }

    @Test
    void acceptsEqualAndOpenYearRangesAndDisabledTemporalValues() {
        assertTrue(facade.getRecommendations("@me", 1, 1, 1, 0, 0.0).isEmpty());
        assertTrue(facade.getRecommendations("@me", 1, 2024, null, 1, 0.1).isEmpty());
        assertTrue(facade.getRecommendations("@me", 1, null, 2024, null, null).isEmpty());
        verify(lists, times(3)).findByStatus(ListStatusType.COMPLETED);
    }
}
