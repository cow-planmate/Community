package com.planmate.community.domain.badge.service;

import com.planmate.community.domain.badge.dto.BadgeResponse;
import com.planmate.community.domain.badge.dto.UserBadgesResponse;
import com.planmate.community.domain.badge.entity.UserBadge;
import com.planmate.community.domain.badge.enums.BadgeType;
import com.planmate.community.domain.badge.repository.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 활동 뱃지 조회 — 저장된 진행도를 그대로 읽는다 (집계 없음).
 * 아직 행이 없는 뱃지는 진행도 0인 잠긴 뱃지로 채워, 프로필에서 목표를 보여줄 수 있게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BadgeService {

    private final UserBadgeRepository userBadgeRepository;

    public UserBadgesResponse getBadges(UUID userId) {
        Map<String, UserBadge> saved = userBadgeRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserBadge::getBadgeCode, Function.identity()));

        List<BadgeResponse> badges = Arrays.stream(BadgeType.values())
                .map(type -> BadgeResponse.of(type, saved.get(type.code())))
                .toList();
        return UserBadgesResponse.of(userId, badges);
    }
}
