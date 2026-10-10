package com.planmate.community.domain.admin.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** 신고 한 건. 신고자 식별자는 관리자에게만 보인다. */
public record AdminReportItem(
        long reportId,
        UUID reporterId,
        String reason,
        String details,
        String status,
        LocalDateTime createdAt,
        UUID handledBy,
        LocalDateTime handledAt
) {
}
