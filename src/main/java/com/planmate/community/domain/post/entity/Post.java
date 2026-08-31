package com.planmate.community.domain.post.entity;

import com.planmate.community.common.entity.BaseSoftDeleteEntity;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.enums.MateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 커뮤니티 게시글과 피드 게시글이 함께 쓰는 공통 필드·동작.
 *
 * 엔티티 상속(TABLE_PER_CLASS)이 아니라 매핑 상속이다 — 두 도메인은 ID 시퀀스가 분리돼 있어
 * 같은 번호가 양쪽에 존재할 수 있고, 한 계층으로 묶으면 부모 타입 조회가 두 테이블을 UNION 해
 * 남의 도메인 행을 집어 오거나(벌크 UPDATE 는 남의 카운터를 올린다) 만다.
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public abstract class Post extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "domain_post_id")
    @GenericGenerator(name = "domain_post_id", type = com.planmate.community.common.persistence.DomainSequenceGenerator.class)
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

    /** MATE 모집 지역이자 FEED 여행 지역 — 두 도메인이 함께 쓴다. */
    @Column(length = 100)
    private String region;

    /** RECOMMEND 대표 장소명이자 FEED 여행지 — 두 도메인이 함께 쓴다. */
    @Column(length = 255)
    private String location;

    private Double lat;

    private Double lng;

    public boolean isAuthor(UUID userId) {
        return this.userId.equals(userId);
    }

    protected void initializeCommon(Category category, UUID userId, String authorNickname, String title,
                                    String content, String contentText, String thumbnailUrl,
                                    String region, String location, Double lat, Double lng) {
        this.category = category;
        this.userId = userId;
        this.authorNickname = authorNickname;
        this.title = title;
        this.content = content;
        this.contentText = contentText;
        this.thumbnailUrl = thumbnailUrl;
        this.region = region;
        this.location = location;
        this.lat = lat;
        this.lng = lng;
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

    /**
     * FEED 수정. itinerary/tags는 이미 직렬화된 JSON 문자열을 받는다.
     * 일정·태그는 통째로 지울 수 있어야 하므로, 호출부가 "변경 없음"과 "비우기"를 구분해 전달한다.
     */
    public void updateFeedFields(String region, String location, Integer durationDays,
                                 String itinerary, boolean itineraryChanged,
                                 String tags, boolean tagsChanged) {
        if (!(this instanceof FeedPost feedPost)) {
            throw new IllegalStateException("피드 게시글이 아닙니다.");
        }
        feedPost.updateFeed(region, location, durationDays, itinerary, itineraryChanged, tags, tagsChanged);
    }

    // 아래 접근자들은 상대 도메인 전용 필드의 기본값이다 — 조회 DTO 하나가 두 도메인을 함께 다루므로
    // 자기 도메인이 아닌 필드는 null(또는 0)로 답하고, 해당 도메인 엔티티가 실제 필드로 오버라이드한다.

    public Boolean getIsAnswered() {
        return null;
    }

    public Integer getMaxParticipants() {
        return null;
    }

    public MateStatus getStatus() {
        return null;
    }

    public BigDecimal getRating() {
        return null;
    }

    public String getPlaceAddress() {
        return null;
    }

    public String getPlacePhone() {
        return null;
    }

    public String getPlaceCategory() {
        return null;
    }

    public String getPlaceUrl() {
        return null;
    }

    public String getPlaces() {
        return null;
    }

    public Integer getDurationDays() {
        return null;
    }

    public String getItinerary() {
        return null;
    }

    public String getTags() {
        return null;
    }

    public UUID getSourcePlanId() {
        return null;
    }

    public int getForkCount() {
        return 0;
    }

    /** 공용 위치 필드는 Post 가 소유하므로, 도메인별 갱신은 이 훅을 통해 들어온다. */
    protected void applyRegion(String region) {
        if (region != null && !region.isBlank()) this.region = region;
    }

    protected void applyLocation(String location, Double lat, Double lng) {
        if (location != null && !location.isBlank()) this.location = location;
        if (lat != null) this.lat = lat;
        if (lng != null) this.lng = lng;
    }

    /** 대표 장소 교체 — 옛 값이 남지 않도록 null 도 그대로 덮어쓴다. */
    protected void replaceLocation(String location, Double lat, Double lng) {
        this.location = location;
        this.lat = lat;
        this.lng = lng;
    }

    protected void updateFeedLocation(String region, String location) {
        if (region != null && !region.isBlank()) this.region = region;
        if (location != null && !location.isBlank()) this.location = location;
    }
}
