package com.planmate.community.domain.admin.controller;

import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.domain.admin.dto.AdminDeletedPostResponse;
import com.planmate.community.domain.admin.dto.AdminReportDetailResponse;
import com.planmate.community.domain.admin.dto.AdminReportTargetResponse;
import com.planmate.community.domain.admin.dto.ModerationActionRequest;
import com.planmate.community.domain.admin.enums.ModerationTargetType;
import com.planmate.community.domain.admin.service.ModerationService;
import com.planmate.community.domain.report.enums.ReportStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 관리자 전용 신고 처리·글 삭제·복구. 경로 권한은 SecurityConfig 의 {@code /api/community/admin/**} 규칙이 막는다.
 */
@RestController
@RequestMapping("/api/community/admin")
@RequiredArgsConstructor
public class AdminModerationController {

    private final ModerationService moderationService;

    @GetMapping("/reports")
    public PageResponse<AdminReportTargetResponse> reportTargets(
            @RequestParam(defaultValue = "PENDING") ReportStatus status,
            @RequestParam(required = false) ModerationTargetType targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return moderationService.reportTargets(status, targetType, page, size);
    }

    @GetMapping("/reports/{targetType}/{targetId}")
    public AdminReportDetailResponse reportDetail(
            @PathVariable ModerationTargetType targetType,
            @PathVariable long targetId
    ) {
        return moderationService.reportDetail(targetType, targetId);
    }

    @PostMapping("/reports/{targetType}/{targetId}/dismiss")
    public ResponseEntity<Void> dismiss(
            Authentication authentication,
            @PathVariable ModerationTargetType targetType,
            @PathVariable long targetId,
            @Valid @RequestBody(required = false) ModerationActionRequest request
    ) {
        moderationService.dismiss(adminId(authentication), targetType, targetId, reason(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reports/{targetType}/{targetId}/delete")
    public ResponseEntity<Void> deleteContent(
            Authentication authentication,
            @PathVariable ModerationTargetType targetType,
            @PathVariable long targetId,
            @Valid @RequestBody(required = false) ModerationActionRequest request
    ) {
        moderationService.deleteContent(adminId(authentication), targetType, targetId, reason(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/deleted-posts")
    public PageResponse<AdminDeletedPostResponse> deletedPosts(
            @RequestParam(required = false) ModerationTargetType targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return moderationService.deletedPosts(targetType, page, size);
    }

    @PostMapping("/posts/{targetType}/{postId}/restore")
    public ResponseEntity<Void> restore(
            Authentication authentication,
            @PathVariable ModerationTargetType targetType,
            @PathVariable long postId
    ) {
        moderationService.restore(adminId(authentication), targetType, postId);
        return ResponseEntity.noContent().build();
    }

    private static UUID adminId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private static String reason(ModerationActionRequest request) {
        return request == null ? null : request.reason();
    }
}
