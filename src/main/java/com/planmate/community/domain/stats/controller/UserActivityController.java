package com.planmate.community.domain.stats.controller;

import com.planmate.community.common.access.ProfileAccessValidator;
import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.domain.comment.dto.CommentResponse;
import com.planmate.community.domain.post.dto.PostSummaryResponse;
import com.planmate.community.domain.stats.dto.MyStatsResponse;
import com.planmate.community.domain.stats.service.MyActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 다른 사용자의 프로필에 노출되는 활동 목록.
 *
 * 조회 로직은 /api/community/me/* 와 동일하며(MyActivityService 재사용), 차이는 대상 사용자가
 * 경로로 오고 프로필 공개 범위 검사를 거친다는 점뿐이다.
 * 좋아요·가져오기 목록은 본인 전용으로 남긴다 — 취향 이력이라 작성 이력과 성격이 다르다.
 */
@Tag(name = "UserActivity", description = "다른 사용자의 커뮤니티 활동 API")
@RestController
@RequestMapping("/api/community/users/{userId}")
@RequiredArgsConstructor
public class UserActivityController {

    private final MyActivityService myActivityService;
    private final ProfileAccessValidator profileAccessValidator;

    @Operation(summary = "사용자가 쓴 글", description = "해당 사용자가 작성한 게시글을 최신순으로 조회합니다. category에 쉼표로 여러 게시판을 넘길 수 있습니다(예: free,qna,mate,recommend). 비공개 프로필이면 본인 외에는 403(USER_002)입니다.")
    @GetMapping("/posts")
    public ResponseEntity<PageResponse<PostSummaryResponse>> getUserPosts(
            @PathVariable("userId") UUID userId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Authentication authentication
    ) {
        profileAccessValidator.validateVisible(userId, viewerId(authentication));
        return ResponseEntity.ok(myActivityService.getMyPosts(userId, category, page, size));
    }

    @Operation(summary = "사용자가 쓴 댓글", description = "해당 사용자가 작성한 댓글을 최신순으로 조회합니다. 비공개 프로필이면 본인 외에는 403(USER_002)입니다.")
    @GetMapping("/comments")
    public ResponseEntity<PageResponse<CommentResponse>> getUserComments(
            @PathVariable("userId") UUID userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Authentication authentication
    ) {
        profileAccessValidator.validateVisible(userId, viewerId(authentication));
        return ResponseEntity.ok(myActivityService.getMyComments(userId, page, size));
    }

    @Operation(summary = "사용자 활동 통계", description = "게시글 수, 댓글 수, 레벨을 조회합니다. 비공개 프로필이면 본인 외에는 403(USER_002)입니다.")
    @GetMapping("/stats")
    public ResponseEntity<MyStatsResponse> getUserStats(
            @PathVariable("userId") UUID userId,
            Authentication authentication
    ) {
        profileAccessValidator.validateVisible(userId, viewerId(authentication));
        return ResponseEntity.ok(myActivityService.getMyStats(userId));
    }

    // 비로그인 요청에는 AnonymousAuthenticationToken이 채워지므로 principal 타입으로 판별한다
    private static UUID viewerId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof UUID principal ? principal : null;
    }
}
