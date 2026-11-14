package com.gordonpn.anime.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gordonpn.anime.commands.*;
import com.gordonpn.anime.config.JacksonConfig;
import com.gordonpn.anime.models.*;
import com.gordonpn.anime.models.userlist.Status;
import com.gordonpn.anime.repository.*;
import com.gordonpn.anime.service.*;
import com.gordonpn.anime.service.recommendation.*;
import com.gordonpn.anime.service.recommendation.explanation.TableFormatter;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Offline baseline: real business logic and presentation, mocked external boundaries only. */
class AnimeGoldenOutputTests {
    private final ObjectMapper mapper = new JacksonConfig().objectMapper();
    private final AnimeService anime = mock(AnimeService.class);
    private final UserListService lists = mock(UserListService.class);
    private RecommendationCommands commands;
    private Locale previousLocale;
    private MockedStatic<LocalDateTime> dates;
    private MockedStatic<Year> years;

    @BeforeEach
    void setUp() throws Exception {
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
        LocalDateTime fixedDate = LocalDateTime.of(2026, 1, 1, 12, 0);
        Year fixedYear = Year.of(2026);
        dates = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS);
        dates.when(LocalDateTime::now).thenReturn(fixedDate);
        years = mockStatic(Year.class, CALLS_REAL_METHODS);
        years.when(Year::now).thenReturn(fixedYear);

        AnimeDetails[] details = mapper.readValue(resource("profile.json"), AnimeDetails[].class);
        Map<Integer, AnimeDetails> byId = new HashMap<>();
        for (AnimeDetails detail : details) byId.put(detail.getId(), detail);
        when(anime.getAnimeById(anyInt())).thenAnswer(call -> {
            int id = call.getArgument(0);
            assertTrue(byId.containsKey(id), "Unexpected anime lookup: " + id);
            return byId.get(id);
        });
        AnimeList completed = mapper.readValue(resource("completed.json"), AnimeList.class);
        AnimeList dropped = mapper.readValue(resource("dropped.json"), AnimeList.class);
        when(lists.getListByUserName("fixture-user", Status.COMPLETED)).thenReturn(completed);
        when(lists.getListByUserName("fixture-user", Status.DROPPED)).thenReturn(dropped);
        RankingService ranking = new RankingService();
        ReflectionTestUtils.setField(ranking, "weightOccurrence", 1.0);
        ReflectionTestUtils.setField(ranking, "weightRating", 0.5);
        ReflectionTestUtils.setField(ranking, "weightPopularity", 0.0001);
        RecommendationFacade facade = new RecommendationFacade(mock(UserAnimeListRepository.class),
                mock(AnimeCacheRepository.class), lists, anime, new FilteringService(), ranking, new ExplanationService());
        commands = new RecommendationCommands(mapper, mock(RecommendationService.class), anime, facade,
                null, null, null, new TableFormatter(), null);
        SeasonalAnime seasonal = mapper.readValue(resource("seasonal-input.json"), SeasonalAnime.class);
        SeasonalAnime emptySeason = new SeasonalAnime();
        emptySeason.setData(List.of());
        when(anime.getSeasonalAnime(anyString(), anyInt())).thenAnswer(call -> {
            assertEquals(Integer.valueOf(2026), call.getArgument(1));
            return "winter".equals(call.getArgument(0)) ? seasonal : emptySeason;
        });
    }

    @AfterEach
    void tearDown() {
        if (years != null) years.close();
        if (dates != null) dates.close();
        Locale.setDefault(previousLocale);
    }

    @Test
    void recommendationJsonAndTable() throws Exception {
        golden("recommendation.json", recommend(true, true, 0, 0.0));
        golden("recommendation.txt", recommend(false, true, 0, 0.0));
    }

    @Test
    void temporalAndUngroupedProfiles() throws Exception {
        golden("recommendation-temporal.json", recommend(true, true, 12, 0.15));
        golden("recommendation-ungrouped.json", recommend(true, false, 0, 0.0));
    }

    @Test
    void emptyAndUnratedProfiles() throws Exception {
        AnimeList completed = mapper.readValue(resource("completed.json"), AnimeList.class);
        completed.getData().forEach(datum -> datum.getListStatus().setScore(0));
        when(lists.getListByUserName("fixture-user", Status.COMPLETED)).thenReturn(completed);
        golden("recommendation-unrated.json", recommend(true, true, 0, 0.0));
        completed.setData(List.of());
        golden("recommendation-empty.json", recommend(true, true, 0, 0.0));
        golden("recommendation-empty.txt", recommend(false, true, 0, 0.0));
    }

    private String recommend(boolean json, boolean grouped, int decay, double freshness) {
        return commands.recommendation("fixture-user", json, 10, 1900, 2100, false, grouped, decay, freshness);
    }

    @Test
    void seasonalJsonAndTable() throws Exception {
        golden("seasonal.json", commands.seasonal("1", true, 10));
        golden("seasonal.txt", commands.seasonal("1", false, 10));
    }

    @Test
    void listReturnsExistingDto() throws Exception {
        ListCommands command = new ListCommands(lists, mock(BackgroundFetchService.class));
        golden("list.json", mapper.writeValueAsString(command.list("fixture-user")));
        verify(lists).getListByUserName("fixture-user", Status.COMPLETED);
    }

    @Test
    void healthMissingAndUnavailableDatabase() throws Exception {
        TokenRepository tokens = mock(TokenRepository.class);
        HealthCommands command = new HealthCommands(tokens);
        ReflectionTestUtils.setField(command, "dbUrl", "jdbc:sqlite:fixture.db");
        // Only the diagnostic timestamp is volatile; all other bytes remain protected.
        golden("health.txt", stableHealth(command));
        when(tokens.count()).thenThrow(new IllegalStateException("fixture database unavailable"));
        golden("health-unavailable.txt", stableHealth(command));
    }

    private String stableHealth(HealthCommands command) {
        String output = command.health();
        assertTrue(output.matches("(?s).*Current Time: [^\n]+Z\n.*"));
        return output.replaceFirst("Current Time: [^\n]+", "Current Time: <instant>");
    }

    private String resource(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/golden/" + name)) {
            assertNotNull(stream, "Missing fixture " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void golden(String name, String actual) throws Exception {
        assertEquals(resource(name), actual, "Golden output changed: " + name);
    }
}
