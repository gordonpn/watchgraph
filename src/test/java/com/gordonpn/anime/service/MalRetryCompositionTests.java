package com.gordonpn.anime.service;

import com.gordonpn.anime.entity.UserAnimeListEntity;
import com.gordonpn.anime.models.ListStatusType;
import com.gordonpn.anime.repository.UserAnimeListRepository;
import com.gordonpn.anime.service.recommendation.FilteringService;
import com.gordonpn.anime.service.recommendation.RankingService;
import com.gordonpn.anime.service.recommendation.ExplanationService;
import com.gordonpn.anime.service.recommendation.RecommendationFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gordonpn.anime.config.MalRetryInterceptor;
import com.gordonpn.anime.entity.AnimeCacheEntity;
import com.gordonpn.anime.entity.GenericCacheEntity;
import com.gordonpn.anime.models.exception.MalResourceNotFoundException;
import com.gordonpn.anime.models.userlist.Status;
import com.gordonpn.anime.repository.AnimeCacheRepository;
import com.gordonpn.anime.repository.GenericCacheRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MalRetryCompositionTests {
    enum Boundary { DETAILS, SEASONAL, LIST }

    static Stream<Arguments> failures() {
        return Stream.of(Boundary.values()).flatMap(boundary ->
                Stream.concat(Stream.of(401, 403, 404, 429, 0), java.util.stream.IntStream.rangeClosed(500, 599).boxed())
                        .map(status -> Arguments.of(boundary, status)));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void appliesPolicyExactlyOnce(Boundary boundary, int status) {
        Fixture fixture = new Fixture(boundary, 3, status);
        RuntimeException failure = assertThrows(RuntimeException.class, fixture::fetch);
        if (status == 404) assertInstanceOf(MalResourceNotFoundException.class, failure);
        else if (status == 0) assertInstanceOf(ResourceAccessException.class, failure);
        else assertEquals(status, assertInstanceOf(RestClientResponseException.class, failure).getStatusCode().value());
        boolean transientFailure = status == 0 || status == 429 || status >= 500;
        assertEquals(transientFailure ? 3 : 1, fixture.attempts);
        assertEquals(transientFailure ? List.of(10L, 20L) : List.of(), fixture.waits);
    }

    @ParameterizedTest
    @EnumSource(Boundary.class)
    void retriesThenReturnsCompletePayload(Boundary boundary) {
        Fixture fixture = new Fixture(boundary, 3, 429, 503, 200);
        assertNotNull(fixture.fetch());
        assertEquals(3, fixture.attempts);
        assertEquals(List.of(10L, 20L), fixture.waits);
    }

    @ParameterizedTest
    @EnumSource(Boundary.class)
    void exhaustedTransientFailuresUseUsableExpiredCache(Boundary boundary) {
        Fixture fixture = new Fixture(boundary, 3, 503);
        fixture.cache(fixture.payload);
        assertNotNull(fixture.fetch());
        assertEquals(3, fixture.attempts);
    }

    @ParameterizedTest
    @EnumSource(Boundary.class)
    void corruptExpiredCachePreservesOriginalFailure(Boundary boundary) {
        Fixture fixture = new Fixture(boundary, 3, 503);
        fixture.cache("{broken");
        assertEquals(503, assertThrows(RestClientResponseException.class, fixture::fetch).getStatusCode().value());
        assertEquals(3, fixture.attempts);
    }

    static Stream<Arguments> permanentFailures() {
        return Stream.of(Boundary.values()).flatMap(boundary ->
                Stream.of(401, 403, 404).map(status -> Arguments.of(boundary, status)));
    }

    @ParameterizedTest
    @MethodSource("permanentFailures")
    void permanentFailuresNeverHideBehindStaleCache(Boundary boundary, int status) {
        Fixture fixture = new Fixture(boundary, 3, status);
        fixture.cache(fixture.payload);
        assertThrows(RuntimeException.class, fixture::fetch);
        assertEquals(1, fixture.attempts);
    }

    @ParameterizedTest
    @EnumSource(Boundary.class)
    void malformedAndHtmlPayloadsAbortWithoutRetryOrStaleFallback(Boundary boundary) {
        for (String payload : List.of("{broken", "<html>challenge</html>", "null")) {
            Fixture fixture = new Fixture(boundary, 3, 200);
            fixture.cache(fixture.payload);
            fixture.payload = payload;
            assertThrows(RuntimeException.class, fixture::fetch);
            assertEquals(1, fixture.attempts);
            assertEquals(List.of(), fixture.waits);
        }
    }

    @ParameterizedTest
    @EnumSource(Boundary.class)
    void unboundedModeStopsBeforeNextRequestWhenInterrupted(Boundary boundary) {
        Fixture fixture = new Fixture(boundary, -1, 503);
        fixture.cache(fixture.payload);
        fixture.interruptOnWait = true;
        try {
            assertThrows(RuntimeException.class, fixture::fetch);
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, fixture.attempts);
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @EnumSource(Boundary.class)
    void alreadyInterruptedRequestNeverStarts(Boundary boundary) {
        Fixture fixture = new Fixture(boundary, -1, 200);
        try {
            Thread.currentThread().interrupt();
            assertThrows(RuntimeException.class, fixture::fetch);
            assertEquals(0, fixture.attempts);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @EnumSource(Boundary.class)
    void unboundedModeCanRecoverAfterFiniteFailures(Boundary boundary) {
        Fixture fixture = new Fixture(boundary, -1, 503, 503, 503, 200);
        assertNotNull(fixture.fetch());
        assertEquals(4, fixture.attempts);
        assertEquals(List.of(10L, 20L, 40L), fixture.waits);
    }

    static Stream<Arguments> recommendationFailures() {
        return Stream.of(Arguments.of(503, "{}"), Arguments.of(0, "{}"),
                Arguments.of(200, "{broken"), Arguments.of(200, "<html>challenge</html>"),
                Arguments.of(200, "null"));
    }

    @ParameterizedTest
    @MethodSource("recommendationFailures")
    void recommendationGenerationAbortsOnUnusableData(int status, String payload) {
        Fixture fixture = new Fixture(Boundary.DETAILS, 3, status);
        fixture.payload = payload;
        var localLists = mock(UserAnimeListRepository.class);
        var completed = new UserAnimeListEntity();
        completed.setAnimeId(1);
        when(localLists.findByStatus(ListStatusType.COMPLETED))
                .thenReturn(List.of(completed));
        var filtering = mock(FilteringService.class);
        var ranking = mock(RankingService.class);
        var explanation = mock(ExplanationService.class);
        var recommendations = new RecommendationFacade(
                localLists, fixture.animeCache, fixture.lists, fixture.anime, filtering, ranking, explanation);
        assertThrows(RuntimeException.class, () -> recommendations.getRecommendations("@me", 10));
        assertEquals(status == 200 ? 1 : 3, fixture.attempts);
        verifyNoInteractions(filtering, ranking, explanation);
    }

    private static class Fixture {
        final Boundary boundary;
        final List<Long> waits = new ArrayList<>();
        final AnimeCacheRepository animeCache = mock(AnimeCacheRepository.class);
        final GenericCacheRepository genericCache = mock(GenericCacheRepository.class);
        final AnimeService anime;
        final UserListService lists;
        int attempts;
        boolean interruptOnWait;
        String payload;

        Fixture(Boundary boundary, int maxAttempts, int... statuses) {
            this.boundary = boundary;
            payload = boundary == Boundary.DETAILS ? "{\"id\":1,\"title\":\"Complete title\"}" : "{\"data\":[]}";
            MalRetryInterceptor retry = new MalRetryInterceptor(maxAttempts, 10, 2) {
                protected void sleep(long ms) {
                    waits.add(ms);
                    if (interruptOnWait) {
                        Thread.currentThread().interrupt();
                        super.sleep(0); // Exercise the real wait interruption handler without wall-clock delay.
                    }
                }
            };
            RestClient client = RestClient.builder().baseUrl("https://mal.test")
                    .requestFactory((uri, method) -> {
                        ClientHttpRequest request = mock(ClientHttpRequest.class);
                        when(request.getHeaders()).thenReturn(new HttpHeaders());
                        when(request.getBody()).thenReturn(new ByteArrayOutputStream());
                        when(request.execute()).thenAnswer(invocation -> {
                            int status = statuses[Math.min(attempts++, statuses.length - 1)];
                            if (status == 0) throw new IOException("offline");
                            MockClientHttpResponse response = new MockClientHttpResponse(payload.getBytes(StandardCharsets.UTF_8), HttpStatusCode.valueOf(status));
                            response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                            return response;
                        });
                        return request;
                    }).requestInterceptor(retry).build();
            anime = new AnimeService(new ObjectMapper(), client, animeCache, genericCache);
            lists = new UserListService(new ObjectMapper(), client, genericCache);
        }

        Object fetch() {
            return switch (boundary) {
                case DETAILS -> anime.getAnimeById(1);
                case SEASONAL -> anime.getSeasonalAnime("winter", 2025);
                case LIST -> lists.getListByUserName("name", Status.COMPLETED);
            };
        }

        void cache(String json) {
            if (boundary == Boundary.DETAILS) {
                AnimeCacheEntity entry = new AnimeCacheEntity();
                entry.setDetailsJson(json);
                entry.setCachedAt(LocalDateTime.now().minusDays(60));
                when(animeCache.findById(1)).thenReturn(Optional.of(entry));
            } else {
                GenericCacheEntity entry = new GenericCacheEntity();
                entry.setCacheValue(json);
                entry.setCachedAt(LocalDateTime.now().minusDays(60));
                when(genericCache.findById(anyString())).thenReturn(Optional.of(entry));
            }
        }
    }
}
