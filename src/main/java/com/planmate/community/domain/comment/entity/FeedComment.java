package com.planmate.community.domain.comment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "feed_comment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedComment extends Comment {

    public static FeedComment create(Long postId, UUID userId, String authorNickname, String content, Long parentId) {
        FeedComment comment = new FeedComment();
        comment.initialize(postId, userId, authorNickname, content, parentId);
        return comment;
    }
}
