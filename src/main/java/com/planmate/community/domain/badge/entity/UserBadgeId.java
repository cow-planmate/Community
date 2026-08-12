package com.planmate.community.domain.badge.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** community_user_badge 복합 키 (user_id, badge_code) */
public class UserBadgeId implements Serializable {

    private UUID userId;
    private String badgeCode;

    protected UserBadgeId() {
    }

    public UserBadgeId(UUID userId, String badgeCode) {
        this.userId = userId;
        this.badgeCode = badgeCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserBadgeId other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && Objects.equals(badgeCode, other.badgeCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, badgeCode);
    }
}
