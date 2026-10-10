package com.planmate.community.domain.admin.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 신고 대상 한 건의 요약. 같은 글에 쌓인 신고를 묶어서 보여준다.
 *
 * @param statusCount 요청한 상태(기본 PENDING)인 신고 수
 * @param totalCount  전체 신고 수
 * @param reasons     신고 사유 종류 (중복 제거)
 */
public record AdminReportTargetResponse(
        String targetType,
        long targetId,
        String title,
        String authorNickname,
        UUID authorId,
        LocalDateTime deletedAt,
        long statusCount,
        long totalCount,
        LocalDateTime latestReportedAt,
        List<String> reasons
) {
}
