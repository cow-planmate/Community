package com.planmate.community.domain.stats.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 커뮤니티 활동 통계 — 작성자 레벨 산정용.
 * 이번 단계에서는 읽기 전용으로 사용하며, 증가 로직은 활동 마일스톤에서 붙는다.
 */
@Getter
@Entity
@Table(name = "community_user_stats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserStats {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "post_count", nullable = false)
    @Builder.Default
    private int postCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private int commentCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private int level = 1;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
