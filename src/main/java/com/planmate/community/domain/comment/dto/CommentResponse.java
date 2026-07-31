package com.planmate.community.domain.comment.dto;

import com.planmate.community.common.client.AuthorProfile;
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
        /** 작성자 프로필 사진 URL. 없으면 클라이언트가 authorAvatarHash(Gravatar) → 이니셜 순으로 떨어진다 */
        String authorImage,
        /** 작성자 이메일 해시(Gravatar 식별자). 이메일 원문은 내려오지 않는다 */
        String authorAvatarHash,
        int level,
        String content,
        /** 내 활동 목록처럼 원문을 함께 보여줄 때만 채워진다 (게시글 상세의 댓글 목록에서는 null) */
        String postTitle,
        /** 원문으로 이동하는 링크에 필요하다. postTitle과 같은 조건으로 채워진다 */
        String postCategory,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CommentResponse of(Comment comment, AuthorProfile author, int level) {
        return of(comment, author, level, null);
    }

    public static CommentResponse of(Comment comment, AuthorProfile author, int level, Post post) {
        AuthorProfile resolved = AuthorProfile.resolve(author, comment.getAuthorNickname());
        return new CommentResponse(
                comment.getCommentId(),
                comment.getPostId(),
                comment.getParentId(),
                comment.getUserId(),
                resolved.nickname(),
                resolved.profileImageUrl(),
                resolved.avatarHash(),
                level,
                comment.getContent(),
                post != null ? post.getTitle() : null,
                post != null ? post.getCategory().toLowerValue() : null,
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
