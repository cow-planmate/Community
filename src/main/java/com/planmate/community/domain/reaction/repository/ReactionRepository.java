package com.planmate.community.domain.reaction.repository;

import com.planmate.community.domain.reaction.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByPostIdAndUserId(Long postId, UUID userId);
}
