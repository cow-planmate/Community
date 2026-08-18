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

    /** 카카오 로컬 검색으로 고른 장소의 부가 정보 — 직접 입력한 옛 글은 전부 null이다. */
    @Column(name = "place_address", length = 255)
    private String placeAddress;

    @Column(name = "place_phone", length = 32)
    private String placePhone;

    @Column(name = "place_category", length = 255)
    private String placeCategory;

    @Column(name = "place_url", length = 512)
    private String placeUrl;

    /**
     * 글에 담긴 장소 목록(JSON 배열). 첫 번째가 대표 장소이며 위의 location/lat/lng/place_* 에 미러링돼 있다.
     * 장소가 하나뿐이던 시절의 옛 글은 null이고, 조회 측에서 대표 장소 한 건으로 취급한다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String places;

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

    public void updateRecommendFields(String location, BigDecimal rating, Double lat, Double lng,
                                      String placeAddress, String placePhone, String placeCategory, String placeUrl) {
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
        // 장소를 다시 고르면 부가 정보도 통째로 따라와야 한다. 옛 장소의 전화번호가 남으면 틀린 정보가 된다.
        if (placeAddress != null) {
            this.placeAddress = placeAddress;
            this.placePhone = placePhone;
            this.placeCategory = placeCategory;
            this.placeUrl = placeUrl;
        }
    }

    /**
     * 장소 목록 교체. 대표 장소(첫 번째)는 부분 갱신이 아니라 통째로 덮어쓴다 —
     * 장소를 지우고 다시 고른 뒤 옛 장소의 주소·전화번호가 남으면 그대로 틀린 정보가 되기 때문이다.
     *
     * @param places 직렬화된 JSON 배열 (null이면 장소 목록을 비운다)
     */
    public void updateRecommendPlaces(String places, RecommendPlaceSnapshot representative) {
        this.places = places;
        if (representative != null) {
            this.location = representative.name();
            this.lat = representative.lat();
            this.lng = representative.lng();
            this.placeAddress = representative.address();
            this.placePhone = representative.phone();
            this.placeCategory = representative.category();
            this.placeUrl = representative.url();
        }
    }

    /** 대표 장소 미러링에 필요한 값만 추린 것 — 엔티티가 DTO를 알지 않도록 별도로 둔다 */
    public record RecommendPlaceSnapshot(String name, String address, String phone, String category, String url,
                                         Double lat, Double lng) {
    }

    public void updateMateFields(String region, Integer maxParticipants) {
        if (region != null && !region.isBlank()) {
            this.region = region;
        }
        if (maxParticipants != null) {
            this.maxParticipants = maxParticipants;
        }
    }

    /**
     * FEED 수정. itinerary/tags는 이미 직렬화된 JSON 문자열을 받는다.
     * 일정·태그는 통째로 지울 수 있어야 하므로, 호출부가 "변경 없음"과 "비우기"를 구분해 전달한다.
     */
    public void updateFeedFields(String region, String location, Integer durationDays,
                                 String itinerary, boolean itineraryChanged,
                                 String tags, boolean tagsChanged) {
        if (region != null && !region.isBlank()) {
            this.region = region;
        }
        if (location != null && !location.isBlank()) {
            this.location = location;
        }
        if (durationDays != null) {
            this.durationDays = durationDays;
        }
        if (itineraryChanged) {
            this.itinerary = itinerary;
        }
        if (tagsChanged) {
            this.tags = tags;
        }
    }
}
