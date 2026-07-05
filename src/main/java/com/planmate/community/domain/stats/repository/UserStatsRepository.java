package com.planmate.community.domain.stats.repository;

import com.planmate.community.domain.stats.entity.UserStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserStatsRepository extends JpaRepository<UserStats, UUID> {

    /** 활동 카운트 원자적 upsert (동시성 안전) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO community_user_stats (user_id, post_count, comment_count, level, updated_at)
            VALUES (:userId, GREATEST(:postDelta, 0), GREATEST(:commentDelta, 0), 1, now())
            ON CONFLICT (user_id) DO UPDATE SET
                post_count = GREATEST(community_user_stats.post_count + :postDelta, 0),
                comment_count = GREATEST(community_user_stats.comment_count + :commentDelta, 0),
                updated_at = now()
            """, nativeQuery = true)
    void upsertCounts(@Param("userId") UUID userId, @Param("postDelta") int postDelta, @Param("commentDelta") int commentDelta);

    /** 레벨 재계산 — 점수 = 게시글*3 + 댓글, 구간 [0,10,30,70,150) = Lv1~5 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE community_user_stats SET level = CASE
                WHEN post_count * 3 + comment_count >= 150 THEN 5
                WHEN post_count * 3 + comment_count >= 70 THEN 4
                WHEN post_count * 3 + comment_count >= 30 THEN 3
                WHEN post_count * 3 + comment_count >= 10 THEN 2
                ELSE 1
            END
            WHERE user_id = :userId
            """, nativeQuery = true)
    void recalculateLevel(@Param("userId") UUID userId);
}
