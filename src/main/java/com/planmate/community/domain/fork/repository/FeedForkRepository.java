package com.planmate.community.domain.fork.repository;

import com.planmate.community.domain.fork.entity.FeedFork;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeedForkRepository extends JpaRepository<FeedFork, Long> {

    boolean existsByPostIdAndUserId(Long postId, UUID userId);

    // 내가 가져온 여행 목록 — 게시글 작성일이 아니라 "가져간 시각" 최신순
    Page<FeedFork> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
