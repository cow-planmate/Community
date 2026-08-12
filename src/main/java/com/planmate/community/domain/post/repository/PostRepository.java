package com.planmate.community.domain.post.repository;

import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByCategory(Category category, Pageable pageable);

    // MATE 참여 등 "정원 검사 → 저장"의 원자성을 위해 게시글 행을 잠근다 (동시 참여 직렬화)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.postId = :postId")
    Optional<Post> findByIdForUpdate(@Param("postId") Long postId);

    @Query("""
            SELECT p FROM Post p
            WHERE p.category = :category
              AND (p.title ilike concat('%', :q, '%')
                OR p.contentText ilike concat('%', :q, '%'))
            """)
    Page<Post> searchByCategory(@Param("category") Category category, @Param("q") String q, Pageable pageable);

    // 작성자 닉네임까지 포함한 검색. 사용자 복제본(community_user)이 준비된 뒤에만 쓴다 —
    // 준비 전에는 위 searchByCategory 로 떨어져 제목/본문만 검색한다.
    //
    // 조인이 아니라 EXISTS 인 이유:
    //   1) findLikedByUserId 가 이미 쓰는 이 코드베이스의 관용구다(연관관계가 하나도 없다).
    //   2) Spring Data 가 count 쿼리를 파생하는데, 조인 형태는 손으로 countQuery 를 써야 할
    //      가능성이 높고 틀리면 totalPages 가 조용히 어긋난다.
    //   3) 서브쿼리는 행을 중복시키지 않는다.
    //
    // 탈퇴 계정을 제외하는 건 닉네임이 null 이라 어차피 안 걸리기 때문이 아니라,
    // 의도를 명시하기 위해서다 — "탈퇴자는 이름으로 찾을 수 없다".
    @Query("""
            SELECT p FROM Post p
            WHERE p.category = :category
              AND (p.title ilike concat('%', cast(:q as string), '%')
                OR p.contentText ilike concat('%', cast(:q as string), '%')
                OR EXISTS (SELECT 1 FROM ReplicatedUser u
                            WHERE u.userId = p.userId
                              AND u.deleted = FALSE
                              AND u.nickname ilike concat('%', cast(:q as string), '%')))
            """)
    Page<Post> searchByCategoryIncludingAuthor(@Param("category") Category category,
                                               @Param("q") String q,
                                               Pageable pageable);

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
                OR p.title ilike concat('%', cast(:q as string), '%')
                OR p.contentText ilike concat('%', cast(:q as string), '%'))
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

    // 프로필 공개 목록 (다른 사용자의 여행기 등) — 정렬은 Pageable로 지정한다
    Page<Post> findByCategoryAndUserId(Category category, UUID userId, Pageable pageable);

    Page<Post> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Post> findByUserIdAndCategoryInOrderByCreatedAtDesc(UUID userId, Collection<Category> categories, Pageable pageable);

    @Query("""
            SELECT p FROM Post p
            WHERE p.postId IN (
                SELECT r.postId FROM Reaction r
                WHERE r.userId = :userId AND r.type = com.planmate.community.domain.reaction.enums.ReactionType.LIKE
            )
            ORDER BY p.createdAt DESC
            """)
    Page<Post> findLikedByUserId(@Param("userId") UUID userId, Pageable pageable);

    // 카테고리 필터는 별도 메서드로 둔다 — 하나의 쿼리에서 :categories를 null 바인딩하면 PG가 타입을 추론하지 못한다
    @Query("""
            SELECT p FROM Post p
            WHERE p.postId IN (
                SELECT r.postId FROM Reaction r
                WHERE r.userId = :userId AND r.type = com.planmate.community.domain.reaction.enums.ReactionType.LIKE
            )
              AND p.category IN :categories
            ORDER BY p.createdAt DESC
            """)
    Page<Post> findLikedByUserIdAndCategoryIn(@Param("userId") UUID userId,
                                              @Param("categories") Collection<Category> categories,
                                              Pageable pageable);

    // ── 뱃지 집계 (삭제된 글은 @SQLRestriction으로 자동 제외된다) ──────────
    long countByUserIdAndCategory(UUID userId, Category category);

    /** 여행기를 쓴 서로 다른 지역 수 */
    @Query("""
            SELECT COUNT(DISTINCT p.region) FROM Post p
            WHERE p.userId = :userId AND p.category = :category AND p.region IS NOT NULL
            """)
    long countDistinctRegionsByUserIdAndCategory(@Param("userId") UUID userId, @Param("category") Category category);

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
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + :delta WHERE p.postId = :postId")
    void addViewCount(@Param("postId") Long postId, @Param("delta") long delta);

    interface RegionCount {
        String getRegion();

        long getPostCount();
    }
}
