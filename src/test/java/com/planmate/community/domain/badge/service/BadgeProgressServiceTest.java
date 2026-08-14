package com.planmate.community.domain.badge.service;

import com.planmate.community.domain.badge.enums.BadgeType;
import com.planmate.community.domain.badge.repository.UserBadgeRepository;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.stats.entity.UserStats;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import com.planmate.community.common.notification.CommunityNotificationFactory;
import com.planmate.community.common.notification.NotificationOutboxWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadgeProgressServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserBadgeRepository userBadgeRepository;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private PostRepository postRepository;
    @Mock private CommunityNotificationFactory notificationFactory;
    @Mock private NotificationOutboxWriter notificationOutbox;

    @InjectMocks
    private BadgeProgressService badgeProgressService;

    private static UserStats stats(int postCount, int commentCount, int receivedLikes) {
        return UserStats.builder()
                .userId(USER_ID)
                .postCount(postCount)
                .commentCount(commentCount)
                .receivedLikes(receivedLikes)
                .build();
    }

    @Test
    @DisplayName("게시글 활동은 글 관련 뱃지만 갱신한다")
    void refreshesPostBadgesOnly() {
        when(userStatsRepository.findById(USER_ID)).thenReturn(Optional.of(stats(4, 30, 0)));
        when(postRepository.countByUserIdAndCategory(USER_ID, Category.FEED)).thenReturn(3L);
        when(postRepository.countDistinctRegionsByUserIdAndCategory(USER_ID, Category.FEED)).thenReturn(2L);

        badgeProgressService.refreshPostBadges(USER_ID);

        verify(userBadgeRepository).upsertProgress(USER_ID, BadgeType.FIRST_STEP.code(), 4L, 1);
        verify(userBadgeRepository).upsertProgress(USER_ID, BadgeType.PLAN_MASTER.code(), 3L, 5);
        verify(userBadgeRepository).upsertProgress(USER_ID, BadgeType.NATIONWIDE.code(), 2L, 5);
        // 댓글 수가 기준을 넘어도 이 경로에서는 건드리지 않는다
        verify(userBadgeRepository, never())
                .upsertProgress(USER_ID, BadgeType.EAGER_REVIEWER.code(), 30L, 20);
    }

    @Test
    @DisplayName("댓글 활동은 댓글 뱃지만 갱신한다")
    void refreshesCommentBadge() {
        when(userStatsRepository.findById(USER_ID)).thenReturn(Optional.of(stats(0, 21, 0)));

        badgeProgressService.refreshCommentBadges(USER_ID);

        verify(userBadgeRepository).upsertProgress(USER_ID, BadgeType.EAGER_REVIEWER.code(), 21L, 20);
        verify(postRepository, never()).countByUserIdAndCategory(USER_ID, Category.FEED);
    }

    @Test
    @DisplayName("받은 좋아요는 저장된 카운터를 그대로 진행도로 쓴다")
    void refreshesLikeBadgeFromCounter() {
        when(userStatsRepository.findById(USER_ID)).thenReturn(Optional.of(stats(2, 0, 51)));

        badgeProgressService.refreshLikeBadges(USER_ID);

        verify(userBadgeRepository).upsertProgress(USER_ID, BadgeType.BEST_PARTNER.code(), 51L, 50);
    }

    @Test
    @DisplayName("통계 행이 아직 없으면 진행도 0으로 갱신한다")
    void handlesMissingStats() {
        when(userStatsRepository.findById(USER_ID)).thenReturn(Optional.empty());

        badgeProgressService.refreshCommentBadges(USER_ID);

        verify(userBadgeRepository).upsertProgress(USER_ID, BadgeType.EAGER_REVIEWER.code(), 0L, 20);
    }
}
