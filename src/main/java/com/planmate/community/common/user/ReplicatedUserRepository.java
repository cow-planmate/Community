package com.planmate.community.common.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ReplicatedUserRepository extends JpaRepository<ReplicatedUser, UUID> {

    /**
     * 변경 스트림 한 건 반영. UserStatsRepository.upsertCounts 와 같은 네이티브 ON CONFLICT 패턴이다.
     *
     * <p>마지막 줄의 {@code WHERE community_user.source_seq <= EXCLUDED.source_seq} 가 이 설계의
     * 정합성 핵심이다. 전달은 at-least-once 라서 같은 변경이 두 번 오거나, 재연결 시 이미 반영한
     * 구간이 다시 재생될 수 있다. 이 가드가 있으면 옛 값이 새 값을 덮어쓰지 못하므로 순서와
     * 중복을 신경 쓰지 않아도 된다.
     *
     * <p>{@code <} 가 아니라 {@code <=} 인 이유: 스냅샷 단계의 항목들은 모두 같은 기준 시퀀스를
     * 달고 오므로, {@code <} 로 비교하면 첫 행만 적용되고 나머지가 전부 버려진다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO community_user
                (user_id, nickname, profile_image_url, avatar_hash,
                 profile_public, deleted, source_seq, updated_at)
            VALUES (:userId, :nickname, :profileImageUrl, :avatarHash,
                    :profilePublic, :deleted, :sourceSeq, now())
            ON CONFLICT (user_id) DO UPDATE SET
                nickname          = EXCLUDED.nickname,
                profile_image_url = EXCLUDED.profile_image_url,
                avatar_hash       = EXCLUDED.avatar_hash,
                profile_public    = EXCLUDED.profile_public,
                deleted           = EXCLUDED.deleted,
                source_seq        = EXCLUDED.source_seq,
                updated_at        = now()
            WHERE community_user.source_seq <= EXCLUDED.source_seq
            """, nativeQuery = true)
    void upsert(@Param("userId") UUID userId,
                @Param("nickname") String nickname,
                @Param("profileImageUrl") String profileImageUrl,
                @Param("avatarHash") String avatarHash,
                @Param("profilePublic") boolean profilePublic,
                @Param("deleted") boolean deleted,
                @Param("sourceSeq") long sourceSeq);
}
