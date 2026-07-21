package com.planmate.community.domain.comment.dto;

import com.planmate.community.domain.comment.entity.Comment;
import com.planmate.community.domain.post.entity.Post;

import java.time.LocalDateTime;
import java.util.UUID;

public record CommentResponse(
        Long id,
        Long postId,
        Long parentId,
        UUID userId,
        String author,
        int level,
        String content,
        /** 내 활동 목록처럼 원문을 함께 보여줄 때만 채워진다 (게시글 상세의 댓글 목록에서는 null) */
        String postTitle,
        /** 원문으로 이동하는 링크에 필요하다. postTitle과 같은 조건으로 채워진다 */
        String postCategory,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CommentResponse of(Comment comment, String freshNickname, int level) {
        return of(comment, freshNickname, level, null);
    }

    public static CommentResponse of(Comment comment, String freshNickname, int level, Post post) {
        return new CommentResponse(
                comment.getCommentId(),
                comment.getPostId(),
                comment.getParentId(),
                comment.getUserId(),
                freshNickname != null ? freshNickname : comment.getAuthorNickname(),
                level,
                comment.getContent(),
                post != null ? post.getTitle() : null,
                post != null ? post.getCategory().toLowerValue() : null,
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
