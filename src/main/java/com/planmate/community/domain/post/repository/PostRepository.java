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

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByCategory(Category category, Pageable pageable);

    @Query("""
            SELECT p FROM Post p
            WHERE p.category = :category
              AND (lower(p.title) LIKE lower(concat('%', :q, '%'))
                OR lower(p.contentText) LIKE lower(concat('%', :q, '%')))
            """)
    Page<Post> searchByCategory(@Param("category") Category category, @Param("q") String q, Pageable pageable);

    List<Post> findTop3ByCategoryOrderByLikeCountDescCreatedAtDesc(Category category);

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
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.postId = :postId")
    void incrementViewCount(@Param("postId") Long postId);
}
