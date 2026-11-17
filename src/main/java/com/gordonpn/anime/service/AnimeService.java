package com.gordonpn.anime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gordonpn.anime.entity.AnimeCacheEntity;
import com.gordonpn.anime.entity.GenericCacheEntity;
import com.gordonpn.anime.models.exception.AnimeDataProcessingException;
import com.gordonpn.anime.models.AnimeDetails;
import com.gordonpn.anime.models.exception.MalResourceNotFoundException;
import com.gordonpn.anime.models.SeasonalAnime;
import com.gordonpn.anime.repository.AnimeCacheRepository;
import com.gordonpn.anime.repository.GenericCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class AnimeService {

    private final ObjectMapper objectMapper;
    private final RestClient apiKeyClient;
    private final AnimeCacheRepository animeCacheRepository;
    private final GenericCacheRepository genericCacheRepository;

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final double backoffMultiplier;

    public AnimeService(
            @Qualifier("objectMapper") ObjectMapper objectMapper,
            RestClient apiKeyClient,
            AnimeCacheRepository animeCacheRepository,
            GenericCacheRepository genericCacheRepository,
            @Value("${mal.retry.max-attempts:5}") int maxAttempts,
            @Value("${mal.retry.initial-backoff-ms:1000}") long initialBackoffMs,
            @Value("${mal.retry.backoff-multiplier:2.0}") double backoffMultiplier
    ) {
        this.objectMapper = objectMapper;
        this.apiKeyClient = apiKeyClient;
        this.animeCacheRepository = animeCacheRepository;
        this.genericCacheRepository = genericCacheRepository;
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
    }

    public AnimeDetails getAnimeById(int animeId) {
        if (animeId <= 0) {
            throw new IllegalArgumentException("animeId must be positive");
        }
        Optional<AnimeCacheEntity> cachedOpt = animeCacheRepository.findById(animeId);
        if (cachedOpt.isPresent()) {
            AnimeCacheEntity cached = cachedOpt.get();
            if (cached.getCachedAt().isAfter(LocalDateTime.now().minusDays(7))) {
                log.debug("Cache hit for anime ID: {}", animeId);
                return parseJson(cached.getDetailsJson(), AnimeDetails.class);
            }
            log.info("Cache expired for anime ID: {}", animeId);
        }

        log.info("Cache miss or expired for anime ID: {}. Fetching from MAL", animeId);
        long backoffMs = initialBackoffMs;
        boolean infinite = maxAttempts <= 0;
        for (int attempt = 1; infinite || attempt <= maxAttempts; attempt++) {
            try {
                String body = apiKeyClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/anime/{animeId}")
                                .queryParam("fields", "id,title,recommendations,mean,popularity,related_anime,alternative_titles,start_date")
                                .build(animeId))
                        .retrieve()
                        .toEntity(String.class)
                        .getBody();

                if (body == null || body.trim().startsWith("<")) {
                    throw new RuntimeException("MAL API returned an HTML page (likely a rate-limit or captcha challenge) instead of a JSON payload.");
                }

                AnimeDetails animeDetails = parseJson(body, AnimeDetails.class);
                log.info("Anime details fetched: {}", animeDetails.getDisplayTitle());

                // Save/Update to cache on success
                AnimeCacheEntity cacheEntry = cachedOpt.orElse(new AnimeCacheEntity());
                cacheEntry.setAnimeId(animeId);
                cacheEntry.setTitle(animeDetails.getDisplayTitle() != null ? animeDetails.getDisplayTitle() : "Unknown");
                cacheEntry.setDetailsJson(body);
                cacheEntry.setCachedAt(LocalDateTime.now());
                animeCacheRepository.save(cacheEntry);

                return animeDetails;
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                log.error("Anime ID {} not found on MyAnimeList (404 Not Found): {}", animeId, e.getMessage());
                throw new MalResourceNotFoundException("Anime ID " + animeId + " not found on MyAnimeList", e);
            } catch (Exception e) {
                boolean shouldRetry = infinite || (attempt < maxAttempts);
                if (shouldRetry) {
                    String attemptStr = infinite ? String.valueOf(attempt) : (attempt + " of " + maxAttempts);
                    log.warn("Attempt {} failed to fetch details for anime ID {} due to: {}. Retrying in {}ms...",
                            attemptStr, animeId, e.getMessage(), backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry wait interrupted", ie);
                    }
                    backoffMs = Math.min(15000L, (long) (backoffMs * backoffMultiplier));
                } else {
                    log.error("Failed to fetch or parse anime ID {} from MAL after {} attempts: {}", animeId, maxAttempts, e.getMessage());
                    if (cachedOpt.isPresent()) {
                        log.warn("Falling back to expired cache for anime ID: {}", animeId);
                        try {
                            return parseJson(cachedOpt.get().getDetailsJson(), AnimeDetails.class);
                        } catch (Exception ex) {
                            log.error("Failed to parse expired cache for anime ID {}: {}", animeId, ex.getMessage());
                        }
                    }
                    throw e;
                }
            }
        }
        throw new RuntimeException("Retry limit exceeded for anime ID: " + animeId);
    }

    public SeasonalAnime getSeasonalAnime(String season, int year) {
        String cacheKey = "seasonal:" + year + ":" + season;
        Optional<GenericCacheEntity> cachedOpt = genericCacheRepository.findById(cacheKey);

        // Determine TTL (30 days for past years, 1 day for current/future years)
        int currentYear = java.time.LocalDate.now().getYear();
        int ttlDays = (year < currentYear) ? 30 : 1;

        if (cachedOpt.isPresent()) {
            GenericCacheEntity cached = cachedOpt.get();
            if (cached.getCachedAt().isAfter(LocalDateTime.now().minusDays(ttlDays))) {
                log.debug("Cache hit for seasonal anime key: {}", cacheKey);
                return parseJson(cached.getCacheValue(), SeasonalAnime.class);
            }
            log.info("Cache expired for seasonal anime key: {}", cacheKey);
        }

        log.info("Cache miss or expired for seasonal anime key: {}. Fetching from MAL", cacheKey);
        long backoffMs = initialBackoffMs;
        boolean infinite = maxAttempts <= 0;
        for (int attempt = 1; infinite || attempt <= maxAttempts; attempt++) {
            try {
                String body = apiKeyClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/anime/season/{year}/{season}")
                                .queryParam("fields", "id,title,mean,alternative_titles")
                                .queryParam("limit", "500")
                                .queryParam("sort", "anime_score")
                                .build(year, season))
                        .retrieve()
                        .toEntity(String.class)
                        .getBody();

                if (body == null || body.trim().startsWith("<")) {
                    throw new RuntimeException("MAL API returned an HTML page (likely a rate-limit or captcha challenge) instead of a JSON payload.");
                }

                SeasonalAnime seasonalAnime = parseJson(body, SeasonalAnime.class);
                log.debug("Seasonal anime fetched from MAL API and parsed successfully");

                // Save/Update to cache on success
                GenericCacheEntity cacheEntry = cachedOpt.orElse(new GenericCacheEntity());
                cacheEntry.setCacheKey(cacheKey);
                cacheEntry.setCacheValue(body);
                cacheEntry.setCachedAt(LocalDateTime.now());
                genericCacheRepository.save(cacheEntry);

                return seasonalAnime;
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                log.error("Seasonal anime key {} not found on MyAnimeList (404 Not Found): {}", cacheKey, e.getMessage());
                throw new MalResourceNotFoundException("Seasonal anime key " + cacheKey + " not found on MyAnimeList", e);
            } catch (Exception e) {
                boolean shouldRetry = infinite || (attempt < maxAttempts);
                if (shouldRetry) {
                    String attemptStr = infinite ? String.valueOf(attempt) : (attempt + " of " + maxAttempts);
                    log.warn("Attempt {} failed to fetch seasonal anime for key {} due to: {}. Retrying in {}ms...",
                            attemptStr, cacheKey, e.getMessage(), backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry wait interrupted", ie);
                    }
                    backoffMs = Math.min(15000L, (long) (backoffMs * backoffMultiplier));
                } else {
                    log.error("Failed to fetch or parse seasonal anime key {} from MAL after {} attempts: {}", cacheKey, maxAttempts, e.getMessage());
                    if (cachedOpt.isPresent()) {
                        log.warn("Falling back to expired cache for seasonal anime key: {}", cacheKey);
                        try {
                            return parseJson(cachedOpt.get().getCacheValue(), SeasonalAnime.class);
                        } catch (Exception ex) {
                            log.error("Failed to parse expired cache for seasonal anime key {}: {}", cacheKey, ex.getMessage());
                        }
                    }
                    throw e;
                }
            }
        }
        throw new RuntimeException("Retry limit exceeded for seasonal anime key: " + cacheKey);
    }

    private <T> T parseJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new AnimeDataProcessingException("Failed to parse JSON for " + clazz.getSimpleName(), e);
        }
    }
}
