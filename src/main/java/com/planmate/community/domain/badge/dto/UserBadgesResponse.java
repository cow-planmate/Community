package com.planmate.community.domain.badge.dto;

import java.util.List;
import java.util.UUID;

public record UserBadgesResponse(
        UUID userId,
        int unlockedCount,
        int totalCount,
        List<BadgeResponse> badges
) {

    public static UserBadgesResponse of(UUID userId, List<BadgeResponse> badges) {
        int unlocked = (int) badges.stream().filter(BadgeResponse::unlocked).count();
        return new UserBadgesResponse(userId, unlocked, badges.size(), badges);
    }
}
