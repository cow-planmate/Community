package com.planmate.community.domain.admin.service;

import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.admin.dto.AdminDeletedPostResponse;
import com.planmate.community.domain.admin.dto.AdminReportDetailResponse;
import com.planmate.community.domain.admin.dto.AdminReportTargetResponse;
import com.planmate.community.domain.admin.enums.ModerationActionType;
import com.planmate.community.domain.admin.enums.ModerationTargetType;
import com.planmate.community.domain.admin.repository.ModerationRepository;
import com.planmate.community.domain.admin.repository.ModerationRepository.PostState;
import com.planmate.community.domain.admin.repository.ModerationRepository.ReportTargetContent;
import com.planmate.community.domain.post.service.PostService;
import com.planmate.community.domain.report.enums.ReportStatus;
import com.planmate.community.domain.stats.service.UserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 관리자의 신고 처리·글 삭제·복구. 권한(ROLE_ADMIN)은 컨트롤러 보안 규칙이 먼저 막는다. */
@Service
@RequiredArgsConstructor
public class ModerationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ModerationRepository repository;
    private final PostService postService;
    private final UserStatsService userStatsService;
    private final PostRetentionPolicy retentionPolicy;

    @Transactional(readOnly = true)
    public PageResponse<AdminReportTargetResponse> reportTargets(ReportStatus status, ModerationTargetType type,
                                                                 int page, int size) {
        int pageSize = clampSize(size);
        long total = repository.countReportTargets(status, type);
        List<AdminReportTargetResponse> items = total == 0
                ? List.of()
                : repository.findReportTargets(status, type, pageSize, offset(page, pageSize));
        return pageOf(items, page, pageSize, total);
    }

    @Transactional(readOnly = true)
    public AdminReportDetailResponse reportDetail(ModerationTargetType type, long targetId) {
        ReportTargetContent target = repository.findTargetContent(type, targetId)
                .orElseThrow(() -> new CommunityException(ErrorCode.REPORT_TARGET_NOT_FOUND));
        return new AdminReportDetailResponse(
                type.name(),
                targetId,
                target.title(),
                target.contentText(),
                target.authorId(),
                target.authorNickname(),
                target.category(),
                target.createdAt(),
                target.deletedAt(),
                repository.findReports(type, targetId));
    }

    /** 신고를 기각한다. 글은 그대로 둔다. */
    @Transactional
    public void dismiss(UUID adminId, ModerationTargetType type, long targetId, String reason) {
        if (!repository.hasReports(type, targetId)) {
            throw new CommunityException(ErrorCode.REPORT_TARGET_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        if (repository.markPendingReports(type, targetId, ReportStatus.DISMISSED, adminId, now) > 0) {
            repository.insertAdminAction(adminId, ModerationActionType.DISMISS_REPORTS, type, targetId, reason, now);
        }
    }

    /**
     * 글을 삭제(soft delete, 보관 기간 시작)하고 대기 중인 신고를 처리 완료로 바꾼다.
     * 이미 삭제된 글이면 글은 건드리지 않고 신고만 정리한다.
     */
    @Transactional
    public void deleteContent(UUID adminId, ModerationTargetType type, long targetId, String reason) {
        PostState state = repository.findPostState(type, targetId)
                .orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND));
        if (!state.isDeleted()) {
            if (type == ModerationTargetType.POST) {
                postService.deletePost(adminId, true, targetId);
            } else {
                postService.deleteFeedPost(adminId, true, targetId);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        repository.markPendingReports(type, targetId, ReportStatus.RESOLVED, adminId, now);
        repository.insertAdminAction(adminId, ModerationActionType.DELETE_CONTENT, type, targetId, reason, now);
    }

    /** 보관 기간 안에 삭제된 글을 되살린다. 작성자의 글 수 통계도 되돌린다. */
    @Transactional
    public void restore(UUID adminId, ModerationTargetType type, long postId) {
        LocalDateTime now = LocalDateTime.now();
        PostState state = repository.findPostState(type, postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND));
        if (!state.isDeleted()) {
            throw new CommunityException(ErrorCode.POST_RESTORE_NOT_ALLOWED, "삭제되지 않은 글입니다.");
        }
        LocalDateTime cutoff = retentionPolicy.cutoff(now);
        if (!state.deletedAt().isAfter(cutoff)) {
            throw new CommunityException(ErrorCode.POST_RESTORE_NOT_ALLOWED, "보관 기간이 지나 복구할 수 없습니다.");
        }
        if (repository.restorePost(type, postId, cutoff) != 1) {
            // 조회와 갱신 사이에 영구 삭제가 끼어든 경우
            throw new CommunityException(ErrorCode.POST_NOT_FOUND);
        }
        userStatsService.recordPostRestored(state.userId());
        repository.insertAdminAction(adminId, ModerationActionType.RESTORE_CONTENT, type, postId, null, now);
    }

    /** 보관 중인 삭제 글 목록. type 이 null 이면 커뮤니티 글과 피드를 함께 본다. */
    @Transactional(readOnly = true)
    public PageResponse<AdminDeletedPostResponse> deletedPosts(ModerationTargetType type, int page, int size) {
        int pageSize = clampSize(size);
        LocalDateTime cutoff = retentionPolicy.cutoff(LocalDateTime.now());
        long total = repository.countDeletedPosts(type, cutoff);
        List<AdminDeletedPostResponse> items = total == 0
                ? List.of()
                : repository.findDeletedPosts(type, cutoff, retentionPolicy.days(), pageSize, offset(page, pageSize));
        return pageOf(items, page, pageSize, total);
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static long offset(int page, int size) {
        return (long) Math.max(page, 0) * size;
    }

    private static <T> PageResponse<T> pageOf(List<T> items, int page, int size, long total) {
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(items, Math.max(page, 0), size, total, totalPages);
    }
}
