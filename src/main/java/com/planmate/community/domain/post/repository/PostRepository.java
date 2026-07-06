package com.planmate.community.domain.post.repository;

import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByCategory(Category category, Pageable pageable);

    @Query("""
            SELECT p FROM Post p
            WHERE p.category = :category
              AND (lower(p.title) LIKE lower(concat('%', :q, '%'))
                OR lower(p.contentText) LIKE lower(concat('%', :q, '%')))
            """)
    Page<Post> searchByCategory(@Param("category") Category category, @Param("q") String q, Pageable pageable);

    // FEED 목록 필터 — 조건은 전부 null-safe (null이면 미적용)
    // 함수 인자의 cast(... as string)은 필수: 파라미터가 null일 때 PG가 타입을 추론하지 못해 bytea로 간주한다
    @Query("""
            SELECT p FROM Post p
            WHERE p.category = :category
              AND (:region IS NULL OR p.region = :region)
              AND (:minDays IS NULL OR p.durationDays >= :minDays)
              AND (:maxDays IS NULL OR p.durationDays <= :maxDays)
              AND (:tag IS NULL OR function('jsonb_exists', p.tags, cast(:tag as string)) = TRUE)
              AND (:q IS NULL
                OR lower(p.title) LIKE lower(concat('%', cast(:q as string), '%'))
                OR lower(p.contentText) LIKE lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Post> findFeedPosts(@Param("category") Category category,
                             @Param("region") String region,
                             @Param("minDays") Integer minDays,
                             @Param("maxDays") Integer maxDays,
                             @Param("tag") String tag,
                             @Param("q") String q,
                             Pageable pageable);

    // 지역별 게시글 수 집계 (피드 지도/필터용)
    @Query("""
            SELECT p.region AS region, COUNT(p) AS postCount
            FROM Post p
            WHERE p.category = :category AND p.region IS NOT NULL
            GROUP BY p.region
            ORDER BY COUNT(p) DESC, p.region ASC
            """)
    List<RegionCount> countRegionsByCategory(@Param("category") Category category);

    List<Post> findTop3ByCategoryOrderByLikeCountDescCreatedAtDesc(Category category);

    Page<Post> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("""
            SELECT p FROM Post p
            WHERE p.postId IN (
                SELECT r.postId FROM Reaction r
                WHERE r.userId = :userId AND r.type = com.planmate.community.domain.reaction.enums.ReactionType.LIKE
            )
            ORDER BY p.createdAt DESC
            """)
    Page<Post> findLikedByUserId(@Param("userId") UUID userId, Pageable pageable);

    // 카운터는 동시성 안전하게 원자적 UPDATE로 증감한다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + :delta WHERE p.postId = :postId")
    void addLikeCount(@Param("postId") Long postId, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.dislikeCount = p.dislikeCount + :delta WHERE p.postId = :postId")
    void addDislikeCount(@Param("postId") Long postId, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.postId = :postId")
    void addCommentCount(@Param("postId") Long postId, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.forkCount = p.forkCount + :delta WHERE p.postId = :postId")
    void addForkCount(@Param("postId") Long postId, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.postId = :postId")
    void incrementViewCount(@Param("postId") Long postId);

    interface RegionCount {
        String getRegion();

        long getPostCount();
    }
}
