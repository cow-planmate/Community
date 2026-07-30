package com.planmate.community.domain.fork.repository;

import com.planmate.community.domain.fork.entity.FeedFork;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface FeedForkRepository extends JpaRepository<FeedFork, Long> {

    boolean existsByPostIdAndUserId(Long postId, UUID userId);

    /**
     * 가져가기 기록 UPSERT — 같은 글을 여러 번 가져가도 (post, user)당 1행만 남기고 시각만 갱신한다.
     * "내가 가져온 여행" 목록의 중복을 막기 위함이며, 가져간 횟수는 community_post.fork_count가 센다.
     * select 후 insert로 나누면 동시 요청이 UNIQUE 위반을 일으켜 트랜잭션이 오염되므로 단일 원자 구문으로 처리한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO community_feed_fork (post_id, user_id, created_at)
            VALUES (:postId, :userId, :now)
            ON CONFLICT (post_id, user_id) DO UPDATE SET created_at = EXCLUDED.created_at
            """, nativeQuery = true)
    void upsertFork(@Param("postId") Long postId,
                    @Param("userId") UUID userId,
                    @Param("now") LocalDateTime now);

    // 내가 가져온 여행 목록 — 게시글 작성일이 아니라 "가져간 시각" 최신순
    Page<FeedFork> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
