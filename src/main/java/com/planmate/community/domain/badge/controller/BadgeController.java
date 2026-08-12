package com.planmate.community.domain.badge.controller;

import com.planmate.community.common.access.ProfileAccessValidator;
import com.planmate.community.domain.badge.dto.UserBadgesResponse;
import com.planmate.community.domain.badge.service.BadgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 활동 뱃지 API. 본인/타인 조회를 한 컨트롤러에 두되, 타인 조회는 활동 통계와 같은
 * 프로필 공개 범위 검사를 거친다.
 */
@Tag(name = "Badge", description = "커뮤니티 활동 뱃지 API")
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;
    private final ProfileAccessValidator profileAccessValidator;

    @Operation(summary = "내 뱃지", description = "달성한 뱃지와 미달성 뱃지의 진행도를 함께 조회합니다.")
    @GetMapping("/me/badges")
    public ResponseEntity<UserBadgesResponse> getMyBadges(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(badgeService.getBadges(userId));
    }

    @Operation(summary = "사용자 뱃지", description = "해당 사용자의 뱃지 달성 현황을 조회합니다. 비공개 프로필이면 본인 외에는 403(USER_002)입니다.")
    @GetMapping("/users/{userId}/badges")
    public ResponseEntity<UserBadgesResponse> getUserBadges(
            @PathVariable("userId") UUID userId,
            Authentication authentication
    ) {
        profileAccessValidator.validateVisible(userId, viewerId(authentication));
        return ResponseEntity.ok(badgeService.getBadges(userId));
    }

    // 비로그인 요청에는 AnonymousAuthenticationToken이 채워지므로 principal 타입으로 판별한다
    private static UUID viewerId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof UUID principal ? principal : null;
    }
}
