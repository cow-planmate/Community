package com.planmate.community.domain.comment.repository;

import com.planmate.community.domain.comment.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByPostIdOrderByCreatedAtAsc(Long postId, Pageable pageable);

    // 살아있는 대댓글 목록 (@SQLRestriction이 soft-deleted 자동 제외)
    List<Comment> findByParentId(Long parentId);

    Page<Comment> findByUserIdOrderByCreatedAtDesc(java.util.UUID userId, Pageable pageable);
}
