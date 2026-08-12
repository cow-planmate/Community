package com.planmate.community.domain.badge.dto;

import com.planmate.community.domain.badge.entity.UserBadge;
import com.planmate.community.domain.badge.enums.BadgeType;

import java.time.LocalDateTime;

public record BadgeResponse(
        String code,
        String name,
        String description,
        /** 달성에 필요한 수치 */
        int goal,
        /** 현재 수치 (goal에서 잘린다) */
        int progress,
        boolean unlocked,
        /** 달성 시각 — 미달성이면 null */
        LocalDateTime earnedAt
) {

    /** saved가 null이면 아직 활동 기록이 없는 사용자 — 잠긴 뱃지로 내려준다 */
    public static BadgeResponse of(BadgeType type, UserBadge saved) {
        if (saved == null) {
            return new BadgeResponse(type.code(), type.displayName(), type.description(), type.goal(), 0, false, null);
        }
        return new BadgeResponse(type.code(), type.displayName(), type.description(), type.goal(),
                Math.min(Math.max(saved.getProgress(), 0), type.goal()), saved.isEarned(), saved.getEarnedAt());
    }
}
