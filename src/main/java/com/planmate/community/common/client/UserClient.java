package com.planmate.community.common.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 메인 백엔드의 내부 사용자 조회 API 클라이언트.
 * Redis 캐시(TTL 10분, MGET/파이프라인 일괄 접근) → 내부 API 순으로 조회하며, 실패 시 빈 결과를 반환한다
 * (호출부는 게시글에 저장된 닉네임 스냅샷으로 fallback).
 *
 * 캐시 키는 메인 백엔드의 CommunityUserCacheEvictor와 맞물린 서비스 간 계약이다 —
 * prefix를 바꾸면 프로필 변경 시 즉시 무효화가 조용히 동작하지 않게 되므로 양쪽을 함께 고쳐야 한다.
 */
@Slf4j
@Component
public class UserClient {

    private static final String CACHE_KEY_PREFIX = "community:user:profile:";
    private static final String VISIBILITY_CACHE_KEY_PREFIX = "community:user:profile-public:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    // 공개 설정을 껐을 때 반영이 10분이나 늦으면 안 되므로 닉네임보다 짧게 캐싱한다
    private static final Duration VISIBILITY_CACHE_TTL = Duration.ofMinutes(1);

    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String internalApiToken;

    public UserClient(
            RestClient.Builder restClientBuilder,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${internal.api-base-url}") String apiBaseUrl,
            @Value("${internal.api-token}") String internalApiToken
    ) {
        this.restClient = restClientBuilder.baseUrl(apiBaseUrl).build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.internalApiToken = internalApiToken;
    }

    public Optional<AuthorProfile> getAuthor(UUID userId) {
        return Optional.ofNullable(getAuthors(List.of(userId)).get(userId));
    }

    public Map<UUID, AuthorProfile> getAuthors(Collection<UUID> userIds) {
        Map<UUID, AuthorProfile> result = new HashMap<>();
        List<UUID> distinctIds = new ArrayList<>(new LinkedHashSet<>(userIds));
        if (distinctIds.isEmpty()) {
            return result;
        }

        List<UUID> cacheMisses = new ArrayList<>();
        List<String> cached = readCache(distinctIds);
        for (int i = 0; i < distinctIds.size(); i++) {
            UUID id = distinctIds.get(i);
            AuthorProfile profile = deserialize(cached.get(i));
            if (profile != null) {
                result.put(id, profile);
            } else {
                cacheMisses.add(id);
            }
        }

        if (!cacheMisses.isEmpty()) {
            Map<UUID, AuthorProfile> fetched = fetchUsers(cacheMisses).entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> toProfile(entry.getValue())));
            result.putAll(fetched);
            writeCache(fetched);
        }

        return result;
    }

    /**
     * 프로필 공개 여부. 닉네임과 달리 조회에 실패하면 <b>비공개로 간주</b>한다 —
     * 메인 백엔드가 잠시 응답하지 않는다고 비공개 프로필이 노출되면 안 된다.
     */
    public boolean isProfilePublic(UUID userId) {
        String cached = readVisibilityCache(userId);
        if (cached != null) {
            return "1".equals(cached);
        }

        InternalUserResponse user = fetchUsers(List.of(userId)).get(userId);
        if (user == null) {
            // 조회 실패/사용자 없음 — 캐싱하지 않고(다음 요청에서 재시도) 비공개로 처리한다
            return false;
        }

        writeVisibilityCache(userId, user.profilePublic());
        return user.profilePublic();
    }

    private Map<UUID, InternalUserResponse> fetchUsers(List<UUID> userIds) {
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
                    .collect(Collectors.toMap(InternalUserResponse::userId, user -> user));
        } catch (Exception e) {
            log.warn("내부 사용자 API 호출 실패 (ids={}): {}", ids, e.getMessage());
            return Map.of();
        }
    }

    private String readVisibilityCache(UUID userId) {
        try {
            return redisTemplate.opsForValue().get(VISIBILITY_CACHE_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("프로필 공개 여부 캐시 조회 실패 (userId={}): {}", userId, e.getMessage());
            return null;
        }
    }

    private void writeVisibilityCache(UUID userId, boolean profilePublic) {
        try {
            redisTemplate.opsForValue()
                    .set(VISIBILITY_CACHE_KEY_PREFIX + userId, profilePublic ? "1" : "0", VISIBILITY_CACHE_TTL);
        } catch (Exception e) {
            log.warn("프로필 공개 여부 캐시 저장 실패 (userId={}): {}", userId, e.getMessage());
        }
    }

    // MGET 일괄 조회 — 결과 리스트는 요청 순서와 동일, null이면 캐시 미스
    private List<String> readCache(List<UUID> userIds) {
        List<String> keys = userIds.stream().map(id -> CACHE_KEY_PREFIX + id).toList();
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values != null && values.size() == userIds.size()) {
                return values;
            }
        } catch (Exception e) {
            log.warn("작성자 프로필 캐시 일괄 조회 실패 (count={}): {}", userIds.size(), e.getMessage());
        }
        return Collections.<String>nCopies(userIds.size(), null);
    }

    // 파이프라인 일괄 저장 — 캐시 저장 실패가 요청을 실패시키지 않는다
    private void writeCache(Map<UUID, AuthorProfile> profiles) {
        if (profiles.isEmpty()) {
            return;
        }
        Map<UUID, String> serialized = new HashMap<>();
        profiles.forEach((userId, profile) -> {
            String json = serialize(profile);
            if (json != null) {
                serialized.put(userId, json);
            }
        });
        if (serialized.isEmpty()) {
            return;
        }

        try {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Map.Entry<UUID, String> entry : serialized.entrySet()) {
                    connection.stringCommands().set(
                            (CACHE_KEY_PREFIX + entry.getKey()).getBytes(StandardCharsets.UTF_8),
                            entry.getValue().getBytes(StandardCharsets.UTF_8),
                            Expiration.from(CACHE_TTL),
                            RedisStringCommands.SetOption.UPSERT
                    );
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("작성자 프로필 캐시 일괄 저장 실패 (count={}): {}", serialized.size(), e.getMessage());
        }
    }

    private AuthorProfile toProfile(InternalUserResponse user) {
        return new AuthorProfile(user.nickname(), user.profileImageUrl(), user.avatarHash());
    }

    private String serialize(AuthorProfile profile) {
        try {
            return objectMapper.writeValueAsString(profile);
        } catch (Exception e) {
            log.warn("작성자 프로필 직렬화 실패 (nickname={}): {}", profile.nickname(), e.getMessage());
            return null;
        }
    }

    // 캐시 미스(null)와 깨진 값 모두 null로 취급해 내부 API 재조회로 넘긴다
    private AuthorProfile deserialize(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AuthorProfile.class);
        } catch (Exception e) {
            log.warn("작성자 프로필 역직렬화 실패: {}", e.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InternalUserResponse(
            UUID userId,
            String nickname,
            boolean profilePublic,
            String profileImageUrl,
            String avatarHash
    ) {
    }
}
