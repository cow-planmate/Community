package com.planmate.community.domain.stats.dto;

import com.planmate.community.domain.stats.entity.UserStats;

import java.util.UUID;

public record MyStatsResponse(
        UUID userId,
        int postCount,
        int commentCount,
        int level
) {

    public static MyStatsResponse of(UUID userId, UserStats stats) {
        if (stats == null) {
            return new MyStatsResponse(userId, 0, 0, 1);
        }
        return new MyStatsResponse(userId, stats.getPostCount(), stats.getCommentCount(), stats.getLevel());
    }
}
