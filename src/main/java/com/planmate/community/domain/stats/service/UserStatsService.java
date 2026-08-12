package com.planmate.community.domain.stats.service;

import com.planmate.community.domain.badge.service.BadgeProgressService;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 커뮤니티 활동 통계 갱신 — 게시글/댓글/좋아요 쓰기 트랜잭션에 참여하여
 * 카운트·레벨·뱃지 진행도를 함께 동기화한다.
 */
@Service
@RequiredArgsConstructor
public class UserStatsService {

    private final UserStatsRepository userStatsRepository;
    private final BadgeProgressService badgeProgressService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPostCreated(UUID userId) {
        applyDelta(userId, 1, 0);
        badgeProgressService.refreshPostBadges(userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPostDeleted(UUID userId) {
        applyDelta(userId, -1, 0);
        badgeProgressService.refreshPostBadges(userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCommentCreated(UUID userId) {
        applyDelta(userId, 0, 1);
        badgeProgressService.refreshCommentBadges(userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCommentDeleted(UUID userId) {
        applyDelta(userId, 0, -1);
        badgeProgressService.refreshCommentBadges(userId);
    }

    /** 글 작성자가 받은 좋아요 증감 (반응을 누른 사람이 아니라 글 주인 기준) */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordLikeReceived(UUID authorId, int delta) {
        userStatsRepository.upsertReceivedLikes(authorId, delta);
        badgeProgressService.refreshLikeBadges(authorId);
    }

    private void applyDelta(UUID userId, int postDelta, int commentDelta) {
        userStatsRepository.upsertCounts(userId, postDelta, commentDelta);
        userStatsRepository.recalculateLevel(userId);
    }
}
