package com.planmate.community.domain.admin.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 신고 대상 상세. 삭제되어 보관 중인 글도 원문을 보여준다 — 관리자가 기각할지 판단할 근거다.
 */
public record AdminReportDetailResponse(
        String targetType,
        long targetId,
        String title,
        String contentText,
        UUID authorId,
        String authorNickname,
        String category,
        LocalDateTime createdAt,
        LocalDateTime deletedAt,
        List<AdminReportItem> reports
) {
}
