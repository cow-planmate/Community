package com.planmate.community.common.user;

import com.planmate.community.support.PostgresTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자 읽기 모델의 SQL 계약 검증.
 *
 * 여기서 검증하는 것들은 전부 네이티브 SQL 안에 있어서 목으로는 확인되지 않는다.
 * 특히 업서트의 source_seq 가드는 이 설계 전체의 정합성 근거다 — 전달이 at-least-once 라서
 * 같은 변경이 두 번 오거나 재연결로 이미 반영한 구간이 재생되는 일이 정상 동작인데,
 * 가드가 없거나 부등호가 뒤집히면 옛 값이 새 값을 덮어쓴다. 그리고 그 증상은
 * "가끔 닉네임이 예전 걸로 돌아간다" 뿐이라 운영에서 원인을 찾기 매우 어렵다.
 */
class UserProjectionPersistenceTest extends PostgresTestBase {

    @Autowired
    private ReplicatedUserRepository userRepository;

    @Autowired
    private UserReplicationStateRepository stateRepository;

    @Autowired
    private EntityManager entityManager;

    private void upsert(UUID userId, String nickname, boolean profilePublic, boolean deleted, long seq) {
        userRepository.upsert(userId, nickname, null, null, profilePublic, deleted, seq);
    }

    private ReplicatedUser reload(UUID userId) {
        entityManager.clear();
        return userRepository.findById(userId).orElseThrow();
    }

    @Test
    @DisplayName("마이그레이션이 만든 커서 행이 초기 상태로 존재한다")
    void replicationCursorIsSeeded() {
        UserReplicationState state =
                stateRepository.findById(UserReplicationState.SINGLETON_ID).orElseThrow();

        // 이 행이 없으면 구독자가 커서를 읽지 못해 매 기동마다 전체 스냅샷을 받는다
        assertThat(state.getLastAppliedSeq()).isZero();
        assertThat(state.isSnapshotComplete()).isFalse();
    }

    @Test
    @DisplayName("같은 사용자의 나중 시퀀스는 이전 값을 덮어쓴다")
    void laterSequenceWins() {
        UUID userId = UUID.randomUUID();
        upsert(userId, "옛닉", false, false, 3L);

        upsert(userId, "새닉", true, false, 7L);

        ReplicatedUser user = reload(userId);
        assertThat(user.getNickname()).isEqualTo("새닉");
        assertThat(user.isProfilePublic()).isTrue();
        assertThat(user.getSourceSeq()).isEqualTo(7L);
    }

    @Test
    @DisplayName("늦게 도착한 이전 시퀀스는 무시된다 — 재생/중복 수신이 값을 되돌리면 안 된다")
    void earlierSequenceIsIgnored() {
        UUID userId = UUID.randomUUID();
        upsert(userId, "새닉", true, false, 7L);

        // 재연결로 3번이 다시 재생됐다고 가정
        upsert(userId, "옛닉", false, false, 3L);

        ReplicatedUser user = reload(userId);
        assertThat(user.getNickname()).isEqualTo("새닉");
        assertThat(user.isProfilePublic()).isTrue();
        assertThat(user.getSourceSeq()).isEqualTo(7L);
    }

    @Test
    @DisplayName("같은 시퀀스가 다시 와도 적용된다 — 스냅샷 항목은 전부 같은 시퀀스를 공유한다")
    void sameSequenceStillApplies() {
        UUID userId = UUID.randomUUID();
        upsert(userId, "첫값", false, false, 5L);

        // 스냅샷 단계에서는 모든 사용자가 같은 기준 시퀀스를 달고 온다.
        // 가드가 < 였다면 여기서 갱신이 통째로 버려진다.
        upsert(userId, "다시받은값", true, false, 5L);

        ReplicatedUser user = reload(userId);
        assertThat(user.getNickname()).isEqualTo("다시받은값");
        assertThat(user.isProfilePublic()).isTrue();
    }

    @Test
    @DisplayName("탈퇴 반영 시 닉네임과 이미지가 비워진다")
    void deletionClearsPersonalFields() {
        UUID userId = UUID.randomUUID();
        userRepository.upsert(userId, "홍길동", "https://img/1.png", "hash", true, false, 1L);

        userRepository.upsert(userId, null, null, null, false, true, 2L);

        ReplicatedUser user = reload(userId);
        assertThat(user.isDeleted()).isTrue();
        // 탈퇴 후에도 개인정보가 남아 있으면 안 된다
        assertThat(user.getNickname()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
        assertThat(user.getAvatarHash()).isNull();
    }

    @Test
    @DisplayName("커서는 전진만 하고 되돌아가지 않는다")
    void cursorNeverMovesBackward() {
        stateRepository.advanceCursor(50L);

        stateRepository.advanceCursor(20L);

        entityManager.clear();
        assertThat(stateRepository.findById(UserReplicationState.SINGLETON_ID).orElseThrow()
                .getLastAppliedSeq()).isEqualTo(50L);
    }

    @Test
    @DisplayName("스냅샷 완료는 커서 확정과 완료 표시를 한 번에 한다")
    void completeSnapshotSetsBothAtOnce() {
        stateRepository.completeSnapshot(12L);

        entityManager.clear();
        UserReplicationState state =
                stateRepository.findById(UserReplicationState.SINGLETON_ID).orElseThrow();
        // 둘이 갈라지면 "커서는 올라갔는데 미완료"라는 복구 불가능한 상태가 된다
        assertThat(state.getLastAppliedSeq()).isEqualTo(12L);
        assertThat(state.isSnapshotComplete()).isTrue();
    }
}
