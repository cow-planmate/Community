package com.planmate.community.domain.post.repository;

import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
