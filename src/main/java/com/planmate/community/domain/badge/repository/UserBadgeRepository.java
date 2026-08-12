package com.planmate.community.domain.badge.repository;

import com.planmate.community.domain.badge.entity.UserBadge;
import com.planmate.community.domain.badge.entity.UserBadgeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UserBadgeId> {

    List<UserBadge> findByUserId(UUID userId);

    /**
     * 진행도 갱신 (동시성 안전).
     * earned_at 은 COALESCE 로 보존한다 — 글이 삭제돼 progress 가 내려가도 이미 딴 뱃지는 유지된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO community_user_badge (user_id, badge_code, progress, earned_at, updated_at)
            VALUES (:userId, :badgeCode, LEAST(GREATEST(:progress, 0), :goal),
                    CASE WHEN :progress >= :goal THEN now() END, now())
            ON CONFLICT (user_id, badge_code) DO UPDATE SET
                progress = LEAST(GREATEST(:progress, 0), :goal),
                earned_at = COALESCE(community_user_badge.earned_at,
                                     CASE WHEN :progress >= :goal THEN now() END),
                updated_at = now()
            """, nativeQuery = true)
    void upsertProgress(@Param("userId") UUID userId,
                        @Param("badgeCode") String badgeCode,
                        @Param("progress") long progress,
                        @Param("goal") int goal);
}
