package com.planmate.community.domain.post.entity;

import com.planmate.community.common.entity.BaseSoftDeleteEntity;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.enums.MateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "community_post")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Post extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Category category;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "author_nickname", nullable = false, length = 100)
    private String authorNickname;

    @Column(nullable = false, length = 255)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String content;

    @Column(name = "content_text", nullable = false, columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "thumbnail_url", length = 512)
    private String thumbnailUrl;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private int likeCount = 0;

    @Column(name = "dislike_count", nullable = false)
    @Builder.Default
    private int dislikeCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private int commentCount = 0;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private int viewCount = 0;

    // QNA 전용
    @Column(name = "is_answered")
    private Boolean isAnswered;

    // MATE 전용
    @Column(length = 100)
    private String region;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private MateStatus status;

    // RECOMMEND 전용
    @Column(length = 255)
    private String location;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    private Double lat;

    private Double lng;

    // FEED 전용
    @Column(name = "duration_days")
    private Integer durationDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String itinerary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String tags;

    @Column(name = "source_plan_id")
    private UUID sourcePlanId;

    @Column(name = "fork_count", nullable = false)
    @Builder.Default
    private int forkCount = 0;

    public boolean isAuthor(UUID userId) {
        return this.userId.equals(userId);
    }

    public void changeStatus(MateStatus status) {
        this.status = status;
    }

    public void markAnswered(boolean answered) {
        this.isAnswered = answered;
    }

    public void update(String title, String content, String contentText, String thumbnailUrl) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
            this.contentText = contentText != null ? contentText : "";
        }
        if (thumbnailUrl != null) {
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    public void updateRecommendFields(String location, BigDecimal rating, Double lat, Double lng) {
        if (location != null && !location.isBlank()) {
            this.location = location;
        }
        if (rating != null) {
            this.rating = rating;
        }
        if (lat != null) {
            this.lat = lat;
        }
        if (lng != null) {
            this.lng = lng;
        }
    }

    public void updateMateFields(String region, Integer maxParticipants) {
        if (region != null && !region.isBlank()) {
            this.region = region;
        }
        if (maxParticipants != null) {
            this.maxParticipants = maxParticipants;
        }
    }
}
