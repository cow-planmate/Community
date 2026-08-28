package com.planmate.community.domain.reaction.entity;

import com.planmate.community.domain.reaction.enums.ReactionType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "feed_reaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedReaction extends Reaction {

    public static FeedReaction create(Long postId, UUID userId, ReactionType type) {
        FeedReaction reaction = new FeedReaction();
        reaction.initialize(postId, userId, type);
        return reaction;
    }
}
