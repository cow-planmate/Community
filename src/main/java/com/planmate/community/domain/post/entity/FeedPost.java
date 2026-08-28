package com.planmate.community.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** 독립 여행 피드 게시글. 작성자와 공통 게시글 정보까지 feed_post가 직접 소유한다. */
@Getter
@Entity
@Table(name = "feed_post")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class FeedPost extends Post {

    @Column(name = "duration_days", nullable = false)
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
    private int forkCount;

    public static FeedPost create(UUID userId, String authorNickname, String title, String content,
                                  String contentText, String thumbnailUrl, String region, String location,
                                  Double lat, Double lng, Integer durationDays, String itinerary,
                                  String tags, UUID sourcePlanId) {
        FeedPost feed = new FeedPost();
        feed.initializeCommon(com.planmate.community.domain.post.enums.Category.FEED, userId, authorNickname,
                title, content, contentText, thumbnailUrl, region, location, lat, lng);
        feed.durationDays = durationDays;
        feed.itinerary = itinerary;
        feed.tags = tags;
        feed.sourcePlanId = sourcePlanId;
        return feed;
    }

    void updateFeed(String region, String location, Integer durationDays,
                    String itinerary, boolean itineraryChanged,
                    String tags, boolean tagsChanged) {
        updateFeedLocation(region, location);
        if (durationDays != null) this.durationDays = durationDays;
        if (itineraryChanged) this.itinerary = itinerary;
        if (tagsChanged) this.tags = tags;
    }
}
