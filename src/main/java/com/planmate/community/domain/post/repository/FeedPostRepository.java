package com.planmate.community.domain.post.repository;

import com.planmate.community.domain.post.entity.FeedPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedPostRepository extends JpaRepository<FeedPost, Long> {

    @Query("""
            SELECT p FROM FeedPost p
            WHERE (:region IS NULL OR p.region = :region)
              AND (:minDays IS NULL OR p.durationDays >= :minDays)
              AND (:maxDays IS NULL OR p.durationDays <= :maxDays)
              AND (:tag IS NULL OR function('jsonb_exists', p.tags, cast(:tag as string)) = TRUE)
              AND (:q IS NULL OR p.title ilike concat('%', cast(:q as string), '%')
                OR p.contentText ilike concat('%', cast(:q as string), '%'))
            """)
    Page<FeedPost> findFeedPosts(String region, Integer minDays, Integer maxDays, String tag, String q, Pageable pageable);

    @Query("SELECT p.region AS region, COUNT(p) AS postCount FROM FeedPost p WHERE p.region IS NOT NULL GROUP BY p.region ORDER BY COUNT(p) DESC, p.region ASC")
    List<PostRepository.RegionCount> countRegions();

    List<FeedPost> findTop3ByOrderByLikeCountDescCreatedAtDesc();
    Optional<FeedPost> findFirstByPostIdGreaterThanOrderByPostIdAsc(Long postId);
    Optional<FeedPost> findFirstByPostIdLessThanOrderByPostIdDesc(Long postId);
    Page<FeedPost> findByUserId(UUID userId, Pageable pageable);

    @Query("SELECT p FROM FeedPost p WHERE p.postId IN (SELECT r.postId FROM FeedReaction r WHERE r.userId = :userId AND r.type = com.planmate.community.domain.reaction.enums.ReactionType.LIKE) ORDER BY p.createdAt DESC")
    Page<FeedPost> findLikedByUserId(UUID userId, Pageable pageable);

    // ── 뱃지 집계 (삭제된 글은 @SQLRestriction으로 자동 제외된다) ──────────
    long countByUserId(UUID userId);

    /** 여행기를 쓴 서로 다른 지역 수 */
    @Query("SELECT COUNT(DISTINCT p.region) FROM FeedPost p WHERE p.userId = :userId AND p.region IS NOT NULL")
    long countDistinctRegionsByUserId(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FeedPost p SET p.likeCount = p.likeCount + :delta WHERE p.postId = :postId")
    void addLikeCount(Long postId, int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FeedPost p SET p.dislikeCount = p.dislikeCount + :delta WHERE p.postId = :postId")
    void addDislikeCount(Long postId, int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FeedPost p SET p.commentCount = p.commentCount + :delta WHERE p.postId = :postId")
    void addCommentCount(Long postId, int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FeedPost p SET p.forkCount = p.forkCount + :delta WHERE p.postId = :postId")
    void addForkCount(Long postId, int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FeedPost p SET p.viewCount = p.viewCount + :delta WHERE p.postId = :postId")
    void addViewCount(Long postId, long delta);

    // 탈퇴 시점에 옛 닉네임 스냅샷을 지운다 — PostRepository.scrubAuthorNickname 과 같은 이유
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FeedPost p SET p.authorNickname = :nickname WHERE p.userId = :userId")
    void scrubAuthorNickname(@Param("userId") UUID userId, @Param("nickname") String nickname);
}
