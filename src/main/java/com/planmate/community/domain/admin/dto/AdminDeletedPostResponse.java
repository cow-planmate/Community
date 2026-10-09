package com.planmate.community.domain.admin.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** 보관 중인 삭제 글 한 건. expiresAt 이 지나면 영구 삭제 대상이 된다. */
public record AdminDeletedPostResponse(
        String targetType,
        long postId,
        String title,
        String authorNickname,
        UUID authorId,
        LocalDateTime deletedAt,
        LocalDateTime expiresAt,
        long reportCount
) {
}
