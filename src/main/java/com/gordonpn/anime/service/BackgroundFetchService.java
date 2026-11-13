package com.gordonpn.anime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gordonpn.anime.config.AuthorizationState;
import com.gordonpn.anime.entity.SyncStatusEntity;
import com.gordonpn.anime.entity.UserAnimeListEntity;
import com.gordonpn.anime.models.AnimeList;
import com.gordonpn.anime.models.Datum;
import com.gordonpn.anime.models.ListStatusType;
import com.gordonpn.anime.models.dto.TokenResponse;
import com.gordonpn.anime.models.userlist.Status;
import com.gordonpn.anime.repository.SyncStatusRepository;
import com.gordonpn.anime.repository.TokenRepository;
import com.gordonpn.anime.repository.UserAnimeListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class BackgroundFetchService {
    private static final long ONE_HOUR_IN_MILLIS = 60 * 60 * 1000L;
    private final UserListService userListService;
    private final AuthorizationState authorizationState;
    private final TokenExchangeService tokenExchangeService;
    private final TokenRepository tokenRepository;
    private final UserAnimeListRepository userAnimeListRepository;
    private final SyncStatusRepository syncStatusRepository;

    @Scheduled(fixedDelay = ONE_HOUR_IN_MILLIS)
    public void fetchList() {
        TokenResponse tokenResponse = authorizationState.getTokenResponse();
        if (tokenResponse == null) {
            log.warn("No active authorization token found. Skipping synchronization. Please run 'login' to authenticate.");
            return;
        }
        TokenResponse tokens = tokenExchangeService.refreshToken(tokenResponse);
        if (tokens == null) {
            log.warn("Token refresh failed. Skipping synchronization.");
            return;
        }
        authorizationState.setTokenResponse(tokens);
        
        com.gordonpn.anime.entity.TokenEntity tokenEntity = new com.gordonpn.anime.entity.TokenEntity();
        tokenEntity.setAccessToken(tokens.getAccessToken());
        tokenEntity.setRefreshToken(tokens.getRefreshToken());
        tokenEntity.setExpiresAt(tokens.getExpiresAt());
        tokenRepository.save(tokenEntity);

        java.util.Set<Integer> fetchedAnimeIds = new java.util.HashSet<>();

        for (Status status : Status.values()) {
            log.info("Fetching anime list for status: {}", status.getType());
            AnimeList listByUserName = userListService.getListByUserName("@me", status);
            if (listByUserName == null || listByUserName.getData() == null) {
                log.warn("No anime list data to save");
                continue;
            }
            ListStatusType listStatusType = ListStatusType.fromValue(status.getType());
            log.debug("Converting status from {} to ListStatusType {}", status.getType(), listStatusType);
            for (Datum datum : listByUserName.getData()) {
                log.debug("Processing anime: {}. Status: {}", datum.toString(), listStatusType);
                Integer animeId = datum.getNode().getId();
                if (animeId == null || animeId <= 0) {
                    log.warn("Skipping list entry with invalid anime ID: {}", animeId);
                    continue;
                }
                fetchedAnimeIds.add(animeId);

                UserAnimeListEntity entity = userAnimeListRepository.findByAnimeId(animeId)
                        .orElse(new UserAnimeListEntity());
                entity.setAnimeId(animeId);
                entity.setStatus(listStatusType);
                if (datum.getListStatus() != null) {
                    entity.setUserScore(datum.getListStatus().getScore());
                    String updatedAtStr = datum.getListStatus().getUpdatedAt();
                    if (updatedAtStr != null && !updatedAtStr.isEmpty()) {
                        try {
                            entity.setUpdatedAt(java.time.OffsetDateTime.parse(updatedAtStr).toLocalDateTime());
                        } catch (Exception e) {
                            log.warn("Failed to parse updatedAt string '{}' for anime ID {}: {}", updatedAtStr, animeId, e.getMessage());
                        }
                    }
                }

                userAnimeListRepository.save(entity);
                log.debug("Saved/updated anime in user list table: {} (ID: {})", datum.getNode().getTitle(), animeId);
            }
            LocalDateTime now = LocalDateTime.now();
            SyncStatusEntity syncStatus = syncStatusRepository.findById(listStatusType.getValue())
                    .orElse(new SyncStatusEntity());
            syncStatus.setListType(listStatusType.getValue());
            syncStatus.setLastSyncedAt(now);
            syncStatusRepository.save(syncStatus);
            log.debug("Updated sync status timestamp for: {}", listStatusType);
            log.info("Saved {} anime entries", listByUserName.getData().size());
        }

        // Delete any local user list items that are no longer in the user's MAL lists
        java.util.List<UserAnimeListEntity> allLocal = userAnimeListRepository.findAll();
        for (UserAnimeListEntity local : allLocal) {
            if (!fetchedAnimeIds.contains(local.getAnimeId())) {
                log.info("Deleting anime ID {} from local list as it is no longer on MAL", local.getAnimeId());
                userAnimeListRepository.delete(local);
            }
        }
    }

    public void fetchAnimeAnAnime() {

    }
}
