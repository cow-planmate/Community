package com.planmate.community.common.client;

import build.buf.gen.planmate.internal.v1.GetUsersRequest;
import build.buf.gen.planmate.internal.v1.GetUsersResponse;
import build.buf.gen.planmate.internal.v1.InternalUser;
import build.buf.gen.planmate.internal.v1.InternalUserServiceGrpc;
import com.planmate.community.common.user.ReplicatedUser;
import com.planmate.community.common.user.ReplicatedUserRepository;
import com.planmate.community.common.user.UserProjectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 작성자 정보 조회.
 *
 * 예전에는 메인 백엔드에 gRPC로 물어보고 Redis에 TTL 캐싱했다. 이제는 로컬 복제 테이블
 * (community_user)을 읽는다 — 같은 DB 안에 있으니 SQL 필터에 쓸 수 있고, 메인 백엔드가
 * 죽어도 답할 수 있다. 캐시 무효화라는 문제 자체가 사라졌다.
 *
 * gRPC 경로는 두 경우에만 남아 있다.
 *   1) 초기 복제가 아직 안 끝난 구간 — 이때 동작은 이 기능이 들어오기 전과 완전히 같다.
 *   2) 복제본에 없는 사용자(가입 직후의 아주 짧은 창) — 한 번 물어보고 끝낸다.
 */
@Slf4j
@Component
public class UserClient {

    // 메인 백엔드가 멈춰도 커뮤니티 요청 스레드가 붙잡히지 않도록 호출마다 건다
    private static final long CALL_TIMEOUT_SECONDS = 2;

    private final InternalUserServiceGrpc.InternalUserServiceBlockingStub stub;
    private final ReplicatedUserRepository replicatedUserRepository;
    private final UserProjectionService projectionService;

    public UserClient(
            InternalUserServiceGrpc.InternalUserServiceBlockingStub internalUserBlockingStub,
            ReplicatedUserRepository replicatedUserRepository,
            UserProjectionService projectionService
    ) {
        this.stub = internalUserBlockingStub;
        this.replicatedUserRepository = replicatedUserRepository;
        this.projectionService = projectionService;
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

        List<UUID> misses = new ArrayList<>();
        if (projectionService.isReady()) {
            for (ReplicatedUser user : replicatedUserRepository.findAllById(distinctIds)) {
                result.put(user.getUserId(), toProfile(user));
            }
            distinctIds.stream().filter(id -> !result.containsKey(id)).forEach(misses::add);
        } else {
            // 초기 복제 전 — 예전처럼 원격 조회로 떨어진다
            misses.addAll(distinctIds);
        }

        if (!misses.isEmpty()) {
            fetchUsers(misses).forEach((id, user) -> result.put(id, toProfile(user)));
        }

        // 여기서도 못 찾은 사용자는 결과에서 빠진다. 호출부는 게시글에 저장된 닉네임 스냅샷으로
        // fallback 한다 — "없음"을 실패가 아니라 열화 표시로 다루는 게 맞다.
        return result;
    }

    /**
     * 프로필 공개 여부. 닉네임과 달리 <b>모르면 비공개로 간주</b>한다 —
     * 판단이 안 서는 상황에서 비공개 프로필이 노출되는 쪽이 훨씬 나쁘다.
     *
     * 복제본이 준비된 뒤에는 행이 없다는 것이 곧 "그런 사용자가 없다"는 뜻이므로 false 가 맞고,
     * 준비 전에는 예전처럼 원격 조회 실패를 비공개로 처리한다.
     *
     * 복제본을 읽게 되면서 메인 백엔드 장애 중에도 <b>올바른 답</b>을 준다 —
     * 예전에는 장애 때 이 메서드가 전부 false 를 반환해 프로필 관련 요청이 통째로 403 이 됐다.
     */
    public boolean isProfilePublic(UUID userId) {
        if (projectionService.isReady()) {
            return replicatedUserRepository.findById(userId)
                    .map(user -> user.isProfilePublic() && !user.isDeleted())
                    .orElse(false);
        }

        InternalUser user = fetchUsers(List.of(userId)).get(userId);
        // 조회 실패/사용자 없음 — 비공개로 처리한다
        return user != null && user.getProfilePublic();
    }

    /**
     * 작성자 속성으로 SQL 필터를 걸어도 되는 상태인지.
     *
     * 초기 복제가 끝나기 전에 필터를 걸면 아직 복제되지 않은 작성자의 글이 통째로 사라진다 —
     * 에러 없이 결과만 틀리므로 눈치채기 어렵다. 그래서 준비 전에는 필터를 끄고 기존 동작으로 둔다.
     */
    public boolean isAuthorSearchAvailable() {
        return projectionService.isReady();
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

    /** 복제본은 이미 null 규약(탈퇴 시 전부 null)을 지키므로 그대로 옮긴다. */
    private AuthorProfile toProfile(ReplicatedUser user) {
        if (user.isDeleted()) {
            return AuthorProfile.ofDeleted();
        }
        return new AuthorProfile(
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getAvatarHash(),
                false);
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
}
