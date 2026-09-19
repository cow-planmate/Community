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
import org.hibernate.annotations.SQLRestriction;

/**
 * 커뮤니티 게시판 글 (FREE / QNA / MATE).
 *
 * 피드와는 테이블도 ID 시퀀스도 공유하지 않는다. 공통 필드만 {@link Post} 에서 물려받고,
 * Hibernate 상으로는 FeedPost 와 아무 관계가 없는 별개 엔티티다.
 *
 * QNA/MATE 전용 컬럼은 여기서만 선언한다 — Post 에 두면 feed_post 에도
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

    public void markAnswered(boolean answered) {
        this.isAnswered = answered;
    }

    public void changeStatus(MateStatus status) {
        this.status = status;
    }

    public void updateMateFields(String region, Integer maxParticipants) {
        applyRegion(region);
        if (maxParticipants != null) {
            this.maxParticipants = maxParticipants;
        }
    }
}
