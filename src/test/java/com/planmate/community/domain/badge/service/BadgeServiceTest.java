package com.planmate.community.domain.badge.service;

import com.planmate.community.domain.badge.dto.BadgeResponse;
import com.planmate.community.domain.badge.dto.UserBadgesResponse;
import com.planmate.community.domain.badge.entity.UserBadge;
import com.planmate.community.domain.badge.enums.BadgeType;
import com.planmate.community.domain.badge.repository.UserBadgeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserBadgeRepository userBadgeRepository;

    @InjectMocks
    private BadgeService badgeService;

    // 갱신이 네이티브 upsert라 엔티티에 세터가 없다 — 조회 결과만 흉내 낸다
    private static UserBadge saved(BadgeType type, int progress, LocalDateTime earnedAt) {
        UserBadge badge = BeanUtils.instantiateClass(UserBadge.class);
        ReflectionTestUtils.setField(badge, "userId", USER_ID);
        ReflectionTestUtils.setField(badge, "badgeCode", type.code());
        ReflectionTestUtils.setField(badge, "progress", progress);
        ReflectionTestUtils.setField(badge, "earnedAt", earnedAt);
        ReflectionTestUtils.setField(badge, "updatedAt", LocalDateTime.now());
        return badge;
    }

    private static BadgeResponse find(UserBadgesResponse response, BadgeType type) {
        return response.badges().stream()
                .filter(badge -> badge.code().equals(type.code()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("저장된 행이 없으면 모든 뱃지가 잠긴 채로 내려온다")
    void returnsAllLockedWhenNothingSaved() {
        when(userBadgeRepository.findByUserId(USER_ID)).thenReturn(List.of());

        UserBadgesResponse response = badgeService.getBadges(USER_ID);

        assertThat(response.totalCount()).isEqualTo(BadgeType.values().length);
        assertThat(response.unlockedCount()).isZero();
        assertThat(response.badges()).allMatch(badge -> !badge.unlocked() && badge.progress() == 0);
    }

    @Test
    @DisplayName("저장된 진행도와 달성 시각을 그대로 내려준다")
    void readsStoredProgress() {
        LocalDateTime earnedAt = LocalDateTime.now().minusDays(3);
        when(userBadgeRepository.findByUserId(USER_ID)).thenReturn(List.of(
                saved(BadgeType.FIRST_STEP, 1, earnedAt),
                saved(BadgeType.EAGER_REVIEWER, 7, null)));

        UserBadgesResponse response = badgeService.getBadges(USER_ID);

        BadgeResponse firstStep = find(response, BadgeType.FIRST_STEP);
        assertThat(firstStep.unlocked()).isTrue();
        assertThat(firstStep.earnedAt()).isEqualTo(earnedAt);

        BadgeResponse reviewer = find(response, BadgeType.EAGER_REVIEWER);
        assertThat(reviewer.unlocked()).isFalse();
        assertThat(reviewer.progress()).isEqualTo(7);
        assertThat(reviewer.goal()).isEqualTo(BadgeType.EAGER_REVIEWER.goal());

        // 행이 없는 뱃지는 잠금으로 채워진다
        assertThat(find(response, BadgeType.NATIONWIDE).progress()).isZero();
        assertThat(response.unlockedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("글이 지워져 진행도가 남아 있어도 달성 시각이 있으면 달성 상태를 유지한다")
    void keepsUnlockedWhenProgressDropped() {
        when(userBadgeRepository.findByUserId(USER_ID)).thenReturn(List.of(
                saved(BadgeType.PLAN_MASTER, 2, LocalDateTime.now().minusMonths(1))));

        BadgeResponse planMaster = find(badgeService.getBadges(USER_ID), BadgeType.PLAN_MASTER);

        assertThat(planMaster.unlocked()).isTrue();
        assertThat(planMaster.progress()).isEqualTo(2);
    }
}
