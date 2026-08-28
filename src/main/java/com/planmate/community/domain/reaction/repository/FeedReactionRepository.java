package com.planmate.community.domain.reaction.repository;

import com.planmate.community.domain.reaction.entity.FeedReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeedReactionRepository extends JpaRepository<FeedReaction, Long> {
    Optional<FeedReaction> findByPostIdAndUserId(Long postId, UUID userId);
    List<FeedReaction> findByUserIdAndPostIdIn(UUID userId, Collection<Long> postIds);
}
