package com.planmate.community.domain.fork.repository;

import com.planmate.community.domain.fork.entity.FeedFork;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeedForkRepository extends JpaRepository<FeedFork, Long> {

    boolean existsByPostIdAndUserId(Long postId, UUID userId);
}
