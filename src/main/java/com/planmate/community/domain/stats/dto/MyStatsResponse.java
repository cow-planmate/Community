package com.planmate.community.domain.stats.dto;

import com.planmate.community.domain.stats.entity.UserStats;

import java.util.UUID;

public record MyStatsResponse(
        UUID userId,
        int postCount,
        int commentCount,
        /** 내가 쓴 글이 받은 좋아요 총합 */
        int receivedLikes,
        int level
) {

    public static MyStatsResponse of(UUID userId, UserStats stats) {
        if (stats == null) {
            return new MyStatsResponse(userId, 0, 0, 0, 1);
        }
        return new MyStatsResponse(userId, stats.getPostCount(), stats.getCommentCount(),
                stats.getReceivedLikes(), stats.getLevel());
    }
}
