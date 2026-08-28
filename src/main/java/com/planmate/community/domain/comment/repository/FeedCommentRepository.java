package com.planmate.community.domain.comment.repository;

import com.planmate.community.domain.comment.entity.FeedComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedCommentRepository extends JpaRepository<FeedComment, Long> {
    Page<FeedComment> findByPostIdOrderByCreatedAtAsc(Long postId, Pageable pageable);
    List<FeedComment> findByParentId(Long parentId);
    Page<FeedComment> findByUserIdOrderByCreatedAtDesc(java.util.UUID userId, Pageable pageable);
}
