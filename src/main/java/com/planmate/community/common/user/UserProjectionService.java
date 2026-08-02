package com.planmate.community.common.user;

import build.buf.gen.planmate.internal.v1.InternalUser;
import build.buf.gen.planmate.internal.v1.WatchUserChangesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 읽기 모델의 유일한 기록 지점.
 *
 * <p>정합성 논거는 두 문장으로 끝난다.
 * <ol>
 *   <li>업서트와 커서 전진이 <b>한 트랜잭션</b>이다 — 반영했는데 커서가 안 올라가거나
 *       그 반대인 상태가 존재할 수 없다.</li>
 *   <li>업서트가 source_seq 로 멱등하다 — 중복 수신과 순서 역전이 무해하다.</li>
 * </ol>
 * 이 둘 덕분에 전달이 at-least-once 이기만 하면 되고, 스트림이 끊기든 재배포로 재연결하든
 * 커서에서 이어받아 정확히 수렴한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProjectionService {

    private final ReplicatedUserRepository userRepository;
    private final UserReplicationStateRepository stateRepository;

    /**
     * 복제본이 쓸 만한 상태인지. 여기서 false 면 신규 필터를 끄고 공개 여부는 원격 조회로
     * 되돌려야 한다 — 불완전한 복제본으로 필터를 걸면 조용히 틀린 목록을 준다.
     *
     * <p>한 방향으로만 바뀌는 값이라 캐시해도 안전하지만, 조회가 PK 한 건이라 굳이 캐시하지
     * 않는다. 호출부가 요청당 한 번씩만 부르므로 비용이 문제되지 않는다.
     */
    @Transactional(readOnly = true)
    public boolean isReady() {
        return state().map(UserReplicationState::isSnapshotComplete).orElse(false);
    }

    /** 다음 연결에서 서버에 보낼 커서. 스냅샷이 끝나지 않았으면 0(= 전체 스냅샷 요청)이다. */
    @Transactional(readOnly = true)
    public long resumeCursor() {
        return state()
                .filter(UserReplicationState::isSnapshotComplete)
                .map(UserReplicationState::getLastAppliedSeq)
                .orElse(0L);
    }

    /**
     * 배치 반영. 업서트와 커서 전진이 같은 트랜잭션이어야 하므로 반드시 여기로 묶어 부른다.
     *
     * @param advanceCursor 스냅샷 단계에서는 false. 스냅샷 도중 커서를 올리면 그 사이에 죽었을 때
     *                      아직 반영하지 않은 사용자를 영영 건너뛴다 —
     *                      커서는 snapshot_complete 를 받은 시점에만 확정한다.
     */
    @Transactional
    public void applyBatch(List<WatchUserChangesResponse> batch, boolean advanceCursor) {
        if (batch.isEmpty()) {
            return;
        }

        long maxSeq = 0;
        for (WatchUserChangesResponse event : batch) {
            InternalUser user = event.getUser();
            userRepository.upsert(
                    UUID.fromString(event.getUserId()),
                    nullIfEmpty(user.getNickname()),
                    nullIfEmpty(user.getProfileImageUrl()),
                    nullIfEmpty(user.getAvatarHash()),
                    user.getProfilePublic(),
                    user.getDeleted(),
                    event.getSequence());
            maxSeq = Math.max(maxSeq, event.getSequence());
        }

        if (advanceCursor) {
            stateRepository.advanceCursor(maxSeq);
        }
    }

    /** 스냅샷 종료. 이 시점에 비로소 커서가 의미를 갖는다. */
    @Transactional
    public void completeSnapshot(long seq) {
        stateRepository.completeSnapshot(seq);
        log.info("사용자 읽기 모델 초기 복제 완료 (커서={})", seq);
    }

    private Optional<UserReplicationState> state() {
        return stateRepository.findById(UserReplicationState.SINGLETON_ID);
    }

    /**
     * proto3 에는 null 이 없어 미등록 값이 빈 문자열로 온다. 그대로 저장하면 프론트가 빈 URL 로
     * 깨진 이미지를 그리고 이니셜 fallback 이 동작하지 않는다.
     */
    private static String nullIfEmpty(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
