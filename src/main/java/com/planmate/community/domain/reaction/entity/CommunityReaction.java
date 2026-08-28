package com.planmate.community.domain.reaction.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 커뮤니티 게시글의 좋아요/싫어요. 피드 반응(feed_reaction)과는 테이블도 ID 시퀀스도 공유하지 않는다. */
@Getter
@Entity
@Table(name = "community_reaction",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class CommunityReaction extends Reaction {
}
