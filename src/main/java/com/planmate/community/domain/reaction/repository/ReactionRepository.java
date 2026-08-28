package com.planmate.community.domain.reaction.repository;

import com.planmate.community.domain.reaction.entity.CommunityReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReactionRepository extends JpaRepository<CommunityReaction, Long> {

    Optional<CommunityReaction> findByPostIdAndUserId(Long postId, UUID userId);

    // 좋아요한 글 목록에 "좋아요한 시각"을 채우기 위한 배치 조회 (N+1 방지)
    List<CommunityReaction> findByUserIdAndPostIdIn(UUID userId, Collection<Long> postIds);
}
