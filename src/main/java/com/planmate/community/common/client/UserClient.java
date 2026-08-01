package com.planmate.community.common.client;

import build.buf.gen.planmate.internal.v1.GetUsersRequest;
import build.buf.gen.planmate.internal.v1.GetUsersResponse;
import build.buf.gen.planmate.internal.v1.InternalUser;
import build.buf.gen.planmate.internal.v1.InternalUserServiceGrpc;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 메인 백엔드의 내부 사용자 조회 gRPC 클라이언트.
 * Redis 캐시(TTL 10분, MGET/파이프라인 일괄 접근) → 내부 API 순으로 조회하며, 실패 시 빈 결과를 반환한다
 * (호출부는 게시글에 저장된 닉네임 스냅샷으로 fallback).
 *
 * 캐시는 이 서비스가 단독으로 소유한다. 프로필이 바뀌면 메인 백엔드가 WatchUserChanges 스트림으로
 * 알려주고 {@link UserChangeSubscriber}가 여기 키를 지운다 — 알림이 끊겨도 TTL로 수렴하므로
 * 정합성이 깨지지는 않고 반영만 늦어진다.
 */
@Slf4j
@Component
public class UserClient {

    static final String CACHE_KEY_PREFIX = "community:user:profile:";
    static final String VISIBILITY_CACHE_KEY_PREFIX = "community:user:profile-public:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    // 공개 설정을 껐을 때 반영이 10분이나 늦으면 안 되므로 닉네임보다 짧게 캐싱한다
    private static final Duration VISIBILITY_CACHE_TTL = Duration.ofMinutes(1);
    // 메인 백엔드가 멈춰도 커뮤니티 요청 스레드가 붙잡히지 않도록 호출마다 건다
    private static final long CALL_TIMEOUT_SECONDS = 2;

    private final InternalUserServiceGrpc.InternalUserServiceBlockingStub stub;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UserClient(
            InternalUserServiceGrpc.InternalUserServiceBlockingStub internalUserBlockingStub,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.stub = internalUserBlockingStub;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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

        InternalUser user = fetchUsers(List.of(userId)).get(userId);
        if (user == null) {
            // 조회 실패/사용자 없음 — 캐싱하지 않고(다음 요청에서 재시도) 비공개로 처리한다
            return false;
        }

        writeVisibilityCache(userId, user.getProfilePublic());
        return user.getProfilePublic();
    }

    private Map<UUID, InternalUser> fetchUsers(List<UUID> userIds) {
        GetUsersRequest request = GetUsersRequest.newBuilder()
                .addAllUserIds(userIds.stream().map(UUID::toString).toList())
                .build();
        try {
            // deadline은 반드시 호출 시점에 건다 — 스텁 생성 시 한 번 걸면 곧 만료된 스텁이 된다
            GetUsersResponse response = stub
                    .withDeadlineAfter(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .getUsers(request);

            return response.getUsersList().stream()
                    .collect(Collectors.toMap(user -> UUID.fromString(user.getUserId()), user -> user));
        } catch (Exception e) {
            log.warn("내부 사용자 gRPC 호출 실패 (count={}): {}", userIds.size(), e.getMessage());
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

    /**
     * proto3에는 null이 없어 미등록 값이 빈 문자열로 내려온다.
     * AuthorProfile은 "없음"을 null로 표현하므로 여기서 되돌린다 —
     * 그대로 두면 프론트가 빈 URL로 깨진 이미지를 그리고 이니셜 fallback이 동작하지 않는다.
     */
    private AuthorProfile toProfile(InternalUser user) {
        // 탈퇴 계정은 서버가 닉네임·아이콘을 비워 보내므로 그대로 쓰면 이름 없는 작성자가 된다
        if (user.getDeleted()) {
            return AuthorProfile.ofDeleted();
        }
        return new AuthorProfile(
                user.getNickname(),
                emptyToNull(user.getProfileImageUrl()),
                emptyToNull(user.getAvatarHash()),
                false);
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
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
}
