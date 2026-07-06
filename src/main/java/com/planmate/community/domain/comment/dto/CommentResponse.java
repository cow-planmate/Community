package com.planmate.community.domain.comment.dto;

import com.planmate.community.domain.comment.entity.Comment;

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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CommentResponse of(Comment comment, String freshNickname, int level) {
        return new CommentResponse(
                comment.getCommentId(),
                comment.getPostId(),
                comment.getParentId(),
                comment.getUserId(),
                freshNickname != null ? freshNickname : comment.getAuthorNickname(),
                level,
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
