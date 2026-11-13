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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testFetchList(boolean includeInvalidIds) throws Exception {
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
        for (Integer invalidId : includeInvalidIds ? java.util.Arrays.asList(0, -1, null) : List.<Integer>of()) {
            Datum invalid = new Datum();
            Node invalidNode = new Node();
            invalidNode.setId(invalidId);
            invalid.setNode(invalidNode);
            data.add(invalid);
        }
        mockList.setData(data);

        when(userListService.getListByUserName(eq("@me"), any(Status.class))).thenReturn(mockList);
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());

        backgroundFetchService.fetchList();

        verify(tokenRepository, times(1)).save(any(TokenEntity.class));
        // Verify userAnimeListRepository.save is called for each status (Status.values().length = 6)
        verify(userAnimeListRepository, times(Status.values().length)).save(any(UserAnimeListEntity.class));
        verify(syncStatusRepository, times(Status.values().length)).save(any(SyncStatusEntity.class));
        verify(userAnimeListRepository, never()).findByAnimeId(argThat(id -> id == null || id <= 0));
        verify(userAnimeListRepository, times(Status.values().length))
                .save(argThat(entity -> entity.getAnimeId() == 100));
    }
}
