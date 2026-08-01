package com.planmate.community.common.client;

import build.buf.gen.planmate.internal.v1.GetUsersRequest;
import build.buf.gen.planmate.internal.v1.GetUsersResponse;
import build.buf.gen.planmate.internal.v1.InternalUser;
import build.buf.gen.planmate.internal.v1.InternalUserServiceGrpc;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

/**
 * UserClient의 gRPC 전환 검증.
 *
 * 캐시 정책(히트 시 미호출, 실패 시 fallback, 공개여부는 실패 시 비공개)은 전환 전후로
 * 동일하게 유지되어야 하는 계약이라 여기서 함께 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserClientTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private Server server;
    private ManagedChannel channel;
    private UserClient userClient;

    /** 서버가 받은 요청과 호출 횟수 — 캐시 히트 시 호출이 없는지 확인하는 데 쓴다. */
    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicReference<GetUsersRequest> lastRequest = new AtomicReference<>();
    /** 테스트마다 서버 동작을 바꾼다. null이면 UNAVAILABLE을 던진다. */
    private final AtomicReference<List<InternalUser>> serverResponse = new AtomicReference<>(List.of());

    @BeforeEach
    void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new InternalUserServiceGrpc.InternalUserServiceImplBase() {
                    @Override
                    public void getUsers(GetUsersRequest request,
                                         StreamObserver<GetUsersResponse> observer) {
                        callCount.incrementAndGet();
                        lastRequest.set(request);

                        List<InternalUser> users = serverResponse.get();
                        if (users == null) {
                            observer.onError(Status.UNAVAILABLE.asRuntimeException());
                            return;
                        }
                        observer.onNext(GetUsersResponse.newBuilder().addAllUsers(users).build());
                        observer.onCompleted();
                    }
                })
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        userClient = new UserClient(
                InternalUserServiceGrpc.newBlockingStub(channel),
                redisTemplate,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    private static InternalUser user(UUID id, String nickname, boolean profilePublic,
                                     String imageUrl, String avatarHash) {
        return InternalUser.newBuilder()
                .setUserId(id.toString())
                .setNickname(nickname)
                .setProfilePublic(profilePublic)
                .setProfileImageUrl(imageUrl)
                .setAvatarHash(avatarHash)
                .build();
    }

    @Test
    @DisplayName("캐시 미스면 gRPC로 조회하고 결과를 캐시에 기록한다")
    void fetchesOnCacheMissAndWritesCache() {
        UUID userId = UUID.randomUUID();
        given(valueOperations.multiGet(anyList())).willReturn(Collections.singletonList((String) null));
        serverResponse.set(List.of(user(userId, "홍길동", true, "https://img/1.png", "abc")));

        Map<UUID, AuthorProfile> result = userClient.getAuthors(List.of(userId));

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(lastRequest.get().getUserIdsList()).containsExactly(userId.toString());
        assertThat(result.get(userId).nickname()).isEqualTo("홍길동");
        assertThat(result.get(userId).profileImageUrl()).isEqualTo("https://img/1.png");
    }

    @Test
    @DisplayName("캐시가 히트하면 gRPC를 호출하지 않는다")
    void skipsGrpcOnCacheHit() {
        UUID userId = UUID.randomUUID();
        given(valueOperations.multiGet(anyList()))
                .willReturn(List.of("{\"nickname\":\"캐시된닉\",\"profileImageUrl\":null,\"avatarHash\":null}"));

        Map<UUID, AuthorProfile> result = userClient.getAuthors(List.of(userId));

        assertThat(callCount.get()).isZero();
        assertThat(result.get(userId).nickname()).isEqualTo("캐시된닉");
    }

    @Test
    @DisplayName("빈 문자열로 내려온 프로필 이미지·아바타 해시는 null로 되돌린다")
    void convertsEmptyStringToNull() {
        UUID userId = UUID.randomUUID();
        given(valueOperations.multiGet(anyList())).willReturn(Collections.singletonList((String) null));
        serverResponse.set(List.of(user(userId, "익명", true, "", "")));

        AuthorProfile profile = userClient.getAuthor(userId).orElseThrow();

        // 그대로 두면 프론트가 빈 URL로 깨진 이미지를 그리고 이니셜 fallback이 동작하지 않는다
        assertThat(profile.profileImageUrl()).isNull();
        assertThat(profile.avatarHash()).isNull();
    }

    @Test
    @DisplayName("gRPC 호출이 실패하면 빈 결과를 반환한다(호출부가 스냅샷으로 fallback)")
    void returnsEmptyOnGrpcFailure() {
        UUID userId = UUID.randomUUID();
        given(valueOperations.multiGet(anyList())).willReturn(Collections.singletonList((String) null));
        serverResponse.set(null);

        Map<UUID, AuthorProfile> result = userClient.getAuthors(List.of(userId));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 결과에서 빠진다")
    void omitsUnknownUser() {
        given(valueOperations.multiGet(anyList())).willReturn(Collections.singletonList((String) null));
        serverResponse.set(List.of());

        Optional<AuthorProfile> result = userClient.getAuthor(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("공개 프로필이면 true를 반환한다")
    void profilePublicTrue() {
        UUID userId = UUID.randomUUID();
        given(valueOperations.get(UserClient.VISIBILITY_CACHE_KEY_PREFIX + userId)).willReturn(null);
        serverResponse.set(List.of(user(userId, "공개", true, "", "")));

        assertThat(userClient.isProfilePublic(userId)).isTrue();
    }

    @Test
    @DisplayName("조회에 실패하면 비공개로 간주한다 — 백엔드 장애로 비공개 프로필이 노출되면 안 된다")
    void profilePublicFalseOnFailure() {
        UUID userId = UUID.randomUUID();
        given(valueOperations.get(UserClient.VISIBILITY_CACHE_KEY_PREFIX + userId)).willReturn(null);
        serverResponse.set(null);

        assertThat(userClient.isProfilePublic(userId)).isFalse();
    }
}
