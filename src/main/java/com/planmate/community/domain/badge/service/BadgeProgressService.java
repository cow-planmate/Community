package com.planmate.community.domain.badge.service;

import com.planmate.community.domain.badge.enums.BadgeType;
import com.planmate.community.domain.badge.repository.UserBadgeRepository;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.stats.entity.UserStats;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 뱃지 진행도 갱신 — 활동이 일어난 쓰기 트랜잭션에 참여한다.
 *
 * 조회 부하를 쓰기 쪽으로 옮기는 게 목적이므로, 이벤트와 관련 있는 뱃지만 다시 계산한다.
 * (글을 써도 댓글 뱃지는 건드리지 않는다.)
 */
@Service
@RequiredArgsConstructor
public class BadgeProgressService {

    private final UserBadgeRepository userBadgeRepository;
    private final UserStatsRepository userStatsRepository;
    private final PostRepository postRepository;

    /** 게시글 작성/삭제 후 — 누적 글 수는 통계에서, 여행기 편수·지역 수는 집계로 얻는다 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshPostBadges(UUID userId) {
        upsert(userId, BadgeType.FIRST_STEP, postCount(userId));
        upsert(userId, BadgeType.PLAN_MASTER, postRepository.countByUserIdAndCategory(userId, Category.FEED));
        upsert(userId, BadgeType.NATIONWIDE,
                postRepository.countDistinctRegionsByUserIdAndCategory(userId, Category.FEED));
    }

    /** 댓글 작성/삭제 후 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshCommentBadges(UUID userId) {
        upsert(userId, BadgeType.EAGER_REVIEWER, commentCount(userId));
    }

    /** 내 글이 좋아요를 받거나 취소당한 후 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshLikeBadges(UUID userId) {
        upsert(userId, BadgeType.BEST_PARTNER, receivedLikes(userId));
    }

    private void upsert(UUID userId, BadgeType type, long progress) {
        userBadgeRepository.upsertProgress(userId, type.code(), progress, type.goal());
    }

    private long postCount(UUID userId) {
        return stats(userId).map(UserStats::getPostCount).orElse(0);
    }

    private long commentCount(UUID userId) {
        return stats(userId).map(UserStats::getCommentCount).orElse(0);
    }

    private long receivedLikes(UUID userId) {
        return stats(userId).map(UserStats::getReceivedLikes).orElse(0);
    }

    private Optional<UserStats> stats(UUID userId) {
        return userStatsRepository.findById(userId);
    }
}
