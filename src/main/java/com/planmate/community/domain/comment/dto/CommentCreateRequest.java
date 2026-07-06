package com.planmate.community.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotBlank(message = "댓글 내용은 필수입니다.")
        @Size(max = 2000, message = "댓글은 2000자를 넘을 수 없습니다.")
        String content,

        // 대댓글이면 부모 댓글 ID, 최상위 댓글이면 null
        Long parentId
) {
}
