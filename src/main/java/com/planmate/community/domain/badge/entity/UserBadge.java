package com.planmate.community.domain.badge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 사용자별 뱃지 진행도/달성 상태.
 *
 * 갱신은 전부 네이티브 upsert(UserBadgeRepository.upsertProgress)로 하고, 이 엔티티는 조회 전용이다.
 * earned_at 이 채워져 있으면 달성 — 한 번 달성한 뱃지는 회수하지 않는다.
 */
@Getter
@Entity
@Table(name = "community_user_badge")
@IdClass(UserBadgeId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBadge {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "badge_code", length = 32)
    private String badgeCode;

    @Column(nullable = false)
    private int progress;

    @Column(name = "earned_at")
    private LocalDateTime earnedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isEarned() {
        return earnedAt != null;
    }
}
