package com.planmate.community.common.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 메인 백엔드의 내부 사용자 조회 API 클라이언트.
 * Redis 캐시(TTL 10분) → 내부 API 순으로 조회하며, 실패 시 빈 결과를 반환한다
 * (호출부는 게시글에 저장된 닉네임 스냅샷으로 fallback).
 */
@Slf4j
@Component
public class UserClient {

    private static final String CACHE_KEY_PREFIX = "community:user:nickname:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final String internalApiToken;

    public UserClient(
            RestClient.Builder restClientBuilder,
            StringRedisTemplate redisTemplate,
            @Value("${internal.api-base-url}") String apiBaseUrl,
            @Value("${internal.api-token}") String internalApiToken
    ) {
        this.restClient = restClientBuilder.baseUrl(apiBaseUrl).build();
        this.redisTemplate = redisTemplate;
        this.internalApiToken = internalApiToken;
    }

    public Optional<String> getNickname(UUID userId) {
        return Optional.ofNullable(getNicknames(List.of(userId)).get(userId));
    }

    public Map<UUID, String> getNicknames(Collection<UUID> userIds) {
        Map<UUID, String> result = new HashMap<>();
        Set<UUID> distinctIds = new LinkedHashSet<>(userIds);
        List<UUID> cacheMisses = new ArrayList<>();

        for (UUID id : distinctIds) {
            String cached = readCache(id);
            if (cached != null) {
                result.put(id, cached);
            } else {
                cacheMisses.add(id);
            }
        }

        if (!cacheMisses.isEmpty()) {
            fetchFromInternalApi(cacheMisses).forEach((id, nickname) -> {
                result.put(id, nickname);
                writeCache(id, nickname);
            });
        }

        return result;
    }

    private Map<UUID, String> fetchFromInternalApi(List<UUID> userIds) {
        String ids = userIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        try {
            List<InternalUserResponse> users = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/internal/users").queryParam("ids", ids).build())
                    .header("X-Internal-Token", internalApiToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (users == null) {
                return Map.of();
            }
            return users.stream()
                    .collect(Collectors.toMap(InternalUserResponse::userId, InternalUserResponse::nickname));
        } catch (Exception e) {
            log.warn("내부 사용자 API 호출 실패 (ids={}): {}", ids, e.getMessage());
            return Map.of();
        }
    }

    private String readCache(UUID userId) {
        try {
            return redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("닉네임 캐시 조회 실패 (userId={}): {}", userId, e.getMessage());
            return null;
        }
    }

    private void writeCache(UUID userId, String nickname) {
        try {
            redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + userId, nickname, CACHE_TTL);
        } catch (Exception e) {
            log.warn("닉네임 캐시 저장 실패 (userId={}): {}", userId, e.getMessage());
        }
    }

    private record InternalUserResponse(UUID userId, String nickname) {
    }
}
