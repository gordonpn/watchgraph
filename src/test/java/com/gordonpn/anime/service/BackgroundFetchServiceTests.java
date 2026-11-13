package com.gordonpn.anime.service;

import com.gordonpn.anime.config.AuthorizationState;
import com.gordonpn.anime.entity.SyncStatusEntity;
import com.gordonpn.anime.entity.UserAnimeListEntity;
import com.gordonpn.anime.models.*;
import com.gordonpn.anime.models.dto.TokenResponse;
import com.gordonpn.anime.entity.TokenEntity;
import com.gordonpn.anime.models.userlist.Status;
import com.gordonpn.anime.repository.SyncStatusRepository;
import com.gordonpn.anime.repository.TokenRepository;
import com.gordonpn.anime.repository.UserAnimeListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BackgroundFetchServiceTests {

    @Mock
    private UserListService userListService;
    @Mock
    private AuthorizationState authorizationState;
    @Mock
    private TokenExchangeService tokenExchangeService;
    @Mock
    private TokenRepository tokenRepository;
    @Mock
    private UserAnimeListRepository userAnimeListRepository;
    @Mock
    private SyncStatusRepository syncStatusRepository;

    private BackgroundFetchService backgroundFetchService;

    @BeforeEach
    public void setUp() {
        backgroundFetchService = new BackgroundFetchService(
                userListService,
                authorizationState,
                tokenExchangeService,
                tokenRepository,
                userAnimeListRepository,
                syncStatusRepository
        );
    }

    @Test
    public void testFetchList() throws Exception {
        TokenResponse mockToken = new TokenResponse();
        when(authorizationState.getTokenResponse()).thenReturn(mockToken);
        when(tokenExchangeService.refreshToken(mockToken)).thenReturn(mockToken);

        // Mock list returned for each status
        AnimeList mockList = new AnimeList();
        List<Datum> data = new ArrayList<>();
        Datum item = new Datum();
        Node node = new Node();
        node.setId(100);
        node.setTitle("Test Anime");
        item.setNode(node);
        data.add(item);
        mockList.setData(data);

        when(userListService.getListByUserName(eq("@me"), any(Status.class), eq(true))).thenReturn(mockList);
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());

        backgroundFetchService.fetchList();

        verify(tokenRepository, times(1)).save(any(TokenEntity.class));
        // Verify userAnimeListRepository.save is called for each status (Status.values().length = 6)
        verify(userAnimeListRepository, times(Status.values().length)).save(any(UserAnimeListEntity.class));
        verify(syncStatusRepository, times(Status.values().length)).save(any(SyncStatusEntity.class));
    }
    @Test
    void freshSnapshotsAvoidTokenRefreshAndFetches() {
        for (Status status : Status.values()) {
            SyncStatusEntity snapshot = new SyncStatusEntity();
            snapshot.setListType(status.getType());
            snapshot.setLastSyncedAt(java.time.LocalDateTime.now());
            when(syncStatusRepository.findById(status.getType())).thenReturn(Optional.of(snapshot));
        }
        backgroundFetchService.fetchList();
        verifyNoInteractions(tokenExchangeService, tokenRepository, userListService, userAnimeListRepository);
    }
    private void authorize() {
        TokenResponse token = new TokenResponse();
        when(authorizationState.getTokenResponse()).thenReturn(token);
        when(tokenExchangeService.refreshToken(token)).thenReturn(token);
    }

    @Test
    void statusMappingAndCutoffDetermineExactFetchMembership() {
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 11, 11, 12, 0);
        java.util.Set<ListStatusType> mapped = new java.util.HashSet<>();
        for (Status target : Status.values()) {
            reset(syncStatusRepository, userListService);
            authorize();
            for (Status status : Status.values()) {
                mapped.add(ListStatusType.fromValue(status.getType()));
                SyncStatusEntity snapshot = new SyncStatusEntity();
                snapshot.setLastSyncedAt(status == target ? now.minusHours(1) : now.minusHours(1).plusNanos(1));
                when(syncStatusRepository.findById(status.getType())).thenReturn(Optional.of(snapshot));
            }
            AnimeList empty = new AnimeList();
            empty.setData(List.of());
            when(userListService.getListByUserName("@me", target, true)).thenReturn(empty);
            backgroundFetchService.synchronize(false, now);
            verify(userListService).getListByUserName("@me", target, true);
            verifyNoMoreInteractions(userListService);
            verify(syncStatusRepository).save(argThat(value -> value.getListType().equals(target.getType())
                    && value.getLastSyncedAt().equals(now)));
        }
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(ListStatusType.values()), mapped);
    }

    @Test
    void missingAuthDoesNotMutateSnapshots() {
        backgroundFetchService.fetchList();
        verifyNoInteractions(tokenExchangeService, tokenRepository, userListService, userAnimeListRepository);
        verify(syncStatusRepository, never()).save(any());
    }

    @Test
    void successfulEmptyStatusCleansOnlyItsOwnRowsAndFailureRemainsEligible() {
        authorize();
        UserAnimeListEntity removed = new UserAnimeListEntity();
        removed.setAnimeId(7);
        removed.setStatus(ListStatusType.WATCHING);
        when(userAnimeListRepository.findByStatus(ListStatusType.WATCHING)).thenReturn(List.of(removed));
        AnimeList empty = new AnimeList();
        empty.setData(List.of());
        when(userListService.getListByUserName("@me", Status.WATCHING, true)).thenReturn(empty);
        when(userListService.getListByUserName("@me", Status.PLAN_TO_WATCH, true))
                .thenThrow(new IllegalStateException("offline"));
        for (int attempt = 0; attempt < 2; attempt++) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> backgroundFetchService.fetchList());
        }
        verify(userAnimeListRepository, times(2)).delete(removed);
        verify(userAnimeListRepository, never()).findByStatus(ListStatusType.PLAN_TO_WATCH);
        verify(syncStatusRepository, never()).save(argThat(value -> value.getListType().equals("plan_to_watch")));
    }

    @Test
    void incompleteAndSkippedStatusesKeepTheirRowsAndTimestamps() {
        authorize();
        SyncStatusEntity fresh = new SyncStatusEntity();
        fresh.setLastSyncedAt(java.time.LocalDateTime.now());
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());
        when(syncStatusRepository.findById("completed")).thenReturn(Optional.of(fresh));
        AnimeList incomplete = new AnimeList();
        incomplete.setData(List.of());
        Paging paging = new Paging();
        paging.setNext("https://example.test/next");
        incomplete.setPaging(paging);
        when(userListService.getListByUserName(eq("@me"), any(Status.class), eq(true))).thenReturn(incomplete);
        backgroundFetchService.fetchList();
        verify(userListService, never()).getListByUserName("@me", Status.COMPLETED, true);
        verifyNoInteractions(userAnimeListRepository);
        verify(syncStatusRepository, never()).save(any());
    }

    @Test
    void explicitSyncFetchesAllStatusesEvenWithFreshSnapshots() {
        authorize();
        for (Status status : Status.values()) {
            SyncStatusEntity fresh = new SyncStatusEntity();
            fresh.setLastSyncedAt(java.time.LocalDateTime.now());
            when(syncStatusRepository.findById(status.getType())).thenReturn(Optional.of(fresh));
        }
        AnimeList empty = new AnimeList();
        empty.setData(List.of());
        when(userListService.getListByUserName(eq("@me"), any(Status.class), eq(true))).thenReturn(empty);
        var result = backgroundFetchService.forceFetchList();
        org.junit.jupiter.api.Assertions.assertTrue(result.skippedStatuses().isEmpty());
        for (Status status : Status.values()) {
            verify(userListService).getListByUserName("@me", status, true);
        }
    }
    @Test
    void nullAndExpiredTimestampsAreStaleForEveryStatus() {
        authorize();
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 11, 11, 12, 0);
        for (Status status : Status.values()) {
            SyncStatusEntity stale = new SyncStatusEntity();
            stale.setLastSyncedAt(status.ordinal() % 2 == 0 ? null : now.minusHours(1).minusNanos(1));
            when(syncStatusRepository.findById(status.getType())).thenReturn(Optional.of(stale));
        }
        AnimeList empty = new AnimeList();
        empty.setData(List.of());
        when(userListService.getListByUserName(eq("@me"), any(Status.class), eq(true))).thenReturn(empty);
        backgroundFetchService.synchronize(false, now);
        for (Status status : Status.values()) {
            verify(userListService).getListByUserName("@me", status, true);
        }
    }
    @Test
    void forcedSyncIdentifiesStatusesNotRefreshed() {
        authorize();
        AnimeList complete = new AnimeList();
        complete.setData(List.of());
        AnimeList paginated = new AnimeList();
        paginated.setData(List.of());
        Paging paging = new Paging();
        paging.setNext("https://example.test/next");
        paginated.setPaging(paging);
        when(userListService.getListByUserName(eq("@me"), any(Status.class), eq(true))).thenReturn(complete);
        when(userListService.getListByUserName("@me", Status.COMPLETED, true)).thenReturn(paginated);

        var result = backgroundFetchService.forceFetchList();

        org.junit.jupiter.api.Assertions.assertEquals(List.of(Status.COMPLETED), result.skippedStatuses());
        verify(syncStatusRepository, never()).save(argThat(value -> value.getListType().equals("completed")));
        verify(syncStatusRepository, times(Status.values().length - 1)).save(any());
    }

    @Test
    void forcedSyncWithoutAuthorizationReportsEveryStatusSkipped() {
        var result = backgroundFetchService.forceFetchList();
        org.junit.jupiter.api.Assertions.assertEquals(List.of(Status.values()), result.skippedStatuses());
        verifyNoInteractions(userListService, userAnimeListRepository);
    }
}
