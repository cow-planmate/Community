package com.planmate.community.domain.stats.service;

import com.planmate.community.domain.stats.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 커뮤니티 활동 통계 갱신 — 게시글/댓글 쓰기 트랜잭션에 참여하여 카운트와 레벨을 동기화한다.
 */
@Service
@RequiredArgsConstructor
public class UserStatsService {

    private final UserStatsRepository userStatsRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPostCreated(UUID userId) {
        applyDelta(userId, 1, 0);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPostDeleted(UUID userId) {
        applyDelta(userId, -1, 0);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCommentCreated(UUID userId) {
        applyDelta(userId, 0, 1);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCommentDeleted(UUID userId) {
        applyDelta(userId, 0, -1);
    }

    private void applyDelta(UUID userId, int postDelta, int commentDelta) {
        userStatsRepository.upsertCounts(userId, postDelta, commentDelta);
        userStatsRepository.recalculateLevel(userId);
    }
}
