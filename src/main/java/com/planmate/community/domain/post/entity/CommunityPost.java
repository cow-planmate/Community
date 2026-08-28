package com.planmate.community.domain.post.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 커뮤니티 게시판 글 (FREE / QNA / MATE / RECOMMEND).
 *
 * 피드와는 테이블도 ID 시퀀스도 공유하지 않는다. 공통 필드만 {@link Post} 에서 물려받고,
 * Hibernate 상으로는 FeedPost 와 아무 관계가 없는 별개 엔티티다.
 */
@Getter
@Entity
@Table(name = "community_post")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class CommunityPost extends Post {
}
