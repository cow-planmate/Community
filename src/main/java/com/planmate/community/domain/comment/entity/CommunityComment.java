package com.planmate.community.domain.comment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/** 커뮤니티 게시글의 댓글. 피드 댓글(feed_comment)과는 테이블도 ID 시퀀스도 공유하지 않는다. */
@Getter
@Entity
@Table(name = "community_comment")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class CommunityComment extends Comment {
}
