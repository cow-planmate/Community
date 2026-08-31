package com.planmate.community.domain.post.entity;

import com.planmate.community.domain.post.enums.MateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * 커뮤니티 게시판 글 (FREE / QNA / MATE / RECOMMEND).
 *
 * 피드와는 테이블도 ID 시퀀스도 공유하지 않는다. 공통 필드만 {@link Post} 에서 물려받고,
 * Hibernate 상으로는 FeedPost 와 아무 관계가 없는 별개 엔티티다.
 *
 * QNA/MATE/RECOMMEND 전용 컬럼은 여기서만 선언한다 — Post 에 두면 feed_post 에도
 * 영원히 NULL 인 컬럼이 생기고, 피드 엔티티에서 markAnswered() 같은 게 호출 가능해진다.
 */
@Getter
@Entity
@Table(name = "community_post")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class CommunityPost extends Post {

    // QNA 전용
    @Column(name = "is_answered")
    private Boolean isAnswered;

    // MATE 전용
    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private MateStatus status;

    // RECOMMEND 전용
    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

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
     * 글에 담긴 장소 목록(JSON 배열). 첫 번째가 대표 장소이며 location/lat/lng/place_* 에 미러링돼 있다.
     * 장소가 하나뿐이던 시절의 옛 글은 null이고, 조회 측에서 대표 장소 한 건으로 취급한다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String places;

    public void markAnswered(boolean answered) {
        this.isAnswered = answered;
    }

    public void changeStatus(MateStatus status) {
        this.status = status;
    }

    public void updateRecommendFields(String location, BigDecimal rating, Double lat, Double lng,
                                      String placeAddress, String placePhone, String placeCategory, String placeUrl) {
        applyLocation(location, lat, lng);
        if (rating != null) {
            this.rating = rating;
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
            replaceLocation(representative.name(), representative.lat(), representative.lng());
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
        applyRegion(region);
        if (maxParticipants != null) {
            this.maxParticipants = maxParticipants;
        }
    }
}
