package com.planmate.community.common.client;

import build.buf.gen.planmate.internal.v1.GetUsersRequest;
import build.buf.gen.planmate.internal.v1.GetUsersResponse;
import build.buf.gen.planmate.internal.v1.InternalUser;
import build.buf.gen.planmate.internal.v1.InternalUserServiceGrpc;
import com.planmate.community.common.user.ReplicatedUser;
import com.planmate.community.common.user.ReplicatedUserRepository;
import com.planmate.community.common.user.UserProjectionService;
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
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

/**
 * UserClient의 읽기 모델 전환 검증.
 *
 * 핵심 계약 두 가지를 고정한다.
 *   1) 복제본이 준비되면 원격 호출을 하지 않는다 — 메인 백엔드가 죽어도 답할 수 있어야 한다.
 *   2) 실패 폴라리티가 필드마다 반대다. 표시는 fail-open(스냅샷으로 떨어짐),
 *      공개 여부는 fail-closed(모르면 비공개).
 * 초기 복제 전 동작이 전환 이전과 같다는 것도 함께 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserClientTest {

    @Mock
    private ReplicatedUserRepository replicatedUserRepository;

    @Mock
    private UserProjectionService projectionService;

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

        userClient = new UserClient(
                InternalUserServiceGrpc.newBlockingStub(channel),
                replicatedUserRepository,
                projectionService);
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

    /** 복제본 행 하나. 엔티티에 변경자가 없으므로(기록은 업서트가 유일하다) 리플렉션으로 채운다. */
    private static ReplicatedUser replicated(UUID id, String nickname, boolean profilePublic,
                                             String imageUrl, String avatarHash, boolean deleted) {
        // 기본 생성자가 protected 다 — 이 엔티티는 업서트로만 만들어지므로 외부 생성을 막아뒀다
        ReplicatedUser user = BeanUtils.instantiateClass(ReplicatedUser.class);
        ReflectionTestUtils.setField(user, "userId", id);
        ReflectionTestUtils.setField(user, "nickname", nickname);
        ReflectionTestUtils.setField(user, "profilePublic", profilePublic);
        ReflectionTestUtils.setField(user, "profileImageUrl", imageUrl);
        ReflectionTestUtils.setField(user, "avatarHash", avatarHash);
        ReflectionTestUtils.setField(user, "deleted", deleted);
        return user;
    }

    @Test
    @DisplayName("복제본이 준비되면 원격 호출 없이 로컬에서 답한다")
    void readsFromProjectionWithoutGrpc() {
        UUID userId = UUID.randomUUID();
        given(projectionService.isReady()).willReturn(true);
        given(replicatedUserRepository.findAllById(anyCollection()))
                .willReturn(List.of(replicated(userId, "홍길동", true, "https://img/1.png", "abc", false)));

        Map<UUID, AuthorProfile> result = userClient.getAuthors(List.of(userId));

        // 이게 이번 전환의 핵심이다 — 메인 백엔드가 죽어도 목록이 정상 렌더링되어야 한다
        assertThat(callCount.get()).isZero();
        assertThat(result.get(userId).nickname()).isEqualTo("홍길동");
        assertThat(result.get(userId).profileImageUrl()).isEqualTo("https://img/1.png");
    }

    @Test
    @DisplayName("복제본에 없는 사용자만 원격으로 한 번 더 물어본다")
    void fallsBackToGrpcForMissingRow() {
        UUID cached = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        given(projectionService.isReady()).willReturn(true);
        given(replicatedUserRepository.findAllById(anyCollection()))
                .willReturn(List.of(replicated(cached, "복제됨", true, null, null, false)));
        serverResponse.set(List.of(user(missing, "갓가입", true, "", "")));

        Map<UUID, AuthorProfile> result = userClient.getAuthors(List.of(cached, missing));

        // 가입 직후의 짧은 창 — 복제가 아직 안 닿은 사용자만 물어본다
        assertThat(lastRequest.get().getUserIdsList()).containsExactly(missing.toString());
        assertThat(result.get(cached).nickname()).isEqualTo("복제됨");
        assertThat(result.get(missing).nickname()).isEqualTo("갓가입");
    }

    @Test
    @DisplayName("초기 복제 전에는 예전처럼 원격 조회로 떨어진다")
    void usesGrpcBeforeInitialReplication() {
        UUID userId = UUID.randomUUID();
        given(projectionService.isReady()).willReturn(false);
        serverResponse.set(List.of(user(userId, "홍길동", true, "https://img/1.png", "abc")));

        Map<UUID, AuthorProfile> result = userClient.getAuthors(List.of(userId));

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(result.get(userId).nickname()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("탈퇴 계정은 '탈퇴한 사용자'로 표시된다")
    void deletedUserRendersAsWithdrawn() {
        UUID userId = UUID.randomUUID();
        given(projectionService.isReady()).willReturn(true);
        given(replicatedUserRepository.findAllById(anyCollection()))
                .willReturn(List.of(replicated(userId, null, false, null, null, true)));

        AuthorProfile profile = userClient.getAuthor(userId).orElseThrow();

        assertThat(profile.deleted()).isTrue();
    }

    @Test
    @DisplayName("빈 문자열로 내려온 프로필 이미지·아바타 해시는 null로 되돌린다")
    void convertsEmptyStringToNull() {
        UUID userId = UUID.randomUUID();
        given(projectionService.isReady()).willReturn(false);
        serverResponse.set(List.of(user(userId, "익명", true, "", "")));

        AuthorProfile profile = userClient.getAuthor(userId).orElseThrow();

        // 그대로 두면 프론트가 빈 URL로 깨진 이미지를 그리고 이니셜 fallback이 동작하지 않는다
        assertThat(profile.profileImageUrl()).isNull();
        assertThat(profile.avatarHash()).isNull();
    }

    @Test
    @DisplayName("조회에 실패하면 빈 결과를 반환한다(호출부가 스냅샷으로 fallback)")
    void returnsEmptyOnGrpcFailure() {
        UUID userId = UUID.randomUUID();
        given(projectionService.isReady()).willReturn(false);
        serverResponse.set(null);

        Map<UUID, AuthorProfile> result = userClient.getAuthors(List.of(userId));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 결과에서 빠진다")
    void omitsUnknownUser() {
        given(projectionService.isReady()).willReturn(false);
        serverResponse.set(List.of());

        Optional<AuthorProfile> result = userClient.getAuthor(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("공개 프로필이면 true를 반환한다")
    void profilePublicTrue() {
        UUID userId = UUID.randomUUID();
        given(projectionService.isReady()).willReturn(true);
        given(replicatedUserRepository.findById(userId))
                .willReturn(Optional.of(replicated(userId, "공개", true, null, null, false)));

        assertThat(userClient.isProfilePublic(userId)).isTrue();
    }

    @Test
    @DisplayName("복제본에 없는 사용자는 비공개로 간주한다 — 모르면 감춘다")
    void profilePublicFalseWhenRowMissing() {
        UUID userId = UUID.randomUUID();
        given(projectionService.isReady()).willReturn(true);
        given(replicatedUserRepository.findById(userId)).willReturn(Optional.empty());

        assertThat(userClient.isProfilePublic(userId)).isFalse();
    }

    @Test
    @DisplayName("탈퇴 계정은 공개 설정과 무관하게 비공개로 간주한다")
    void profilePublicFalseWhenDeleted() {
        UUID userId = UUID.randomUUID();
        given(projectionService.isReady()).willReturn(true);
        given(replicatedUserRepository.findById(userId))
                .willReturn(Optional.of(replicated(userId, null, true, null, null, true)));

        assertThat(userClient.isProfilePublic(userId)).isFalse();
    }

    @Test
    @DisplayName("초기 복제 전 조회에 실패하면 비공개로 간주한다 — 장애로 비공개 프로필이 노출되면 안 된다")
    void profilePublicFalseOnFailure() {
        UUID userId = UUID.randomUUID();
        given(projectionService.isReady()).willReturn(false);
        serverResponse.set(null);

        assertThat(userClient.isProfilePublic(userId)).isFalse();
    }
}
