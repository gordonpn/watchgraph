package com.gordonpn.anime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gordonpn.anime.entity.GenericCacheEntity;
import com.gordonpn.anime.models.AnimeList;
import com.gordonpn.anime.models.userlist.Status;
import com.gordonpn.anime.repository.GenericCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserListServiceTests {

    @Mock
    private RestClient apiKeyClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Mock
    private ResponseEntity<String> responseEntity;

    @Mock
    private GenericCacheRepository genericCacheRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserListService userListService;

    @BeforeEach
    public void setUp() {
        userListService = new UserListService(objectMapper, apiKeyClient, genericCacheRepository, 3, 50, 1.5);
    }

    @Test
    public void testGetListByUserName() throws Exception {
        String jsonResponse = "{\"data\":[]}";

        when(genericCacheRepository.findById("userlist:testUser:completed")).thenReturn(Optional.empty());
        when(apiKeyClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(String.class)).thenReturn(responseEntity);
        when(responseEntity.getBody()).thenReturn(jsonResponse);

        AnimeList result = userListService.getListByUserName("testUser", Status.COMPLETED);

        assertNotNull(result);
        assertNotNull(result.getData());
        verify(apiKeyClient, times(1)).get();
    }

    @Test
    public void testGetListByUserNameCacheHit() throws Exception {
        String jsonResponse = "{\"data\":[]}";
        GenericCacheEntity cached = new GenericCacheEntity();
        cached.setCacheKey("userlist:testUser:completed");
        cached.setCacheValue(jsonResponse);
        cached.setCachedAt(LocalDateTime.now());

        when(genericCacheRepository.findById("userlist:testUser:completed")).thenReturn(Optional.of(cached));

        AnimeList result = userListService.getListByUserName("testUser", Status.COMPLETED);

        assertNotNull(result);
        assertNotNull(result.getData());
        verify(apiKeyClient, times(0)).get(); // should not call MAL
    }

    @Test
    public void testGetListByUserNameFallbackToExpiredCacheOnFailure() throws Exception {
        String jsonResponse = "{\"data\":[]}";
        GenericCacheEntity cached = new GenericCacheEntity();
        cached.setCacheKey("userlist:testUser:completed");
        cached.setCacheValue(jsonResponse);
        cached.setCachedAt(LocalDateTime.now().minusDays(3)); // expired

        when(genericCacheRepository.findById("userlist:testUser:completed")).thenReturn(Optional.of(cached));
        when(apiKeyClient.get()).thenThrow(new RuntimeException("API is down"));

        AnimeList result = userListService.getListByUserName("testUser", Status.COMPLETED);

        assertNotNull(result);
        assertNotNull(result.getData()); // should fallback to the expired cache content
    }
    @Test
    void synchronizationBypassesFreshCacheAndDoesNotFallbackAfterFailure() {
        when(apiKeyClient.get()).thenThrow(new IllegalStateException("offline"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> userListService.getListByUserName("@me", Status.COMPLETED, true));
        verify(apiKeyClient, atLeastOnce()).get();
        verify(genericCacheRepository, never()).findById(any());
        verify(genericCacheRepository, never()).save(any());
    }
}
